package com.valora.icebeats.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmApiClient @Inject constructor() {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
        const val USER_AGENT = "IceBeats/1.0 (Android)"
    }

    suspend fun get(params: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val urlBuilder = BASE_URL.toHttpUrl().newBuilder()
        params.forEach { (k, v) ->
            urlBuilder.addQueryParameter(k, v)
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string().orEmpty()
            code to body
        }
    }

    suspend fun post(params: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val formBuilder = FormBody.Builder()
        params.forEach { (k, v) ->
            formBuilder.add(k, v)
        }
        val request = Request.Builder()
            .url(BASE_URL)
            .header("User-Agent", USER_AGENT)
            .post(formBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string().orEmpty()
            code to body
        }
    }
}
