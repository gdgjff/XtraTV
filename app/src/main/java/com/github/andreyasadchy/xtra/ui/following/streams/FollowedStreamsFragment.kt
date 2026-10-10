package com.github.andreyasadchy.xtra.ui.following.streams

import com.github.andreyasadchy.xtra.ui.main.MainActivity
import kotlinx.coroutines.Job
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import android.view.ViewTreeObserver
import android.os.SystemClock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import androidx.recyclerview.widget.SimpleItemAnimator
import android.graphics.Color
import androidx.recyclerview.widget.GridLayoutManager
import com.github.andreyasadchy.xtra.util.isTv
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.CommonRecyclerViewLayoutBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.StreamsAdapter
import com.github.andreyasadchy.xtra.ui.common.TvStreamsAdapter
import com.github.andreyasadchy.xtra.ui.common.StreamsCompactAdapter
import com.github.andreyasadchy.xtra.ui.following.streams.FollowedStreamsViewModel.Companion.FollowedStreamsViewModelFactory
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FollowedStreamsFragment : PagedListFragment(), Scrollable {

    private var _binding: CommonRecyclerViewLayoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FollowedStreamsViewModel by viewModels { FollowedStreamsViewModelFactory }
    private lateinit var pagingAdapter: PagingDataAdapter<Stream, out RecyclerView.ViewHolder>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CommonRecyclerViewLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tv = requireContext().isTv()
        pagingAdapter = if (tv) {
            TvStreamsAdapter(this) // SmartTwitchTV-style cards
        } else if (requireContext().prefs().getString(C.COMPACT_STREAMS, "disabled") != "disabled") {
            StreamsCompactAdapter(this, {
                findNavController().navigate(
                    TopStreamsFragmentDirections.actionGlobalTopFragment(
                        tags = arrayOf(it)
                    )
                )
            })
        } else {
            StreamsAdapter(this, {
                findNavController().navigate(
                    TopStreamsFragmentDirections.actionGlobalTopFragment(
                        tags = arrayOf(it)
                    )
                )
            })
        }
        setAdapter(binding.recyclerView, pagingAdapter)
        if (tv) {
            // Like SmartTwitchTV: black background, 3 streams per row
            binding.recyclerView.setBackgroundColor(Color.BLACK)
            (binding.recyclerView.layoutManager as? GridLayoutManager)?.spanCount = 3
            // Updated cards are redrawn in place (no fade), so the selected card keeps the selection
            (binding.recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            // Live info on the selected card only: its uptime counts every second, and its viewer
            // count is fetched from Twitch when selected and every 30 seconds while it stays selected.
            // Other cards are left as they are while you browse.
            view.viewTreeObserver.addOnGlobalFocusChangeListener(previewFocusListener)
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    var lastFetchedId: String? = null
                    var lastFetchTime = 0L
                    var seconds = 0
                    var previewCandidateId: String? = null
                    try {
                    while (true) {
                        delay(1.seconds)
                        seconds++
                        val recyclerView = _binding?.recyclerView ?: continue
                        // While you're not browsing the list (e.g. watching a stream), reload it every minute
                        // so newly live channels show up. Never while the remote is in the list.
                        if (seconds % 60 == 0 && !recyclerView.hasFocus()) {
                            pagingAdapter.refresh()
                            continue
                        }
                        val holder = recyclerView.focusedChild?.let { recyclerView.getChildViewHolder(it) } as? TvStreamsAdapter.PagingViewHolder
                        val item = holder?.currentItem
                        if (holder == null || item == null) {
                            previewCandidateId = null
                            continue
                        }
                        holder.updateInfo(item)
                        // Live preview: start once the same card has stayed selected for a moment
                        if (previewHolder == null && previewEnabled()) {
                            if (previewCandidateId == item.id) {
                                startPreview(holder, item)
                            } else {
                                previewCandidateId = item.id
                            }
                        }
                        val now = SystemClock.elapsedRealtime()
                        if (item.id != lastFetchedId || now - lastFetchTime >= 30_000L) {
                            lastFetchedId = item.id
                            lastFetchTime = now
                            launch {
                                viewModel.loadLiveInfo(item.channelId, item.channelLogin)?.let { (viewers, startedAt) ->
                                    viewers?.let { item.viewerCount = it }
                                    startedAt?.let { item.createdAt = it }
                                    if (holder.currentItem === item) {
                                        holder.updateInfo(item)
                                    }
                                }
                            }
                        }
                    }
                    } finally {
                        // Screen left / app in background: stop the preview
                        stopPreview()
                    }
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) {
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                binding.recyclerView.updatePadding(bottom = insets.bottom)
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun initialize() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.flow.collectLatest { pagingData ->
                    pagingAdapter.submitData(pagingData)
                }
            }
        }
        initializeAdapter(binding, pagingAdapter, enableSwipeRefresh = !requireContext().isTv(), enableScrollTopButton = false) // TV: refresh quietly, no spinner
    }

    override fun scrollToTop() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onNetworkRestored() {
        pagingAdapter.retry()
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "refresh" -> {
                pagingAdapter.refresh()
            }
        }
    }

    // Android TV: live preview of the selected stream inside its card

    private var previewPlayer: ExoPlayer? = null
    private var previewHolder: TvStreamsAdapter.PagingViewHolder? = null
    private var previewJob: Job? = null

    /** Stops the preview as soon as the selection leaves the card that is playing. */
    private val previewFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (previewHolder != null && newFocus !== previewHolder?.itemView) {
            stopPreview()
        }
    }

    private fun previewEnabled(): Boolean {
        // Never while a stream is open (full screen or minimized): one video at a time
        return requireContext().prefs().getBoolean(KEY_LIVE_PREVIEW, true) &&
                (activity as? MainActivity)?.playerFragment == null
    }

    private fun startPreview(holder: TvStreamsAdapter.PagingViewHolder, item: Stream) {
        val channelLogin = item.channelLogin ?: return
        previewHolder = holder
        previewJob = viewLifecycleOwner.lifecycleScope.launch {
            val url = viewModel.loadPreviewUrl(channelLogin)
            if (previewHolder !== holder || holder.currentItem !== item) return@launch
            if (url == null) {
                stopPreview()
                return@launch
            }
            val player = previewPlayer ?: ExoPlayer.Builder(requireContext()).build().apply {
                // A small card doesn't need more than 480p
                trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSize(854, 480).build()
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        previewHolder?.showPreviewFrame()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        stopPreview()
                    }
                })
            }.also { previewPlayer = it }
            holder.attachPreview(player)
            player.volume = if (requireContext().prefs().getBoolean(KEY_LIVE_PREVIEW_SOUND, false)) 1f else 0f
            player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        val holder = previewHolder
        previewHolder = null
        previewPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            holder?.detachPreview(player)
        }
    }

    override fun onDestroyView() {
        stopPreview()
        previewPlayer?.release()
        previewPlayer = null
        view?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalFocusChangeListener(previewFocusListener)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_LIVE_PREVIEW = "tv_live_preview"
        private const val KEY_LIVE_PREVIEW_SOUND = "tv_live_preview_sound"
    }
}
