package com.streamlocal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var playerLoading: ProgressBar
    private lateinit var imgArt: ImageView
    private lateinit var txtPlayerTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var switchAudioOnly: SwitchMaterial

    private var exoPlayer: ExoPlayer? = null
    private var totalDurationSec: Long = 0
    private var isAudioMode = false
    private var resolveResult: ResolveResult? = null
    private var sourceUrl: String = ""

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgressUi()
            uiHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        playerLoading = findViewById(R.id.playerLoading)
        imgArt = findViewById(R.id.imgArt)
        txtPlayerTitle = findViewById(R.id.txtPlayerTitle)
        txtArtist = findViewById(R.id.txtArtist)
        seekBar = findViewById(R.id.seekBar)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnBack = findViewById(R.id.btnBack)
        switchAudioOnly = findViewById(R.id.switchAudioOnly)

        sourceUrl = intent.getStringExtra("source_url") ?: ""
        val titleExtra = intent.getStringExtra("title") ?: ""
        val artistExtra = intent.getStringExtra("artist")
        val isMusicTab = intent.getBooleanExtra("is_music_tab", false)

        txtPlayerTitle.text = titleExtra
        txtArtist.text = artistExtra ?: ""

        isAudioMode = isMusicTab || Prefs.getAudioOnlyDefault(this)
        switchAudioOnly.isChecked = isAudioMode
        applyModeVisuals()

        btnBack.setOnClickListener { finish() }

        btnPlayPause.setOnClickListener { togglePlayPause() }

        switchAudioOnly.setOnCheckedChangeListener { _, checked ->
            if (checked != isAudioMode) {
                isAudioMode = checked
                applyModeVisuals()
                resolveResult?.let { startPlayback(it) }
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    txtCurrentTime.text = MediaAdapter.formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
                exoPlayer?.seekTo((sb?.progress ?: 0) * 1000L)
            }
        })

        if (sourceUrl.isBlank()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        resolveAndPlay()
    }

    private fun applyModeVisuals() {
        playerView.visibility = if (isAudioMode) View.GONE else View.VISIBLE
        imgArt.visibility = if (isAudioMode) View.VISIBLE else View.GONE
    }

    private fun resolveAndPlay() {
        playerLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result = ApiClient.resolve(this@PlayerActivity, sourceUrl)
                resolveResult = result
                totalDurationSec = result.duration
                txtTotalTime.text = MediaAdapter.formatDuration(totalDurationSec)
                seekBar.max = if (totalDurationSec > 0) totalDurationSec.toInt() else 100
                if (txtPlayerTitle.text.isNullOrBlank()) txtPlayerTitle.text = result.title
                if (txtArtist.text.isNullOrBlank()) txtArtist.text = result.uploader ?: ""
                loadArtwork(result.thumbnail)
                startPlayback(result)
            } catch (e: ApiException) {
                playerLoading.visibility = View.GONE
                Toast.makeText(this@PlayerActivity, e.message ?: getString(R.string.error_generic), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                playerLoading.visibility = View.GONE
                Toast.makeText(this@PlayerActivity, R.string.error_generic, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadArtwork(url: String?) {
        if (url.isNullOrBlank()) return
        lifecycleScope.launch {
            try {
                val input = java.net.URL(url).openStream()
                val bmp = android.graphics.BitmapFactory.decodeStream(input)
                input.close()
                imgArt.setImageBitmap(bmp)
            } catch (_: Exception) {
            }
        }
    }

    private fun startPlayback(result: ResolveResult) {
        releasePlayer()

        val path = if (isAudioMode) result.streamUrlAudioPath else result.streamUrlPath
        val fullUrl = ApiClient.buildStreamUrl(this, path)

        val player = ExoPlayer.Builder(this).build()
        exoPlayer = player
        playerView.player = player

        val mediaItem = ExoMediaItem.fromUri(Uri.parse(fullUrl))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        playerLoading.visibility = View.GONE
                    }
                    Player.STATE_BUFFERING -> {
                        playerLoading.visibility = View.VISIBLE
                    }
                    Player.STATE_ENDED -> {
                        btnPlayPause.setImageResource(R.drawable.ic_play)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playerLoading.visibility = View.GONE
                Toast.makeText(this@PlayerActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        })

        uiHandler.removeCallbacks(progressRunnable)
        uiHandler.post(progressRunnable)
    }

    private fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    private fun updateProgressUi() {
        val player = exoPlayer ?: return
        if (isUserSeeking) return
        // PENTING: jangan pakai player.duration (fragmented stream membuatnya naik-naik sendiri).
        // Selalu pakai totalDurationSec dari hasil /api/resolve.
        val currentSec = (player.currentPosition / 1000).coerceAtLeast(0)
        seekBar.progress = currentSec.toInt().coerceAtMost(seekBar.max)
        txtCurrentTime.text = MediaAdapter.formatDuration(currentSec)
    }

    private fun releasePlayer() {
        uiHandler.removeCallbacks(progressRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
