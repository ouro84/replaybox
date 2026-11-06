package com.replaybox.app.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to the system package installer (user confirms there).
 * First time only: if Replaybox may not install unknown apps yet, opens the
 * system screen that grants it; the user then presses the banner again.
 */
object UpdateInstaller {

    const val AUTHORITY = "com.replaybox.updates"
    private const val TAG = "UpdateInstaller"

    fun install(context: Context, apk: File) {
        // Started from the Activity (no NEW_TASK) so BACK from the system
        // screens returns to Replaybox, not to the launcher.
        val taskFlags = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        if (!context.packageManager.canRequestPackageInstalls()) {
            val grant = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(taskFlags)
            try {
                context.startActivity(grant)
                return
            } catch (error: ActivityNotFoundException) {
                // Some TV builds lack this screen: let the installer ask instead.
                Log.w(TAG, "no unknown-sources settings screen, trying the installer directly")
            }
        }
        val uri = FileProvider.getUriForFile(context, AUTHORITY, apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or taskFlags)
        context.startActivity(intent)
    }
}
