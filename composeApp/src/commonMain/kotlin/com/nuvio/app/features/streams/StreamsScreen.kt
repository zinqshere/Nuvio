package com.nuvio.app.features.streams

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.debrid.toastMessage
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.watchedItemKeys
import com.nuvio.app.navigation.LocalUseNativeNavigation
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------
// Streams Screen
// ---------------------------------------------------------------------------

@Composable
fun StreamsScreen(
    type: String,
    videoId: String,
    parentMetaId: String,
    parentMetaType: String,
    title: String,
    logo: String? = null,
    poster: String? = null,
    background: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
    episodeThumbnail: String? = null,
    resumePositionMs: Long? = null,
    resumeProgressFraction: Float? = null,
    manualSelection: Boolean = false,
    startFromBeginning: Boolean = false,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit = { _, _, _ -> },
    onStreamActionOpen: (
        stream: StreamItem,
        openExternally: Boolean,
        resumePositionMs: Long?,
        resumeProgressFraction: Float?,
    ) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val useNativeNavigation = LocalUseNativeNavigation.current
    val uiState by StreamsRepository.uiState.collectAsStateWithLifecycle()
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val debridSettings by remember {
        DebridSettingsRepository.ensureLoaded()
        DebridSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchProgressUiState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val metaScreenSettings by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    remember {
        DownloadsRepository.ensureLoaded()
    }
    val isEpisode = seasonNumber != null && episodeNumber != null
    val clipboardManager = LocalClipboardManager.current
    val streamLinkCopiedText = stringResource(Res.string.streams_link_copied)
    val noDirectStreamLinkText = stringResource(Res.string.streams_no_direct_link)
    var streamActionsTarget by remember(videoId) { mutableStateOf<StreamItem?>(null) }
    val downloadScope = rememberCoroutineScope()
    var preferredFilterApplied by remember(videoId) { mutableStateOf(false) }
    var autoPlayOverlayLogoLoadError by remember(logo) { mutableStateOf(false) }
    val autoPlayOverlayLogoUrl = logo?.takeIf { it.isNotBlank() }
    val episodeProgress = watchProgressUiState.progressForVideo(
        videoId = videoId,
        parentMetaId = parentMetaId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )
    val storedProgress = if (startFromBeginning) {
        null
    } else {
        episodeProgress
    }
    val resumeState = resolveStreamResumeState(
        progress = episodeProgress,
        initialPositionMs = resumePositionMs,
        initialProgressFraction = resumeProgressFraction,
        startFromBeginning = startFromBeginning,
    )
    val effectiveResumePositionMs = resumeState.positionMs
    val effectiveResumeProgressFraction = resumeState.progressFraction

    LaunchedEffect(type, videoId, seasonNumber, episodeNumber, manualSelection) {
        StreamsRepository.load(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = seasonNumber,
            episode = episodeNumber,
            manualSelection = manualSelection,
        )
    }

    LaunchedEffect(uiState.groups, storedProgress?.providerAddonId, preferredFilterApplied) {
        if (preferredFilterApplied) return@LaunchedEffect
        val preferredAddonId = storedProgress?.providerAddonId ?: return@LaunchedEffect
        if (uiState.groups.any { it.addonId == preferredAddonId }) {
            StreamsRepository.selectFilter(preferredAddonId)
            preferredFilterApplied = true
        }
    }

    val heroArtwork = if (isEpisode) {
        episodeThumbnail ?: background ?: poster
    } else {
        background ?: poster
    }
    val isEpisodeWatched = episodeProgress?.isEffectivelyCompleted == true || watchedItemKeys(
        type = parentMetaType,
        id = parentMetaId,
        season = seasonNumber,
        episode = episodeNumber,
    ).any(watchedUiState.watchedKeys::contains)
    val blurEpisodeThumbnail = metaScreenSettings.blurUnwatchedEpisodes &&
        isEpisode &&
        !isEpisodeWatched &&
        !episodeThumbnail.isNullOrBlank()
    val reloadStreams: () -> Unit = {
        StreamsRepository.reload(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = seasonNumber,
            episode = episodeNumber,
            manualSelection = manualSelection,
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTabletLayout = maxWidth >= 768.dp

        if (isTabletLayout) {
            TabletStreamsLayout(
                isEpisode = isEpisode,
                title = title,
                logo = logo,
                poster = poster,
                background = background,
                episodeThumbnail = episodeThumbnail,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                uiState = uiState,
                debridEnabled = debridSettings.canResolvePlayableLinks,
                appendInstantServiceToDefaultName = debridSettings.canResolvePlayableLinks && !debridSettings.hasCustomStreamFormatting,
                resumePositionMs = effectiveResumePositionMs,
                resumeProgressFraction = effectiveResumeProgressFraction,
                onStreamSelected = { stream, positionMs, progressFraction ->
                    onStreamSelected(stream, positionMs, progressFraction)
                },
                onStreamLongPress = { stream -> streamActionsTarget = stream },
                onRefresh = reloadStreams,
            )
        } else {
            MobileStreamsLayout(
                isEpisode = isEpisode,
                title = title,
                logo = logo,
                heroArtwork = heroArtwork,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                blurEpisodeThumbnail = blurEpisodeThumbnail,
                uiState = uiState,
                debridEnabled = debridSettings.canResolvePlayableLinks,
                appendInstantServiceToDefaultName = debridSettings.canResolvePlayableLinks && !debridSettings.hasCustomStreamFormatting,
                resumePositionMs = effectiveResumePositionMs,
                resumeProgressFraction = effectiveResumeProgressFraction,
                onStreamSelected = { stream, positionMs, progressFraction ->
                    onStreamSelected(stream, positionMs, progressFraction)
                },
                onStreamLongPress = { stream -> streamActionsTarget = stream },
                onRefresh = reloadStreams,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(start = 12.dp, top = if (useNativeNavigation) 52.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NuvioBackButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp),
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.onBackground,
            )
        }

        AnimatedVisibility(
            visible = uiState.showDirectAutoPlayOverlay,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (autoPlayOverlayLogoUrl != null && !autoPlayOverlayLogoLoadError) {
                        AsyncImage(
                            model = autoPlayOverlayLogoUrl,
                            contentDescription = title,
                            modifier = Modifier
                                .height(48.dp),
                            contentScale = ContentScale.Fit,
                            onError = { autoPlayOverlayLogoLoadError = true },
                        )
                    } else if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    NuvioLoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                    )
                    Text(
                        text = uiState.overlayMessage
                            ?: stringResource(Res.string.streams_finding_source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        StreamActionsSheet(
            stream = streamActionsTarget,
            externalPlayerEnabled = playerSettings.externalPlayerEnabled,
            onDismiss = { streamActionsTarget = null },
            onCopyLink = { stream ->
                val directUrl = stream.playableDirectUrl ?: stream.externalOpenUrl
                if (!directUrl.isNullOrBlank()) {
                    clipboardManager.setText(AnnotatedString(directUrl))
                    NuvioToastController.show(streamLinkCopiedText)
                } else if (DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) {
                    downloadScope.launch {
                        val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(
                            stream = stream,
                            season = seasonNumber,
                            episode = episodeNumber,
                        )
                        when (resolved) {
                            is DirectDebridPlayableResult.Success -> {
                                val resolvedUrl = resolved.stream.playableDirectUrl
                                if (!resolvedUrl.isNullOrBlank()) {
                                    clipboardManager.setText(AnnotatedString(resolvedUrl))
                                    NuvioToastController.show(streamLinkCopiedText)
                                } else {
                                    NuvioToastController.show(noDirectStreamLinkText)
                                }
                            }
                            else -> {
                                val message = resolved.toastMessage()
                                if (message != null) {
                                    NuvioToastController.show(message)
                                }
                            }
                        }
                    }
                } else {
                    NuvioToastController.show(noDirectStreamLinkText)
                }
            },
            onDownload = { stream ->
                if (DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)) {
                    downloadScope.launch {
                        val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(
                            stream = stream,
                            season = seasonNumber,
                            episode = episodeNumber,
                        )
                        when (resolved) {
                            is DirectDebridPlayableResult.Success -> {
                                val result = DownloadsRepository.enqueueFromStream(
                                    contentType = type,
                                    videoId = videoId,
                                    parentMetaId = parentMetaId,
                                    parentMetaType = parentMetaType,
                                    title = title,
                                    logo = logo,
                                    poster = poster,
                                    background = background,
                                    seasonNumber = seasonNumber,
                                    episodeNumber = episodeNumber,
                                    episodeTitle = episodeTitle,
                                    episodeThumbnail = episodeThumbnail,
                                    stream = resolved.stream,
                                )
                                NuvioToastController.show(result.toastMessage())
                            }
                            else -> {
                                val message = resolved.toastMessage()
                                if (message != null) {
                                    NuvioToastController.show(message)
                                }
                            }
                        }
                    }
                } else {
                    val result = DownloadsRepository.enqueueFromStream(
                        contentType = type,
                        videoId = videoId,
                        parentMetaId = parentMetaId,
                        parentMetaType = parentMetaType,
                        title = title,
                        logo = logo,
                        poster = poster,
                        background = background,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        episodeTitle = episodeTitle,
                        episodeThumbnail = episodeThumbnail,
                        stream = stream,
                    )
                    NuvioToastController.show(result.toastMessage())
                }
                streamActionsTarget = null
            },
            onOpenExternal = { stream ->
                val url = stream.externalOpenUrl ?: stream.playableDirectUrl ?: return@StreamActionsSheet
                streamActionsTarget = null
                onStreamActionOpen(
                    stream,
                    true,
                    effectiveResumePositionMs,
                    effectiveResumeProgressFraction,
                )
            },
        )
    }
}

@Composable
private fun MobileStreamsLayout(
    isEpisode: Boolean,
    title: String,
    logo: String?,
    heroArtwork: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    blurEpisodeThumbnail: Boolean,
    uiState: StreamsUiState,
    debridEnabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    resumePositionMs: Long?,
    resumeProgressFraction: Float?,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit,
    onStreamLongPress: (StreamItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val screenTitle = if (isEpisode) {
        buildString {
            append(title)
            if (seasonNumber != null && episodeNumber != null) {
                append(" S")
                append(seasonNumber.toString().padStart(2, '0'))
                append('E')
                append(episodeNumber.toString().padStart(2, '0'))
            }
        }
    } else {
        title
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val tablet = maxWidth >= 768.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 72.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = screenTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!episodeTitle.isNullOrBlank()) {
                        Text(
                            text = episodeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (heroArtwork != null && !tablet) {
                item {
                    AsyncImage(
                        model = heroArtwork,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .let { base ->
                                if (blurEpisodeThumbnail) base.blur(14.dp) else base
                            },
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (uiState.isLoading && uiState.groups.isEmpty()) {
                item {
                    NuvioLoadingIndicator(modifier = Modifier.padding(32.dp))
                }
            } else if (uiState.error != null && uiState.groups.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.SearchOff, contentDescription = null)
                        Text(text = uiState.error, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(onClick = onRefresh) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            } else {
                items(
                    items = uiState.groups,
                    key = { group -> group.addonId },
                ) { group ->
                    StreamGroupCard(
                        group = group,
                        debridEnabled = debridEnabled,
                        appendInstantServiceToDefaultName = appendInstantServiceToDefaultName,
                        resumePositionMs = resumePositionMs,
                        resumeProgressFraction = resumeProgressFraction,
                        onStreamSelected = onStreamSelected,
                        onStreamLongPress = onStreamLongPress,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabletStreamsLayout(
    isEpisode: Boolean,
    title: String,
    logo: String?,
    poster: String?,
    background: String?,
    episodeThumbnail: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    uiState: StreamsUiState,
    debridEnabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    resumePositionMs: Long?,
    resumeProgressFraction: Float?,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit,
    onStreamLongPress: (StreamItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val selectedArtwork = if (isEpisode) episodeThumbnail ?: background ?: poster else background ?: poster
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (selectedArtwork != null) {
            AsyncImage(
                model = selectedArtwork,
                contentDescription = null,
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(0.65f).fillMaxHeight(),
            contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = buildString {
                        append(title)
                        if (isEpisode && seasonNumber != null && episodeNumber != null) {
                            append(" S")
                            append(seasonNumber.toString().padStart(2, '0'))
                            append('E')
                            append(episodeNumber.toString().padStart(2, '0'))
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!episodeTitle.isNullOrBlank()) {
                    Text(
                        text = episodeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (uiState.isLoading && uiState.groups.isEmpty()) {
                item { NuvioLoadingIndicator(modifier = Modifier.padding(32.dp)) }
            } else if (uiState.error != null && uiState.groups.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.SearchOff, contentDescription = null)
                        Text(uiState.error, textAlign = TextAlign.Center)
                        androidx.compose.material3.Button(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            } else {
                items(
                    items = uiState.groups,
                    key = { group -> group.addonId },
                ) { group ->
                    StreamGroupCard(
                        group = group,
                        debridEnabled = debridEnabled,
                        appendInstantServiceToDefaultName = appendInstantServiceToDefaultName,
                        resumePositionMs = resumePositionMs,
                        resumeProgressFraction = resumeProgressFraction,
                        onStreamSelected = onStreamSelected,
                        onStreamLongPress = onStreamLongPress,
                    )
                }
            }
        }
    }
}

internal data class StreamResumeState(
    val positionMs: Long? = null,
    val progressFraction: Float? = null,
)

internal fun resolveStreamResumeState(
    progress: WatchProgressEntry?,
    initialPositionMs: Long?,
    initialProgressFraction: Float?,
    startFromBeginning: Boolean,
): StreamResumeState {
    if (startFromBeginning || progress?.isResumable == false) return StreamResumeState()
    val fraction = (if (progress != null) progress.progressPercent?.div(100f) else initialProgressFraction)
        ?.takeIf { it > 0f }?.coerceIn(0f, 1f)
    val position = if (fraction != null) null
        else (progress?.lastPositionMs ?: initialPositionMs)?.takeIf { it > 0L }
    return StreamResumeState(positionMs = position, progressFraction = fraction)
}

@Composable
internal fun ResumeBanner(
    positionMs: Long?,
    progressFraction: Float? = null,
    modifier: Modifier = Modifier,
) {
    val resumeText = when {
        progressFraction != null && progressFraction > 0f -> stringResource(
            Res.string.streams_resume_from_percent,
            (progressFraction * 100f).roundToInt(),
        )
        positionMs != null && positionMs > 0L -> stringResource(
            Res.string.streams_resume_from_time,
            positionMs.toPlaybackClock(),
        )
        else -> null
    } ?: return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = resumeText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
