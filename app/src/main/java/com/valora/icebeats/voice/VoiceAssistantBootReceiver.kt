package com.valora.icebeats.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.valora.icebeats.constants.EnableVoiceAssistantKey
import com.valora.icebeats.constants.VoiceAssistantAutoStartOnBootKey
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class VoiceAssistantBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Timber.i("VoiceAssistantBootReceiver triggered with action: %s", action)

            CoroutineScope(Dispatchers.IO).launch {
                val isEnabled = context.dataStore.get(EnableVoiceAssistantKey, false)
                val autoStartOnBoot = context.dataStore.get(VoiceAssistantAutoStartOnBootKey, false)

                if (isEnabled && autoStartOnBoot) {
                    Timber.i("Auto-starting VoiceAssistantService after boot/update")
                    VoiceAssistantService.start(context)
                }
            }
        }
    }
}
