package com.valora.icebeats.recognition

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.valora.icebeats.R
import com.valora.icebeats.extensions.toMediaItem
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.models.SongItem
import com.valora.icebeats.innertube.models.WatchEndpoint
import com.valora.icebeats.models.toMediaMetadata
import com.valora.icebeats.playback.MusicService
import com.valora.icebeats.playback.PlayerConnection
import com.valora.icebeats.playback.queues.ListQueue
import com.valora.icebeats.playback.queues.YouTubeQueue
import com.valora.icebeats.providers.ProviderSearchManager
import com.valora.icebeats.shazamkit.Shazam
import com.valora.icebeats.shazamkit.ShazamSignatureGenerator
import com.valora.icebeats.shazamkit.models.RecognitionResult
import com.valora.icebeats.songs.IdentifiedSong
import com.valora.icebeats.ui.sharedmusic.SharedMusicIdentificationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Floating compact pill overlay for quick music recognition matching the user's custom design.
 * Keeps underlying social media apps (Instagram, TikTok) playing continuously.
 */
class MusicRecognitionOverlayManager private constructor(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var overlayView: View? = null
    private var isOverlayShowing = false

    private var statusTextView: TextView? = null
    private var waveformContainer: LinearLayout? = null
    private var waveformBars = mutableListOf<View>()
    private var waveformAnimator: ValueAnimator? = null
    private var playButton: View? = null

    private var activeJob: Job? = null
    private var currentResult: RecognitionResult? = null

    private val autoDismissRunnable = Runnable { hide() }

    companion object {
        @Volatile
        private var instance: MusicRecognitionOverlayManager? = null

        fun getInstance(context: Context): MusicRecognitionOverlayManager {
            return instance ?: synchronized(this) {
                instance ?: MusicRecognitionOverlayManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }

    /**
     * Starts listening and shows the floating bottom pill overlay.
     */
    fun startListening() {
        if (!canDrawOverlays(context)) {
            Timber.w("Cannot draw overlays: permission not granted")
            return
        }

        mainHandler.post {
            ensureOverlayCreated()
            bindListeningUi()
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            runRecognitionPipeline()
        }
    }

    private fun bindListeningUi() {
        scheduleAutoDismiss(15000L)
        currentResult = null

        statusTextView?.text = "Listening for music..."
        statusTextView?.setTextColor(Color.parseColor("#E0E0E0"))

        playButton?.visibility = View.GONE
        waveformContainer?.visibility = View.VISIBLE
        startWaveformAnimation()
    }

    private fun bindProcessingUi() {
        mainHandler.post {
            statusTextView?.text = "Identifying with Shazam..."
            statusTextView?.setTextColor(Color.parseColor("#B388FF"))
            playButton?.visibility = View.GONE
        }
    }

    private fun bindSuccessUi(result: RecognitionResult) {
        mainHandler.post {
            currentResult = result
            stopWaveformAnimation()
            waveformContainer?.visibility = View.GONE
            playButton?.visibility = View.VISIBLE
            vibrateSuccess()

            val displayTitle = if (!result.artist.isNullOrBlank()) {
                "🎵 ${result.title} • ${result.artist}"
            } else {
                "🎵 ${result.title}"
            }
            statusTextView?.text = displayTitle
            statusTextView?.setTextColor(Color.WHITE)
            statusTextView?.isSelected = true

            scheduleAutoDismiss(10000L)
        }
    }

    private fun bindNoMatchUi(message: String? = null) {
        mainHandler.post {
            currentResult = null
            stopWaveformAnimation()
            waveformContainer?.visibility = View.GONE
            playButton?.visibility = View.GONE

            statusTextView?.text = message ?: "No match found. Tap to retry"
            statusTextView?.setTextColor(Color.parseColor("#FFAB40"))

            scheduleAutoDismiss(6000L)
        }
    }

    private fun bindErrorUi(errorMsg: String) {
        mainHandler.post {
            currentResult = null
            stopWaveformAnimation()
            waveformContainer?.visibility = View.GONE
            playButton?.visibility = View.GONE

            statusTextView?.text = errorMsg
            statusTextView?.setTextColor(Color.parseColor("#FF5252"))

            scheduleAutoDismiss(5000L)
        }
    }

    private suspend fun runRecognitionPipeline() {
        val samples = recordMicPcm16Mono(sampleRateHz = 16000, recordMs = 4200L)
        if (samples.isEmpty()) {
            bindErrorUi("Microphone silent or unavailable")
            return
        }

        bindProcessingUi()

        val signature = withContext(Dispatchers.Default) {
            ShazamSignatureGenerator().apply {
                feedPcm16Mono(samples)
            }.nextSignatureOrNull()
        }

        if (signature == null) {
            bindNoMatchUi("Couldn't generate audio signature")
            return
        }

        val result = Shazam.recognize(signature.uri, signature.sampleDurationMs)
        result.fold(
            onSuccess = { res ->
                bindSuccessUi(res)
            },
            onFailure = { err ->
                val msg = err.message?.trim().orEmpty()
                if (msg.contains("no match", ignoreCase = true) || msg.contains("404")) {
                    bindNoMatchUi()
                } else {
                    bindErrorUi(msg.ifEmpty { "Recognition failed. Tap to retry" })
                }
            }
        )
    }

    private suspend fun recordMicPcm16Mono(sampleRateHz: Int, recordMs: Long): ShortArray =
        withContext(Dispatchers.IO) {
            val channel = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT

            try {
                val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channel, encoding)
                    .coerceAtLeast(4096)
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRateHz,
                    channel,
                    encoding,
                    minBuffer
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    return@withContext ShortArray(0)
                }

                val totalSamples = ((recordMs / 1000.0) * sampleRateHz).toInt().coerceAtLeast(sampleRateHz)
                val output = ShortArray(totalSamples)
                val buffer = ShortArray(minBuffer / 2)

                try {
                    record.startRecording()
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        return@withContext ShortArray(0)
                    }

                    var written = 0
                    while (written < output.size && isActive) {
                        val read = record.read(buffer, 0, minOf(buffer.size, output.size - written))
                        if (read > 0) {
                            System.arraycopy(buffer, 0, output, written, read)
                            written += read
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                            break
                        }
                    }

                    if (written <= 0) ShortArray(0) else output.copyOf(written)
                } finally {
                    runCatching {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    }
                    runCatching { record.release() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error recording audio for music recognition")
                ShortArray(0)
            }
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureOverlayCreated() {
        if (overlayView != null && isOverlayShowing) return

        try {
            val displayMetrics = context.resources.displayMetrics
            // Fixed compact pill width to maintain original length between states
            val overlayWidth = minOf(dpToPx(320f).toInt(), (displayMetrics.widthPixels - dpToPx(24f)).toInt())

            // Main compact horizontal pill container matching user's design
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#14131E"))
                    cornerRadius = dpToPx(28f)
                    setStroke(dpToPx(1f).toInt(), Color.parseColor("#2C2B3C"))
                }
                background = bg
                setPadding(dpToPx(10f).toInt(), dpToPx(8f).toInt(), dpToPx(8f).toInt(), dpToPx(8f).toInt())
                elevation = dpToPx(16f)
            }

            // Left: Official IceBeats App Icon (Circular)
            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(32f).toInt(), dpToPx(32f).toInt())
                setImageResource(R.mipmap.ic_launcher_round)
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
            }
            card.addView(iconView)

            // Text "IceBeats"
            val appTitleTv = TextView(context).apply {
                text = "IceBeats"
                setTextColor(Color.WHITE)
                textSize = 14f
                paint.isFakeBoldText = true
                setPadding(dpToPx(6f).toInt(), 0, 0, 0)
            }
            card.addView(appTitleTv)

            // Vertical subtle divider line
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(1f).toInt(), dpToPx(18f).toInt()).apply {
                    marginStart = dpToPx(8f).toInt()
                    marginEnd = dpToPx(8f).toInt()
                }
                setBackgroundColor(Color.parseColor("#2C2B3C"))
            }
            card.addView(divider)

            // Middle Section Container (Waveform bars + Status/Song text)
            // Weight = 1f ensures the overlay width never changes when play button toggles
            val middleSection = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (currentResult != null) {
                        launchReelIdentificationScreen(autoPlay = false)
                    } else {
                        startListening()
                    }
                }
            }

            // 5 Animated Waveform Bars
            val waveContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(18f).toInt())
            }
            waveformBars.clear()
            val baseHeights = listOf(6f, 11f, 16f, 11f, 6f)
            for (i in 0 until 5) {
                val bar = View(context).apply {
                    val h = dpToPx(baseHeights[i]).toInt()
                    layoutParams = LinearLayout.LayoutParams(dpToPx(3f).toInt(), h).apply {
                        marginStart = if (i == 0) 0 else dpToPx(2f).toInt()
                    }
                    val barDrawable = GradientDrawable().apply {
                        setColor(Color.parseColor("#8364FF"))
                        cornerRadius = dpToPx(2f)
                    }
                    background = barDrawable
                }
                waveformBars.add(bar)
                waveContainer.addView(bar)
            }
            waveformContainer = waveContainer
            middleSection.addView(waveContainer)

            // Status label ("Listening for music..." or "🎵 Title • Artist")
            val statusTv = TextView(context).apply {
                text = "Listening for music..."
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 13f
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
                isFocusable = true
                setHorizontallyScrolling(true)
                setPadding(dpToPx(6f).toInt(), 0, dpToPx(4f).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            statusTextView = statusTv
            middleSection.addView(statusTv)

            card.addView(middleSection)

            // Right 1: Sleek Circular Vector Play Button
            val playBtnFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(32f).toInt(), dpToPx(32f).toInt()).apply {
                    marginStart = dpToPx(6f).toInt()
                }
                val playBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#7C5CFF"))
                }
                background = playBg
                visibility = View.GONE
                setOnClickListener {
                    if (currentResult != null) {
                        playSongInBackground(currentResult)
                    }
                }
            }

            val playIv = ImageView(context).apply {
                setImageResource(R.drawable.play)
                setColorFilter(Color.WHITE)
                val iconSize = dpToPx(16f).toInt()
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER
                    marginStart = dpToPx(1.5f).toInt()
                }
            }
            playBtnFrame.addView(playIv)
            playButton = playBtnFrame
            card.addView(playBtnFrame)

            // Right 2: Sleek Circular Vector Close Button (✕)
            val closeBtnFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(32f).toInt(), dpToPx(32f).toInt()).apply {
                    marginStart = dpToPx(5f).toInt()
                }
                val closeBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#252433"))
                }
                background = closeBg
                setOnClickListener { hide() }
            }

            val closeIv = ImageView(context).apply {
                setImageResource(R.drawable.close)
                setColorFilter(Color.parseColor("#D0D0E0"))
                val iconSize = dpToPx(15f).toInt()
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER
                }
            }
            closeBtnFrame.addView(closeIv)
            card.addView(closeBtnFrame)

            val layoutParams = WindowManager.LayoutParams(
                overlayWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                x = 0
                y = dpToPx(16f).toInt()
                windowAnimations = android.R.style.Animation_Toast
            }

            windowManager.addView(card, layoutParams)
            overlayView = card
            isOverlayShowing = true

            startWaveformAnimation()
        } catch (e: Exception) {
            Timber.e(e, "Error adding music recognition overlay view")
        }
    }

    private fun playSongInBackground(res: RecognitionResult?) {
        val result = res ?: return
        hide()

        scope.launch(Dispatchers.IO) {
            try {
                val song = IdentifiedSong(
                    title = result.title,
                    artist = result.artist,
                    album = result.album,
                    albumArtUrl = result.coverArtHqUrl ?: result.coverArtUrl,
                    durationMs = null
                )
                val providerSearch = ProviderSearchManager(context)
                val candidates = runCatching { providerSearch.searchProviders(song) }.getOrDefault(emptyList())
                val topMatch = candidates.firstOrNull()

                val service = ensureMusicService()

                withContext(Dispatchers.Main) {
                    if (topMatch != null && topMatch.id.isNotBlank()) {
                        val queue = if (topMatch.provider == "JIOSAAVN") {
                            ListQueue(
                                title = topMatch.title,
                                items = listOf(topMatch.mediaMetadata.toMediaItem())
                            )
                        } else {
                            YouTubeQueue(
                                WatchEndpoint(videoId = topMatch.id),
                                topMatch.mediaMetadata
                            )
                        }
                        val s = MusicService.instance ?: service
                        s?.playQueue(queue, playWhenReady = true)
                        s?.player?.play()
                        PlayerConnection.instance?.playQueue(queue)
                        Toast.makeText(context, "Playing: ${topMatch.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch(Dispatchers.IO) {
                            val query = "${result.title} ${result.artist.orEmpty()}".trim()
                            val yt = runCatching { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull() }.getOrNull()
                            val topYt = yt?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                            withContext(Dispatchers.Main) {
                                if (topYt != null) {
                                    val q = YouTubeQueue(WatchEndpoint(topYt.id), topYt.toMediaMetadata())
                                    val activeS = MusicService.instance ?: service
                                    activeS?.playQueue(q, playWhenReady = true)
                                    activeS?.player?.play()
                                    PlayerConnection.instance?.playQueue(q)
                                    Toast.makeText(context, "Playing: ${topYt.title}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Couldn't find playable track", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing identified song in background")
            }
        }
    }

    private fun ensureMusicService(): MusicService? {
        val service = MusicService.instance ?: PlayerConnection.instance?.service
        if (service == null) {
            try {
                val intent = Intent(context, MusicService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start MusicService")
            }
        }
        return service ?: MusicService.instance ?: PlayerConnection.instance?.service
    }

    private fun launchReelIdentificationScreen(autoPlay: Boolean = false) {
        val res = currentResult ?: return

        // Opens the exact same screen shown when sharing a reel to IceBeats
        val intent = Intent(context, SharedMusicIdentificationActivity::class.java).apply {
            putExtra(SharedMusicIdentificationActivity.EXTRA_IDENTIFIED_TITLE, res.title)
            putExtra(SharedMusicIdentificationActivity.EXTRA_IDENTIFIED_ARTIST, res.artist)
            putExtra(SharedMusicIdentificationActivity.EXTRA_IDENTIFIED_ALBUM, res.album)
            putExtra(SharedMusicIdentificationActivity.EXTRA_IDENTIFIED_ART_URL, res.coverArtHqUrl ?: res.coverArtUrl)
            putExtra(SharedMusicIdentificationActivity.EXTRA_AUTO_PLAY, autoPlay)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        context.startActivity(intent)
        hide()
    }

    private fun startWaveformAnimation() {
        waveformAnimator?.cancel()
        waveformAnimator = ValueAnimator.ofFloat(0.3f, 1.0f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                waveformBars.forEachIndexed { index, bar ->
                    val multiplier = when (index) {
                        0, 4 -> 0.6f + 0.4f * (1f - fraction)
                        1, 3 -> 0.4f + 0.6f * fraction
                        else -> 0.5f + 0.5f * fraction
                    }
                    bar.scaleY = multiplier
                }
            }
            start()
        }
    }

    private fun stopWaveformAnimation() {
        waveformAnimator?.cancel()
        waveformAnimator = null
        waveformBars.forEach { it.scaleY = 1f }
    }

    private fun vibrateSuccess() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100L)
            }
        } catch (_: Exception) {}
    }

    private fun scheduleAutoDismiss(delayMs: Long) {
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.postDelayed(autoDismissRunnable, delayMs)
    }

    /**
     * Hides and removes the floating bottom overlay.
     */
    fun hide() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        activeJob?.cancel()
        activeJob = null

        mainHandler.post {
            try {
                stopWaveformAnimation()

                if (overlayView != null && isOverlayShowing) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                    isOverlayShowing = false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error removing music recognition overlay view")
            }
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
