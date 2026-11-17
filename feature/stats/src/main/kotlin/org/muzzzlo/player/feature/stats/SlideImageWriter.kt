package org.muzzzlo.player.feature.stats

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.designsystem.component.drawLiveBackground
import org.muzzzlo.player.core.model.DispatcherProvider
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

/** The veil over the colors of the summary, between the lists and the player (assistant's decision). */
internal const val SUMMARY_DIM = 0.35f

private const val PNG_QUALITY = 100

/** Turns a captured slide into the shared picture ([ShareImage]) and saves it where the file provider serves it. */
interface SlideImageWriter {
    /** Writes the picture of [slide] over the live background of [palette] as [fileName]; the content uri to share. */
    suspend fun write(
        slide: ImageBitmap,
        palette: List<Int>,
        fileName: String,
    ): String
}

/** Draws in memory and writes a PNG into `cache/summary/`, one file per period and slide, replaced on every share. */
internal class DefaultSlideImageWriter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dispatchers: DispatcherProvider,
    ) : SlideImageWriter {
        override suspend fun write(
            slide: ImageBitmap,
            palette: List<Int>,
            fileName: String,
        ): String {
            val picture = withContext(dispatchers.default) { compose(slide, palette.map(::Color)) }
            val file =
                withContext(dispatchers.io) {
                    val folder = File(context.cacheDir, ShareImage.FOLDER).apply { mkdirs() }
                    File(folder, fileName).also { file ->
                        file.outputStream().use {
                            picture.asAndroidBitmap().compress(
                                Bitmap.CompressFormat.PNG,
                                PNG_QUALITY,
                                it,
                            )
                        }
                    }
                }
            return FileProvider.getUriForFile(context, "${context.packageName}.summaryshare", file).toString()
        }

        private fun compose(
            slide: ImageBitmap,
            colors: List<Color>,
        ): ImageBitmap {
            val picture = ImageBitmap(ShareImage.WIDTH, ShareImage.HEIGHT)
            val size = Size(ShareImage.WIDTH.toFloat(), ShareImage.HEIGHT.toFloat())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(picture), size) {
                drawLiveBackground(colors, timeSec = 0f, colorAmount = 1f, dim = SUMMARY_DIM)
                val place = ShareImage.fit(slide.width, slide.height)
                drawImage(
                    image = slide,
                    dstOffset = IntOffset(place.left.roundToInt(), place.top.roundToInt()),
                    dstSize = IntSize(place.width.roundToInt(), place.height.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
            }
            return picture
        }
    }

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class SlideImageModule {
    @Binds
    abstract fun slideImageWriter(impl: DefaultSlideImageWriter): SlideImageWriter
}
