package com.valora.icebeats.share

import android.content.Context
import android.net.Uri

object ShareContentValidator {

    fun validateUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize > 0
            } ?: (context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.available() >= 0
            } == true)
        } catch (_: Exception) {
            false
        }
    }
}
