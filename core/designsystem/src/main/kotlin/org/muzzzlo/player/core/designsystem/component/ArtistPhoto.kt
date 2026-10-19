package org.muzzzlo.player.core.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Round artist photo from the copy the library keeps ([path]), or the empty circle of the mockup while it loads
 * and when there is none. The photos are small (512 px), so they are decoded as they are, off the main thread.
 */
@Composable
fun ArtistPhoto(
    path: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val photo by produceState<ImageBitmap?>(initialValue = null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    val image = photo
    if (image == null) {
        ArtistPhotoPlaceholder(size, modifier)
    } else {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}
