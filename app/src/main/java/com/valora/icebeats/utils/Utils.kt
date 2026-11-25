package com.valora.icebeats.utils

import timber.log.Timber

fun reportException(throwable: Throwable) {
    if (throwable is java.util.concurrent.CancellationException ||
        throwable is kotlinx.coroutines.CancellationException) {
        return
    }
    throwable.printStackTrace()
    Timber.e(throwable, "Non-fatal exception reported")
}
