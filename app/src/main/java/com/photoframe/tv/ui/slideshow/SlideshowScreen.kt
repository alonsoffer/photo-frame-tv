package com.photoframe.tv.ui.slideshow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.photoframe.tv.data.ScaleMode
import com.photoframe.tv.data.SettingsRepository
import com.photoframe.tv.data.Transition
import com.photoframe.tv.R
import com.photoframe.tv.drive.DriveRepository
import com.photoframe.tv.drive.Photo
import com.photoframe.tv.ui.common.ClockOverlay
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@Composable
fun SlideshowScreen(
    driveRepository: DriveRepository,
    localRepository: DriveRepository,
    settingsRepository: SettingsRepository,
    imageLoader: ImageLoader,
    isDemoMode: Boolean,
    driveConnected: () -> Boolean,
    onOpenSettings: () -> Unit,
) {
    val vm: SlideshowViewModel = viewModel(
        factory = simpleFactory {
            SlideshowViewModel(driveRepository, localRepository, settingsRepository, isDemoMode, driveConnected)
        },
    )
    val context = LocalContext.current

    // Download/decode images at the display's own resolution instead of full-res originals.
    val maxDimPx = remember {
        val dm = context.resources.displayMetrics
        maxOf(dm.widthPixels, dm.heightPixels).coerceIn(480, 4096)
    }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Night mode: recompute the current hour once a minute.
    var currentHour by remember { mutableStateOf(hourNow()) }
    LaunchedEffect(Unit) {
        while (true) { currentHour = hourNow(); delay(60_000) }
    }
    val isNight = settings.nightModeEnabled &&
        withinHours(currentHour, settings.nightStartHour, settings.nightEndHour)

    // Auto-advance timer.
    LaunchedEffect(index, playing, settings.intervalSeconds, photos.size, isNight) {
        if (playing && !isNight && photos.size > 1) {
            delay(settings.intervalSeconds * 1000L)
            vm.next()
        }
    }

    // Prefetch the next image (at screen resolution) so transitions are seamless.
    LaunchedEffect(index, photos, maxDimPx) {
        val size = photos.size
        if (size > 1) {
            val nextUrl = screenSizedUrl(photos[(index + 1) % size], maxDimPx)
            imageLoader.enqueue(ImageRequest.Builder(context).data(nextUrl).size(maxDimPx).build())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { vm.next(); true }
                    Key.DirectionLeft -> { vm.previous(); true }
                    Key.DirectionCenter, Key.Enter -> { vm.togglePlaying(); true }
                    Key.DirectionUp, Key.Menu -> { onOpenSettings(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading && photos.isEmpty() -> Text(stringResource(R.string.loading_photos), color = Color.White)
            error != null && photos.isEmpty() -> ErrorState(error!!)
            photos.isEmpty() -> EmptyState()
            else -> {
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { transitionFor(settings.transition) },
                    label = "photo",
                ) { targetIndex ->
                    val photo = photos.getOrNull(targetIndex)
                    if (photo != null) {
                        PhotoImage(
                            url = screenSizedUrl(photo, maxDimPx),
                            targetPx = maxDimPx,
                            imageLoader = imageLoader,
                            scaleMode = settings.scaleMode,
                            kenBurns = settings.transition == Transition.KEN_BURNS,
                            intervalMs = settings.intervalSeconds * 1000,
                            animKey = targetIndex,
                        )
                    }
                }
            }
        }

        if (settings.showClock) {
            ClockOverlay(
                use24h = settings.clock24h,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp),
            )
        }

        // Night dimming veil.
        if (isNight) {
            Box(Modifier.fillMaxSize().background(Color(0xFF000000)))
        }
    }
}

@Composable
private fun PhotoImage(
    url: String,
    targetPx: Int,
    imageLoader: ImageLoader,
    scaleMode: ScaleMode,
    kenBurns: Boolean,
    intervalMs: Int,
    animKey: Int,
) {
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current).data(url).size(targetPx).build(),
        imageLoader = imageLoader,
    )
    val scale = remember(animKey) { Animatable(1f) }
    LaunchedEffect(animKey, kenBurns) {
        if (kenBurns) {
            scale.snapTo(1f)
            scale.animateTo(1.12f, animationSpec = tween(intervalMs.coerceAtLeast(1000), easing = LinearEasing))
        }
    }

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = when (scaleMode) {
            ScaleMode.FIT -> ContentScale.Fit
            ScaleMode.FILL -> ContentScale.Crop
            ScaleMode.STRETCH -> ContentScale.FillBounds
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read the animated value in the draw phase so it doesn't recompose every frame.
                val s = if (kenBurns) scale.value else 1f
                scaleX = s
                scaleY = s
            },
    )
}

@Composable
private fun ErrorState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.error_load_title), color = Color.White)
        Text(message, color = Color(0xFFB0B8C4))
        Text(stringResource(R.string.hint_up_settings), color = Color(0xFFB0B8C4))
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.empty_no_photos), color = Color.White)
        Text(stringResource(R.string.empty_pick_folder), color = Color(0xFFB0B8C4))
    }
}

private fun transitionFor(t: Transition): ContentTransform = when (t) {
    Transition.FADE, Transition.KEN_BURNS ->
        fadeIn(tween(900)) togetherWith fadeOut(tween(900))
    Transition.SLIDE ->
        (slideInHorizontally(tween(600)) { it } + fadeIn(tween(600))) togetherWith
            (slideOutHorizontally(tween(600)) { -it } + fadeOut(tween(600)))
    Transition.SLIDE_UP ->
        (slideInVertically(tween(600)) { it } + fadeIn(tween(600))) togetherWith
            (slideOutVertically(tween(600)) { -it } + fadeOut(tween(600)))
    Transition.ZOOM ->
        (scaleIn(tween(700), initialScale = 0.85f) + fadeIn(tween(700))) togetherWith
            (scaleOut(tween(700), targetScale = 1.1f) + fadeOut(tween(700)))
    Transition.NONE ->
        fadeIn(tween(1)) togetherWith fadeOut(tween(1))
}

/** URL to load a photo at [maxPx]: a screen-sized Drive thumbnail when available, else the original. */
private fun screenSizedUrl(photo: Photo, maxPx: Int): String =
    photo.thumbnailLink?.let { sizedThumbnail(it, maxPx) } ?: photo.url

/** Rewrite a Drive thumbnail link's size token (e.g. `=s220`) to the display's largest dimension. */
private fun sizedThumbnail(thumbnailLink: String, maxPx: Int): String {
    val token = "=s$maxPx"
    return if (Regex("=[sw]\\d").containsMatchIn(thumbnailLink))
        thumbnailLink.replace(Regex("=[sw][^=]*$"), token)
    else thumbnailLink + token
}

private fun hourNow(): Int =
    java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

/** True if [hour] falls in the (possibly midnight-wrapping) window [start, end). */
private fun withinHours(hour: Int, start: Int, end: Int): Boolean =
    if (start == end) false
    else if (start < end) hour in start until end
    else hour >= start || hour < end
