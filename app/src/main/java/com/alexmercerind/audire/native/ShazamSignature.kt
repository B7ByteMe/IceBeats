package com.alexmercerind.audire.native

object ShazamSignature {
    init {
        try {
            System.loadLibrary("shazam_signature_jni")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("ShazamSignature", "Failed to load shazam_signature_jni", e)
        }
    }

    @JvmStatic
    external fun create(input: ShortArray): String
}
