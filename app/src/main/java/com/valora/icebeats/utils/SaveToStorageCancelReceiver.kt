package com.valora.icebeats.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.valora.icebeats.R
import timber.log.Timber

class SaveToStorageCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SaveToStorageUtil.ACTION_CANCEL_SAVE) {
            val notificationId = intent.getIntExtra(SaveToStorageUtil.EXTRA_NOTIFICATION_ID, -1)
            Timber.tag("SaveToStorageCancelReceiver").d("Received cancel action for notificationId=$notificationId")
            if (notificationId != -1) {
                SaveToStorageUtil.cancelSave(context, notificationId)
                Toast.makeText(context, R.string.download_canceled, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
