package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.kodraliu.localrock.shared.device.MAX_DEVICE_NAME_LENGTH
import com.kodraliu.localrock.shared.device.renameDevice
import com.kodraliu.localrock.shared.device.validateDeviceName
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.LocalAppContainer
import com.kodraliu.localrock.ui.components.BonusKind
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.components.SettingRowClickable
import com.kodraliu.localrock.ui.components.SettingRowInfo
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Product Info: the robot's name (renamable), model, firmware and serial, then LocalRock's server details. */
@Composable
fun VacuumProductInfoScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val duid = viewModel.repository.duid
    val devices by container.deviceRepository.devices.collectAsState()
    val serverUrl by container.appSettings.serverBaseUrl.collectAsState()
    val device = devices.firstOrNull { it.duid == duid }
    val name = device?.name ?: viewModel.deviceName
    var renaming by remember { mutableStateOf(false) }

    SettingsPage(title = SettingsStrings.PRODUCT_INFO, onBack = onBack, snackbar = snackbar) {
        item {
            SettingGroup {
                SettingRowClickable(Icons.Default.Edit, AppColors.AccentCyan, SettingsStrings.NAME, name) { renaming = true }
                RowDivider()
                SettingRowInfo(Icons.Default.Router, AppColors.AccentIndigo, SettingsStrings.DEVICE_MODEL,
                    viewModel.modelDisplayName ?: SettingsStrings.UNKNOWN)
                viewModel.modelName?.let {
                    RowDivider()
                    SettingRowInfo(Icons.Default.Router, AppColors.AccentIndigo, SettingsStrings.MODEL, it)
                }
                RowDivider()
                SettingRowInfo(Icons.Default.Memory, AppColors.AccentCyan, SettingsStrings.FIRMWARE_VERSION,
                    device?.fv ?: viewModel.firmwareVersion ?: SettingsStrings.UNKNOWN)
                device?.sn?.takeIf { it.isNotBlank() }?.let {
                    RowDivider()
                    SettingRowInfo(Icons.Default.QrCode, AppColors.AccentPurple, SettingsStrings.SERIAL_NUMBER, it)
                }
            }
        }
        item { GroupCaption(SettingsStrings.SERVER_CONNECTION_CAPTION, BonusKind.LocalRock) }
        item {
            SettingGroup {
                SettingRowInfo(Icons.Default.Dns, AppColors.Good, SettingsStrings.SERVER, serverUrl ?: SettingsStrings.UNKNOWN)
                RowDivider()
                SettingRowInfo(Icons.Default.Fingerprint, AppColors.Good, SettingsStrings.DEVICE_ID, duid)
            }
        }
    }

    if (renaming) {
        RenameDialog(
            current = name,
            onDismiss = { renaming = false },
            onSave = { newName ->
                renaming = false
                scope.launch {
                    val message = try {
                        if (container.appSettings.demoMode.value) error(SettingsStrings.RENAME_DEMO)
                        container.httpClient.renameDevice(duid, newName)
                        // Reload home data so every screen that reads the device list shows the new name.
                        runCatching { container.deviceRepository.refresh() }
                        SettingsStrings.RENAMED
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        e.message ?: SettingsStrings.RENAME_FAILED
                    }
                    snackbar.showSnackbar(message)
                }
            },
        )
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    val error = runCatching { validateDeviceName(text) }.exceptionOrNull()?.message
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SettingsStrings.RENAME) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_DEVICE_NAME_LENGTH + 8) text = it },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "${text.trim().length}/$MAX_DEVICE_NAME_LENGTH") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = error == null && text.trim() != current) { Text(SettingsStrings.SAVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(SettingsStrings.CANCEL) } },
    )
}
