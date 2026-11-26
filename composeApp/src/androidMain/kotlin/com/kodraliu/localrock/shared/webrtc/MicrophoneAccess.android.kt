package com.kodraliu.localrock.shared.webrtc

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.kodraliu.localrock.shared.protocol.appContextOrNull

/** The live view registers [requester] while it is open, because only a screen can show the permission prompt. */
internal object MicrophoneAccess {
    @Volatile
    var requester: (suspend () -> Boolean)? = null

    fun granted(): Boolean {
        val context = appContextOrNull() ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun request(): Boolean = granted() || requester?.invoke() == true
}
