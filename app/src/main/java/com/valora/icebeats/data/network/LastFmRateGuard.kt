package com.valora.icebeats.data.network

import android.os.SystemClock
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmRateGuard @Inject constructor() {

    private val lock = Any()
    private var cooldownUntilElapsed: Long = 0L

    val cooldownRemainingMs: Long
        get() = synchronized(lock) {
            (cooldownUntilElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }

    fun onRequestLimited() {
        synchronized(lock) {
            cooldownUntilElapsed = SystemClock.elapsedRealtime() + 2_500L
        }
    }

    fun onRequestSucceeded() {
        synchronized(lock) {
            cooldownUntilElapsed = 0L
        }
    }

    suspend fun suspendAwaitClearance(maxWaitMs: Long): Boolean {
        val remaining = cooldownRemainingMs
        if (remaining > 0) {
            delay(minOf(remaining, maxWaitMs))
        }
        return true
    }
}
