package com.streamlocal.app.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.streamlocal.app.R
import com.streamlocal.app.databinding.ActivityPlayerBinding
import java.util.Locale

/**
 * Memutar stream video atau audio dari StreamLocal.
 *
 * Catatan penting (sesuai API.md StreamLocal):
 * - Stream di-remux fragmented (frag_keyframe+empty_moov) TANPA total-duration
 *   yang akurat di header, jadi player.duration akan naik-naik sendiri seiring
 *   data diterima. Karena itu durasi total SELALU dari EXTRA_DURATION yang
 *   dikirim dari hasil /api/resolve, bukan dari ExoPlayer.
 * - Seek jauh ke depan bisa gagal karena ini live-pipe (bukan file statis
 *   dengan byte-range request). Seek tetap diizinkan tapi bisa gagal untuk
 *   posisi yang belum di-buffer.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_UPLOADER = "extra_uploader"
        const val EXTRA_THUMBNAIL = "extra_thumbnail"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_STREAM_URL_AUDIO = "extra_stream_url_audio"
        const val EXTRA_IS_MUSIC = "extra_is_music"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var knownDurationSeconds: Long = 0
    private var isMusic: Boolean = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val uploader = intent.getStringExtra(EXTRA_UPLOADER).orEmpty()
        val thumbnail = intent.getStringExtra(EXTRA_THUMBNAIL)
        knownDurationSeconds = intent.getLongExtra(EXTRA_DURATION, 0)
        isMusic = intent.getBooleanExtra(EXTRA_IS_MUSIC, false)
        val videoUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        val audioUrl = intent.getStringExtra(EXTRA_STREAM_URL_AUDIO)

        binding.toolbar.title = if (isMusic) getString(R.string.tab_music) else getString(R.string.player_title)
        binding.textTitle.text = title
        binding.textUploader.text = uploader
        binding.textTotalTime.text = formatTime(knownDurationSeconds)

        if (isMusic) {
            // Mode audio: sembunyikan video surface, tampilkan artwork
            binding.playerView.visibility = View.GONE
            binding.imageArtwork.visibility = View.VISIBLE
            Glide.with(this)
                .load(thumbnail)
                .placeholder(R.drawable.ic_thumbnail_placeholder)
                .error(R.drawable.ic_thumbnail_placeholder)
                .into(binding.imageArtwork)
        } else {
            binding.playerView.visibility = View.VISIBLE
            binding.imageArtwork.visibility = View.GONE
        }

        val streamUrl = if (isMusic) audioUrl else videoUrl
        if (streamUrl.isNullOrBlank()) {
            binding.textPlayerStatus.text = getString(R.string.msg_load_error, "URL stream kosong")
            return
        }

        setupPlayer(streamUrl)
        setupControls()
    }

    private fun setupPlayer(streamUrl: String) {
        binding.textPlayerStatus.text = getString(R.string.player_title) + "…"

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        binding.playerView.player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        binding.textPlayerStatus.text = ""
                        startProgressUpdates()
                    }
                    Player.STATE_BUFFERING -> {
                        binding.textPlayerStatus.text = "Memuat stream…"
                    }
                    Player.STATE_ENDED -> {
                        binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.btnPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.textPlayerStatus.text = getString(R.string.msg_load_error, error.message ?: "playback error")
            }
        })

        val mediaItem = MediaItem.fromUri(streamUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
        }

        binding.seekBar.max = if (knownDurationSeconds > 0) knownDurationSeconds.toInt() else 100
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.textCurrentTime.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val targetSeconds = seekBar?.progress ?: 0
                // Seek terbatas: ini live-pipe, jadi lompat jauh ke depan bisa gagal
                // kalau server belum mengirim sejauh itu (lihat catatan API.md).
                player?.seekTo(targetSeconds * 1000L)
            }
        })
    }

    private fun startProgressUpdates() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = object : Runnable {
            override fun run() {
                val p = player
                if (p != null && !isUserSeeking) {
                    val currentSeconds = p.currentPosition / 1000
                    binding.textCurrentTime.text = formatTime(currentSeconds)
                    if (knownDurationSeconds > 0) {
                        binding.seekBar.progress = currentSeconds.coerceAtMost(knownDurationSeconds).toInt()
                    }
                }
                progressHandler.postDelayed(this, 500)
            }
        }
        progressHandler.post(progressRunnable!!)
    }

    private fun formatTime(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0:00"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        player?.release()
        player = null
    }
}
