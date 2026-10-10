package com.github.andreyasadchy.xtra.ui.following.streams

import com.github.andreyasadchy.xtra.repository.PlayerRepository
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalChannelFollowsRepository
import com.github.andreyasadchy.xtra.repository.datasource.FollowedStreamsDataSource
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs

class FollowedStreamsViewModel(
    applicationContext: Context,
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val flow = Pager(
        if (applicationContext.prefs().getString(C.COMPACT_STREAMS, "disabled") != "disabled") {
            PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
        } else {
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        }
    ) {
        FollowedStreamsDataSource(
            userId = applicationContext.tokenPrefs().getString(C.USER_ID, null),
            localChannelFollowsRepository = localChannelFollowsRepository,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true),
            graphQLRepository = graphQLRepository,
            helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
            helixRepository = helixRepository,
            enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
        )
    }.flow.cachedIn(viewModelScope)

    private val appContext = applicationContext

    /** TV: video address of a live stream for the preview (H.264 only, the lightest to decode). */
    suspend fun loadPreviewUrl(channelLogin: String): String? {
        return try {
            val prefs = appContext.prefs()
            playerRepository.loadStreamPlaylistUrl(
                context = appContext,
                networkLibrary = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(appContext, prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                channelLogin = channelLogin,
                platform = prefs.getString(C.TOKEN_PLATFORM, "web"),
                playerType = prefs.getString(C.TOKEN_PLAYER_TYPE, "site"),
                supportedCodecs = "h264",
                proxyPlaybackAccessToken = false,
                proxyHost = null,
                proxyPort = null,
                proxyUser = null,
                proxyPassword = null,
                proxyTimeout = null,
                enableIntegrity = false,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** TV: current viewer count and start time of one stream, for the selected card. Null if it fails. */
    suspend fun loadLiveInfo(channelId: String?, channelLogin: String?): Pair<Int?, String?>? {
        return try {
            val response = graphQLRepository.loadQueryUsersStream(
                networkLibrary = appContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                headers = TwitchApiHelper.getGQLHeaders(appContext, true),
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null,
            )
            response.data?.users?.firstOrNull()?.stream?.let { it.viewersCount to it.createdAt?.toString() }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        val FollowedStreamsViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                FollowedStreamsViewModel(application.applicationContext, xtraModule.localChannelFollowsRepository, xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.playerRepository)
            }
        }
    }
}