package com.github.andreyasadchy.xtra.ui.following.streams

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
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    var lastFetchedId: String? = null
                    var lastFetchTime = 0L
                    var seconds = 0
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
                        val item = holder?.currentItem ?: continue
                        holder.updateInfo(item)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
