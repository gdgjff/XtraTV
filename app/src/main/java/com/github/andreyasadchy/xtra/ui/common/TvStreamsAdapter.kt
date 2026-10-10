package com.github.andreyasadchy.xtra.ui.common

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.TvStreamsListItemBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Android TV: live streams shown like SmartTwitchTV's "User - Channels live" screen.
 * OK opens the stream, holding OK opens the channel page.
 */
class TvStreamsAdapter(
    private val fragment: Fragment,
) : PagingDataAdapter<Stream, TvStreamsAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<Stream>() {
        override fun areItemsTheSame(oldItem: Stream, newItem: Stream): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Stream, newItem: Stream): Boolean =
            oldItem.viewerCount == newItem.viewerCount &&
                    oldItem.gameName == newItem.gameName &&
                    oldItem.title == newItem.title
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagingViewHolder {
        val binding = TvStreamsListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PagingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_TICK }) {
            getItem(position)?.let { holder.updateInfo(it) }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /** Re-draws the uptime of every visible card, without rebinding them (keeps the selection). */
    fun tick() {
        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_TICK)
        }
    }

    companion object {
        private const val PAYLOAD_TICK = "tick"
    }

    inner class PagingViewHolder(
        private val binding: TvStreamsListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Stream?) {
            with(binding) {
                if (item == null) return
                val context = fragment.requireContext()
                root.setOnClickListener {
                    (fragment.activity as MainActivity).startStream(item)
                }
                root.setOnLongClickListener {
                    fragment.findNavController().navigate(
                        ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                            channelId = item.channelId,
                            channelLogin = item.channelLogin,
                            channelName = item.channelName,
                            channelImage = item.channelImage,
                            streamId = item.id
                        )
                    )
                    true
                }
                username.text = if (item.channelLogin != null && item.channelName != null && !item.channelLogin.equals(item.channelName, true)) {
                    when (context.prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                        "0" -> "${item.channelName}(${item.channelLogin})"
                        "1" -> item.channelName
                        else -> item.channelLogin
                    }
                } else {
                    item.channelName ?: item.channelLogin
                }
                val streamTitle = item.title?.trim()
                title.text = streamTitle
                title.visibility = if (streamTitle.isNullOrBlank()) View.GONE else View.VISIBLE
                val game = item.gameName
                if (!game.isNullOrBlank()) {
                    gameName.text = context.getString(R.string.tv_stream_playing, game)
                    gameName.visibility = View.VISIBLE
                } else {
                    gameName.visibility = View.GONE
                }
                updateInfo(item)
                if (item.thumbnailURL != null) {
                    // refresh the thumbnail every 5 minutes
                    val minutes = System.currentTimeMillis() / 60000L
                    val lastMinute = minutes % 10
                    val key = if (lastMinute < 5) minutes - lastMinute else minutes - (lastMinute - 5)
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context).apply {
                            data(item.thumbnail)
                            memoryCacheKeyExtra("minutes", key.toString())
                            diskCachePolicy(CachePolicy.DISABLED)
                            crossfade(true)
                            target(thumbnail)
                        }.build()
                    )
                } else {
                    thumbnail.setImageDrawable(null)
                }
            }
        }

        fun updateInfo(item: Stream) {
            with(binding) {
                val context = fragment.requireContext()
                val uptimeText = item.createdAt?.let {
                    Instant.parseOrNull(it)?.takeIf { time -> time.toEpochMilliseconds() > 0 }?.let { createdAt ->
                        val uptime = Clock.System.now() - createdAt
                        if (uptime.isPositive()) DateUtils.formatElapsedTime(uptime.inWholeSeconds) else null
                    }
                }
                val viewersText = item.viewerCount?.let { count ->
                    context.resources.getQuantityString(
                        R.plurals.viewers,
                        count,
                        TwitchApiHelper.formatCount(count, context.prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true))
                    )
                }
                info.text = when {
                    uptimeText != null && viewersText != null -> context.getString(R.string.tv_stream_since, uptimeText, viewersText)
                    viewersText != null -> viewersText
                    uptimeText != null -> context.getString(R.string.uptime, uptimeText)
                    else -> null
                }
                info.visibility = if (info.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}
