package org.muzzzlo.player

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.component.DeleteFileDialog
import org.muzzzlo.player.core.designsystem.component.LocalTrackDeleter
import org.muzzzlo.player.core.designsystem.component.TrackDeleter
import org.muzzzlo.player.core.library.IntentSenderHandle
import org.muzzzlo.player.core.model.DeleteRequest

/**
 * Gives every screen under [content] the "Delete file" of the track menu ([LocalTrackDeleter]) and shows what the
 * delete needs: the warning of Android 10 and older, the system dialog or the permission request (task 2.0.6).
 */
@Composable
fun TrackDeletionHost(
    viewModel: TrackDeletionViewModel,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleter = remember(viewModel) { TrackDeleter(viewModel::onDeleteRequested) }
    CompositionLocalProvider(LocalTrackDeleter provides deleter, content = content)

    val systemDialog =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            viewModel.onSystemAnswer(it.resultCode == Activity.RESULT_OK)
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::onSystemAnswer)
    val context = LocalContext.current
    when (val current = state) {
        is DeletionState.Confirm ->
            DeleteFileDialog(current.title, onConfirm = viewModel::onConfirmed, onDismiss = viewModel::onDismissed)
        is DeletionState.AskSystem ->
            LaunchedEffect(current) {
                when (val request = current.request) {
                    is DeleteRequest.SystemDialog -> {
                        val sender = (request.handle as IntentSenderHandle).intentSender
                        systemDialog.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    is DeleteRequest.RuntimePermission -> permission.launch(request.permission)
                    else -> viewModel.onSystemAnswer(true)
                }
            }
        DeletionState.Failed ->
            LaunchedEffect(Unit) {
                Toast.makeText(context, R.string.delete_file_failed, Toast.LENGTH_SHORT).show()
                viewModel.onFailureShown()
            }
        DeletionState.Idle -> Unit
    }
}
