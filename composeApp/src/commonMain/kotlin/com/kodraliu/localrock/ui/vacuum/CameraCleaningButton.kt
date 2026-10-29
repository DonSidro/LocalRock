package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.camera.CameraCleaningAction
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController
import com.kodraliu.localrock.shared.vacuum.camera.cameraCleaningAction
import kotlinx.coroutines.CancellationException

@Composable
internal fun CameraCleaningButton(viewModel: VacuumViewModel, motion: CameraMotionController, compact: Boolean = false, modifier: Modifier = Modifier) {
    val status by viewModel.status.collectAsState()
    val busy by motion.busy.collectAsState()
    val manualMode by motion.manualMode.collectAsState()
    val action = cameraCleaningAction(if (manualMode && status.state in setOf(4, 7)) 3 else status.state, status.inCleaning, status.errorCode)
    // Refresh after an acknowledgement, including when video is unavailable on the dock.
    LaunchedEffect(busy) {
        if (!busy) try { viewModel.repository.refreshStatus() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* The next action still requires a fresh status read. */ }
    }
    if (status.state in setOf(VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE)) return
    // The viewer can pause/resume an existing clean, but never offers a new cleaning run.
    if (action == null || action == CameraCleaningAction.START) return
    if (compact) {
        FilledTonalIconButton(onClick={action?.let(motion::cleaning)},enabled=!busy && action!=null,modifier=modifier.size(56.dp)) {
            Icon(if(action==CameraCleaningAction.PAUSE) Icons.Default.Pause else Icons.Default.PlayArrow,action?.label ?: "Cleaning unavailable")
        }
        return
    }
    FilledTonalButton(modifier=modifier,onClick = { action?.let(motion::cleaning) }, enabled = !busy && action != null) {
        Icon(if (action == CameraCleaningAction.PAUSE) Icons.Default.Pause else Icons.Default.PlayArrow, null)
        Spacer(Modifier.width(6.dp))
        Text(action?.label ?: "Cleaning unavailable")
    }
}

