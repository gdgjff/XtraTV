package com.github.andreyasadchy.xtra.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.text.format.DateUtils
import android.util.Base64
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import org.json.JSONArray
import org.json.JSONException

@OptIn(UnstableApi::class)
class ExoPlayerFragment : PlayerFragment() {

    override var playbackService: ExoPlayerService? = null
    private var serviceConnection: ServiceConnection? = null
    private var playerListener: Player.Listener? = null
    private val updateProgressAction = Runnable { if (view != null) updateProgress() }

    override fun onStart() {
        super.onStart()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                binding.bufferingIndicator.isVisible = playbackState == Player.STATE_BUFFERING
                val showPlayButton = Util.shouldShowPlayButton(playbackService?.player)
                if (showPlayButton) {
                    binding.playerControls.playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                    binding.playerControls.playPause.visibility = View.VISIBLE
                } else {
                    binding.playerControls.playPause.setImageResource(R.drawable.baseline_pause_black_48)
                    if (playbackService?.type == BasePlaybackService.STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                        binding.playerControls.playPause.visibility = View.GONE
                    }
                }
                setPipActions(!showPlayButton)
                updateProgress()
                controllerAutoHide = !showPlayButton
                if (useController) {
                    showController(show = playbackService?.type != BasePlaybackService.STREAM && playbackState == Player.STATE_ENDED)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                binding.bufferingIndicator.isVisible = playbackService?.player?.playbackState == Player.STATE_BUFFERING
                val showPlayButton = Util.shouldShowPlayButton(playbackService?.player)
                if (showPlayButton) {
                    binding.playerControls.playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                    binding.playerControls.playPause.visibility = View.VISIBLE
                } else {
                    binding.playerControls.playPause.setImageResource(R.drawable.baseline_pause_black_48)
                    if (playbackService?.type == BasePlaybackService.STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                        binding.playerControls.playPause.visibility = View.GONE
                    }
                }
                setPipActions(!showPlayButton)
                updateProgress()
                controllerAutoHide = !showPlayButton
                if (useController) {
                    showController(show = playbackService?.type != BasePlaybackService.STREAM && playbackService?.player?.playbackState == Player.STATE_ENDED)
                }
            }

            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                if (Util.shouldShowPlayButton(playbackService?.player)) {
                    binding.playerControls.playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                    binding.playerControls.playPause.visibility = View.VISIBLE
                } else {
                    binding.playerControls.playPause.setImageResource(R.drawable.baseline_pause_black_48)
                    if (playbackService?.type == BasePlaybackService.STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                        binding.playerControls.playPause.visibility = View.GONE
                    }
                }
                val duration = playbackService?.player?.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0
                binding.playerControls.progressBar.setDuration(duration)
                binding.playerControls.duration.text = DateUtils.formatElapsedTime(duration / 1000)
                updateProgress()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize != VideoSize.UNKNOWN && playbackService?.player?.let { it.playbackState != Player.STATE_IDLE } == true) {
                    val aspectRatio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                    binding.aspectRatioFrameLayout.setAspectRatio(aspectRatio)
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                binding.subtitleView.setCues(cueGroup.cues)
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                val duration = playbackService?.player?.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0
                binding.playerControls.progressBar.setDuration(duration)
                binding.playerControls.duration.text = DateUtils.formatElapsedTime(duration / 1000)
                updateProgress()
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    if (chatFragment?.context != null) { // TODO
                        chatFragment?.updatePosition(newPosition.positionMs)
                    }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                if (chatFragment?.context != null) { // TODO
                    chatFragment?.updateSpeed(playbackParameters.speed)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateProgress()
                if (!requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false) && canEnterPictureInPicture()) {
                    requireView().keepScreenOn = isPlaying
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                setSubtitlesButton()
                if (!tracks.isEmpty) {
                    chatFragment?.startReplayChatLoad()
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val duration = playbackService?.player?.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0
                binding.playerControls.progressBar.setDuration(duration)
                binding.playerControls.duration.text = DateUtils.formatElapsedTime(duration / 1000)
                updateProgress()
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                if (parameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO)) {
                    binding.playerSurface.visibility = View.GONE
                } else {
                    binding.playerSurface.visibility = View.VISIBLE
                }
            }
        }
        val serviceListener = object : ExoPlayerService.Listener {
            override fun started() {
                if (view != null) {
                    if (!started) {
                        if (isInitialized || !enableNetworkCheck) {
                            started = true
                            start()
                        }
                    } else {
                        chatFragment?.startReplayChatLoad()
                        if (playbackService?.restoreQuality == true) {
                            playbackService?.restoreQuality = false
                            changeQuality(playbackService?.previousQuality)
                        }
                    }
                }
            }

            override fun loaded() {
                if (view != null) {
                    with(binding.playerControls) {
                        quality.isEnabled = true
                        quality.setColorFilter(Color.WHITE)
                        download.isEnabled = true
                        download.setColorFilter(Color.WHITE)
                        audioOnly.isEnabled = true
                        audioOnly.setColorFilter(Color.WHITE)
                        setQualityText()
                    }
                }
            }

            override fun changePlayerMode() {
                if (view != null) {
                    this@ExoPlayerFragment.changePlayerMode()
                }
            }

            override fun toast(resId: Int, duration: Int) {
                if (view != null) {
                    Toast.makeText(requireContext(), resId, duration).show()
                }
            }

            override fun toast(text: CharSequence, duration: Int) {
                if (view != null) {
                    Toast.makeText(requireContext(), text, duration).show()
                }
            }

            override fun updateVideoInfo() {
                if (view != null) {
                    with(binding.playerControls) {
                        val titleText = playbackService?.title
                        if (!titleText.isNullOrBlank() && requireContext().prefs().getBoolean(C.PLAYER_TITLE, true)) {
                            title.visibility = View.VISIBLE
                            title.text = titleText
                        }
                        val gameName = playbackService?.gameName
                        if (!gameName.isNullOrBlank() && requireContext().prefs().getBoolean(C.PLAYER_CATEGORY, true)) {
                            category.visibility = View.VISIBLE
                            category.text = gameName
                            category.setOnClickListener {
                                findNavController().navigate(
                                    if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                                        GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                            gameId = playbackService?.gameId,
                                            gameSlug = playbackService?.gameSlug,
                                            gameName = gameName
                                        )
                                    } else {
                                        GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                            gameId = playbackService?.gameId,
                                            gameSlug = playbackService?.gameSlug,
                                            gameName = gameName
                                        )
                                    }
                                )
                                minimize()
                            }
                        }
                    }
                }
            }

            override fun setPlayerSurface(secondaryPlayer: Boolean) {
                if (secondaryPlayer) {
                    binding.playerSurface.visibility = View.VISIBLE
                    playbackService?.player?.setVideoSurfaceView(null)
                    playbackService?.secondaryPlayer?.setVideoSurfaceView(binding.playerSurface)
                    playerListener?.let {
                        playbackService?.player?.removeListener(it)
                        playbackService?.secondaryPlayer?.addListener(it)
                    }
                } else {
                    playbackService?.secondaryPlayer?.setVideoSurfaceView(null)
                    playbackService?.player?.setVideoSurfaceView(binding.playerSurface)
                    playerListener?.let {
                        playbackService?.secondaryPlayer?.removeListener(it)
                        playbackService?.player?.addListener(it)
                    }
                }
                if (!requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false) && canEnterPictureInPicture()) {
                    requireView().keepScreenOn = playbackService?.player?.isPlaying == true
                }
            }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (view != null) {
                    val binder = service as ExoPlayerService.ServiceBinder
                    playbackService = binder.getService()
                    playbackService?.serviceListener = serviceListener
                    (playbackService?.secondaryPlayer ?: playbackService?.player)?.setVideoSurfaceView(binding.playerSurface)
                    (playbackService?.secondaryPlayer ?: playbackService?.player)?.addListener(listener)
                    playerListener = listener
                    val endTime = playbackService?.setSleepTimer(-1)
                    if (endTime != null && endTime > 0L) {
                        val duration = endTime - System.currentTimeMillis()
                        if (duration > 0L) {
                            (activity as? MainActivity)?.setSleepTimer(duration)
                        } else {
                            minimize()
                            close()
                            (activity as? MainActivity)?.closePlayer()
                        }
                    }
                    playbackService?.setStopServiceTimer(false)
                    (playbackService?.secondaryPlayer ?: playbackService?.player)?.let { player ->
                        if (!requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false) && canEnterPictureInPicture()) {
                            requireView().keepScreenOn = player.isPlaying
                        }
                        updateProgress()
                        if (Util.shouldShowPlayButton(player)) {
                            binding.playerControls.playPause.setImageResource(R.drawable.baseline_play_arrow_black_48)
                            binding.playerControls.playPause.visibility = View.VISIBLE
                        } else {
                            binding.playerControls.playPause.setImageResource(R.drawable.baseline_pause_black_48)
                            if (playbackService?.type == BasePlaybackService.STREAM && !requireContext().prefs().getBoolean(C.PLAYER_PAUSE, false)) {
                                binding.playerControls.playPause.visibility = View.GONE
                            }
                        }
                    }
                    if (playbackService?.started == true) {
                        if (!started) {
                            if (isInitialized || !enableNetworkCheck) {
                                started = true
                                start()
                            }
                        } else {
                            chatFragment?.startReplayChatLoad()
                            if (playbackService?.restoreQuality == true) {
                                playbackService?.restoreQuality = false
                                changeQuality(playbackService?.previousQuality)
                            }
                        }
                    }
                    playbackService?.player?.let { player ->
                        setPipActions(player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE && player.playWhenReady)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playbackService = null
            }
        }
        val intent = Intent(requireContext(), ExoPlayerService::class.java).apply {
            action = ExoPlayerService.INTENT_START
        }
        requireContext().startService(intent)
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        serviceConnection = connection
    }

    override fun getCurrentPosition() = playbackService?.player?.currentPosition

    override fun getCurrentSpeed() = playbackService?.player?.playbackParameters?.speed

    override fun getCurrentVolume(): Float? {
        return playbackService?.getCurrentVolume()
    }

    override fun getTotalDuration() = (playbackService?.player?.currentManifest as? HlsManifest)?.mediaPlaylist?.durationUs?.div(1000)

    private var catchingUp = false

    // Live offset correction for broadcasts stamped ahead of real time (handoff from Xtra-Web)
    /** (elapsedRealtime ms, skew ms): how far the newest loaded video's stamp is ahead of now */
    private val liveSkews = ArrayDeque<Pair<Long, Long>>()
    private var skewChannel: String? = null
    private var correctedOffset: Long? = null
    private var atLiveEdge = false

    /** currentLiveOffset, plus the amount the stream's timestamps run ahead of real time (max over 20 s). */
    private fun correctedLiveOffset(player: androidx.media3.common.Player): Long? {
        val raw = player.currentLiveOffset.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: return null
        val now = android.os.SystemClock.elapsedRealtime()
        if (player.playbackState == Player.STATE_READY) {
            // Offset of the newest loaded frame = raw - buffered. Below 0 means it's stamped in the future.
            val skew = player.totalBufferedDuration - raw
            if (skew in -30_000L..10_000L) liveSkews.addLast(now to skew)
        }
        while (liveSkews.isNotEmpty() && now - liveSkews.first().first > 20_000L) liveSkews.removeFirst()
        val excess = (liveSkews.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(0L)
        return (raw + excess).takeIf { it in 0L..120_000L }
    }

    /**
     * Xtra TV catch-up: speeds up to the set speed when the delay reaches the "start" value, back to
     * normal at the "stop" value. Only speeds up when enough video is loaded ahead, so it never causes buffering.
     * Returns the catch-up speed while catching up, null otherwise.
     */
    override fun updateLiveCatchUp(): Float? {
        val service = playbackService
        val player = service?.player
        if (service == null || player == null || service.type != BasePlaybackService.STREAM || !player.isCurrentMediaItemLive) {
            // Not a live stream (or switched to a VOD): the skew is per broadcast
            liveSkews.clear()
            correctedOffset = null
            stopCatchUp()
            return null
        }
        if (service.channelLogin != skewChannel) {
            // Different channel: don't carry the previous broadcast's skew over
            liveSkews.clear()
            skewChannel = service.channelLogin
        }
        // Corrected delay, used by both the readout (getLiveOffset) and catch-up
        val offset = correctedLiveOffset(player)
        correctedOffset = offset
        val prefs = requireContext().prefs()
        val active = service.tvCatchUpEnabled() && service.secondaryPlayer == null
        if (!active || offset == null) {
            stopCatchUp()
            return null
        }
        val startMs = ((prefs.getString(ExoPlayerService.TV_CATCH_UP_START, "2.5")?.toFloatOrNull() ?: 2.5f) * 1000).toLong()
        val stopMs = ((prefs.getString(ExoPlayerService.TV_CATCH_UP_STOP, "2.0")?.toFloatOrNull() ?: 2f) * 1000).toLong()
            .coerceAtMost(startMs - 200)
        val speed = (prefs.getString(ExoPlayerService.TV_CATCH_UP_SPEED, "1.1")?.toFloatOrNull() ?: 1.1f).coerceIn(1.01f, 1.5f)
        val buffered = player.totalBufferedDuration
        if (!catchingUp) {
            if (player.isPlaying && player.playbackParameters.speed == 1f && offset >= startMs && buffered >= CATCH_UP_MIN_BUFFER_START_MS) {
                player.setPlaybackSpeed(speed)
                catchingUp = true
            }
        } else if (!player.isPlaying || offset <= stopMs || buffered < CATCH_UP_MIN_BUFFER_KEEP_MS) {
            stopCatchUp()
        }
        // Above "start" but too little loaded to speed up: already as close to live as the stream allows
        atLiveEdge = !catchingUp && player.isPlaying && player.playbackParameters.speed == 1f &&
                offset >= startMs && buffered < CATCH_UP_MIN_BUFFER_START_MS
        return if (catchingUp) speed else null
    }

    override fun isAtLiveEdge() = atLiveEdge

    private fun stopCatchUp() {
        atLiveEdge = false
        if (catchingUp) {
            catchingUp = false
            playbackService?.player?.setPlaybackSpeed(1f)
        }
    }

    override fun getProxyHost(): String? = playbackService?.currentProxyHost()

    override fun isProxyExcluded(): Boolean = playbackService?.isProxyExcluded() == true

    override fun setProxyExcluded(excluded: Boolean) {
        stopCatchUp()
        playbackService?.setProxyExcluded(excluded)
    }

    override fun getChannelLogin(): String? = playbackService?.channelLogin

    /** The corrected delay computed by updateLiveCatchUp() (called just before this, every 500 ms). */
    override fun getLiveOffset(): Long? {
        val player = playbackService?.player ?: return null
        if (!player.isCurrentMediaItemLive) return null
        return correctedOffset
    }

    override fun playPause() {
        playbackService?.togglePause()
    }

    override fun rewind() {
        playbackService?.seekBack()
    }

    override fun fastForward() {
        playbackService?.seekForward()
    }

    override fun seek(position: Long) {
        playbackService?.seekTo(position)
    }

    override fun seekToLivePosition() {
        playbackService?.seekToDefaultPosition()
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackService?.player?.setPlaybackSpeed(speed)
    }

    override fun changeVolume(volume: Float) {
        playbackService?.changeVolume(volume)
    }

    override fun updateProgress() {
        with(binding.playerControls) {
            if (root.isVisible && !progressBar.isPressed) {
                val currentPosition = playbackService?.player?.currentPosition ?: 0
                position.text = DateUtils.formatElapsedTime(currentPosition / 1000)
                progressBar.setPosition(currentPosition)
                progressBar.setBufferedPosition(playbackService?.player?.bufferedPosition ?: 0)
                root.removeCallbacks(updateProgressAction)
                playbackService?.player?.let { player ->
                    if (player.isPlaying) {
                        val speed = player.playbackParameters.speed
                        val delay = if (speed > 0f) {
                            (progressBar.preferredUpdateDelay / speed).toLong().coerceIn(200L..1000L)
                        } else {
                            1000
                        }
                        root.postDelayed(updateProgressAction, delay)
                    }
                }
            }
        }
    }

    override fun restartPlayer() {
        playbackService?.restartPlayer()
    }

    override fun toggleAudioCompressor() {
        val enabled = playbackService?.toggleDynamicsProcessing()
        if (enabled == true) {
            binding.playerControls.audioCompressor.setImageResource(R.drawable.baseline_audio_compressor_on_24dp)
        } else {
            binding.playerControls.audioCompressor.setImageResource(R.drawable.baseline_audio_compressor_off_24dp)
        }
    }

    override fun setSubtitlesButton() {
        with(binding.playerControls) {
            val textTracks = playbackService?.player?.currentTracks?.groups?.find { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
            if (textTracks != null && requireContext().prefs().getBoolean(C.PLAYER_SUBTITLES, false)) {
                subtitles.visibility = View.VISIBLE
                if (textTracks.isSelected) {
                    subtitles.setImageResource(androidx.media3.ui.R.drawable.exo_ic_subtitle_on)
                    subtitles.setOnClickListener {
                        showController(force = true)
                        toggleSubtitles(false)
                        requireContext().prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, false) }
                    }
                } else {
                    subtitles.setImageResource(androidx.media3.ui.R.drawable.exo_ic_subtitle_off)
                    subtitles.setOnClickListener {
                        showController(force = true)
                        toggleSubtitles(true)
                        requireContext().prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, true) }
                    }
                }
            } else {
                subtitles.visibility = View.GONE
            }
            (childFragmentManager.findFragmentByTag("closeOnPip") as? PlayerSettingsDialog?)?.setSubtitles(textTracks)
        }
    }

    override fun toggleSubtitles(enabled: Boolean) {
        playbackService?.toggleSubtitles(enabled)
    }

    override fun getUnavailableQualities(): List<VideoQuality> {
        val stableVariantIds = mutableListOf<VideoQuality>()
        (playbackService?.player?.currentManifest as? HlsManifest)?.multivariantPlaylist?.tags?.let { playlist ->
            playlist.filter { it.startsWith("#EXT-X-SESSION-DATA") }.forEach { line ->
                val id = Regex("DATA-ID=\"(.+?)\"").find(line)?.groups?.get(1)?.value
                if (id == "com.amazon.ivs.unavailable-media") {
                    val value = Regex("VALUE=\"(.+?)\"").find(line)?.groups?.get(1)?.value
                    if (value != null) {
                        val bytes = try {
                            Base64.decode(value, Base64.DEFAULT)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                        if (bytes != null) {
                            val string = String(bytes)
                            val array = try {
                                JSONArray(string)
                            } catch (e: JSONException) {
                                null
                            }
                            if (array != null) {
                                for (i in 0 until array.length()) {
                                    val obj = array.optJSONObject(i)
                                    if (obj != null) {
                                        var skip = false
                                        val filterReasons = obj.optJSONArray("FILTER_REASONS")
                                        if (filterReasons != null) {
                                            for (filterIndex in 0 until filterReasons.length()) {
                                                val filter = filterReasons.optString(filterIndex)
                                                if (filter.isNotEmpty()) {
                                                    skip = true
                                                    break
                                                }
                                            }
                                        }
                                        if (!skip) {
                                            val newVariantId = obj.optString("STABLE-VARIANT-ID")
                                            val resolution = obj.optString("RESOLUTION")
                                            val frameRate = obj.optString("FRAME-RATE").toFloatOrNull()
                                            val bitrate = obj.optInt("BANDWIDTH")
                                            val codec = obj.optString("CODECS")
                                            if (!newVariantId.isNullOrBlank()) {
                                                stableVariantIds.add(VideoQuality(newVariantId, resolution.substringAfter('x').toIntOrNull(), frameRate, bitrate, codec))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return stableVariantIds
    }

    override fun showPlaylistTags(mediaPlaylist: Boolean) {
        val tags = playbackService?.getPlaylistTags(mediaPlaylist)
        if (!tags.isNullOrBlank()) {
            requireContext().getAlertDialogBuilder().apply {
                setView(NestedScrollView(context).apply {
                    addView(HorizontalScrollView(context).apply {
                        addView(TextView(context).apply {
                            text = tags
                            textSize = 12F
                            setTextIsSelectable(true)
                        })
                    })
                })
                setNegativeButton(R.string.copy_clip) { _, _ ->
                    val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("label", tags))
                }
                setPositiveButton(android.R.string.ok, null)
            }.show()
        }
    }

    override fun changeQuality(selectedQuality: VideoQuality?) {
        playbackService?.changeQuality(selectedQuality)
    }

    override fun startAudioOnly() {
        if (playbackService != null) {
            playbackService?.startAudioOnly()
            playbackService?.setSleepTimer((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
            playbackService?.setStopServiceTimer(true)
        }
        playerListener?.let { (playbackService?.secondaryPlayer ?: playbackService?.player)?.removeListener(it) }
        playerListener = null
        playbackService?.serviceListener = null
        serviceConnection?.let { requireContext().unbindService(it) }
        serviceConnection = null
        playbackService = null
    }

    override fun close(deleteStates: Boolean) {
        playbackService?.pause()
        playbackService?.stop()
        if (deleteStates) {
            viewModel.deletePlaybackStates()
        }
        playerListener?.let { (playbackService?.secondaryPlayer ?: playbackService?.player)?.removeListener(it) }
        playerListener = null
        playbackService?.serviceListener = null
        serviceConnection?.let { requireContext().unbindService(it) }
        serviceConnection = null
        playbackService?.stopSelf()
        playbackService = null
    }

    override fun retry(item: String) {
        playbackService?.retry(item)
    }

    override fun onStop() {
        super.onStop()
        if (playbackService != null) {
            val isInPIPMode = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> requireActivity().isInPictureInPictureMode
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> !useController && isMaximized
                else -> false
            }
            playbackService?.stop(isInPIPMode)
            playbackService?.setSleepTimer((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
            playbackService?.setStopServiceTimer(true)
        }
        binding.playerControls.root.removeCallbacks(updateProgressAction)
        playerListener?.let { (playbackService?.secondaryPlayer ?: playbackService?.player)?.removeListener(it) }
        playerListener = null
        playbackService?.serviceListener = null
        serviceConnection?.let { requireContext().unbindService(it) }
        serviceConnection = null
    }

    override fun onNetworkRestored() {
        if (isResumed) {
            if (playbackService?.type == BasePlaybackService.STREAM) {
                restartPlayer()
            } else {
                playbackService?.player?.prepare()
            }
        }
    }

    override fun onNetworkLost() {
        if (playbackService?.type != BasePlaybackService.STREAM && isResumed) {
            playbackService?.stop()
        }
    }

    companion object {
        /** Video that must be loaded ahead to start / keep catching up (milliseconds). */
        private const val CATCH_UP_MIN_BUFFER_START_MS = 800L
        private const val CATCH_UP_MIN_BUFFER_KEEP_MS = 400L
    }
}