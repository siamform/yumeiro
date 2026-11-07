/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.asfakul.yumeiro.update.UpdateManager
import dev.asfakul.yumeiro.update.UpdateState

/**
 * Hosts the update popup on top of the app. Place once at the root of the UI.
 * @param enabled set to false while a screen (e.g. splash) should not be interrupted.
 */
@Composable
fun UpdateHost(enabled: Boolean = true) {
    val context = LocalContext.current
    val manager = remember { UpdateManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val visible by manager.dialogVisible.collectAsStateWithLifecycle()

    // Returning from "Install unknown apps" settings: continue the install if it's now allowed.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!manager.needsInstallPermission()) manager.install()
    }

    val showable = state is UpdateState.Available || state is UpdateState.Downloading ||
        state is UpdateState.ReadyToInstall || state is UpdateState.Installing ||
        (state is UpdateState.Failed && (state as UpdateState.Failed).release != null)

    if (!enabled || !visible || !showable) return

    UpdateDialog(
        state = state,
        currentVersion = manager.currentVersionName,
        needsInstallPermission = manager.needsInstallPermission(),
        onDownload = manager::startDownload,
        onRemindLater = manager::remindLater,
        onCancelDownload = manager::cancelDownload,
        onContinueInBackground = manager::continueInBackground,
        onInstall = {
            if (manager.needsInstallPermission()) {
                permissionLauncher.launch(manager.installPermissionIntent())
            } else {
                manager.install()
            }
        },
        onRetry = manager::startDownload,
        onDismiss = manager::dismissDialog,
    )
}
