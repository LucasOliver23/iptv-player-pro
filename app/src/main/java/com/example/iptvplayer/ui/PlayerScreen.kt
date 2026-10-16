package com.example.iptvplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.onFocusChanged
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.iptvplayer.MainViewModel
import com.example.iptvplayer.data.EpgProgram
import com.example.iptvplayer.data.PlayKind
import com.example.iptvplayer.data.PlayRequest
import com.example.iptvplayer.data.USER_AGENT
import kotlinx.coroutines.delay

private class PlayerViewRef {
    var value: PlayerView? = null
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainViewModel, request: PlayRequest) {
    val context = LocalContext.current
    val view = LocalView.current
    val isLive = request.kind == PlayKind.LIVE

    var locked by remember(request.key) { mutableStateOf(false) }
    var lockHint by remember(request.key) { mutableStateOf(false) }
    var lockHintTick by remember { mutableStateOf(0) }

    BackHandler {
        if (locked) { lockHint = true; lockHintTick++ } else vm.stopPlayback()
    }
    LaunchedEffect(lockHintTick) {
        if (lockHint) { delay(3_000); lockHint = false }
    }

    val url = remember(request.key) { vm.urlFor(request) }
    val player = remember(request.key) { createPlayer(context, url, request.startPositionMs, vm.decoderMode, vm.bufferMode) }
    var errorText by remember(request.key) { mutableStateOf<String?>(null) }
    var overlayVisible by remember(request.key) { mutableStateOf(true) }
    var epg by remember(request.key) { mutableStateOf<List<EpgProgram>>(emptyList()) }
    val viewRef = remember { PlayerViewRef() }
    val boxFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val buttonsFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    var buttonsFocused by remember { mutableStateOf(false) }
    var showTracks by remember(request.key) { mutableStateOf(false) }
    var tracks by remember(request.key) { mutableStateOf(Tracks.EMPTY) }
    BackHandler(enabled = showTracks) { showTracks = false }
    BackHandler(enabled = vm.nextUp != null) { vm.cancelNext() }

    // Contagem regressiva do próximo episódio.
    val nextUp = vm.nextUp
    var countdown by remember(nextUp?.key) { mutableStateOf(vm.autoNextSeconds) }
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(nextUp?.key) {
        if (nextUp != null) {
            runCatching { nextFocus.requestFocus() }
            while (countdown > 0) {
                delay(1_000)
                countdown--
            }
            vm.confirmNext()
        }
    }

    // Rotação forçada para Horizontal (Landscape) ao abrir o Player
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Tela cheia (barras do sistema escondidas) e tela sempre ligada durante o vídeo.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val previousKeepOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepOn
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Erros, fim do vídeo e liberação do player (salvando o ponto onde parou).
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlayerError(error: PlaybackException) {
                buttonsFocused = false
                errorText = "Erro de reprodução (${error.errorCodeName})"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) vm.onPlaybackEnded(request)
            }
        }
        player.addListener(listener)
        tracks = player.currentTracks
        onDispose {
            vm.saveProgress(request, player.currentPosition, player.duration)
            player.removeListener(listener)
            player.release()
        }
    }

    // Pausa ao sair do app; em canal ao vivo, volta ao vivo ao retornar.
    DisposableEffect(player) {
        val owner = context.findActivity() as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                vm.saveProgress(request, player.currentPosition, player.duration)
                player.pause()
            } else if (event == Lifecycle.Event.ON_START && isLive) {
                player.seekToDefaultPosition()
                player.play()
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    // Salva o progresso a cada 5 s (filmes e episódios).
    LaunchedEffect(player) {
        if (!isLive) {
            while (true) {
                delay(5_000)
                vm.saveProgress(request, player.currentPosition, player.duration)
            }
        }
    }

    // Atualiza a programação do canal a cada minuto.
    LaunchedEffect(request.key) {
        if (isLive) {
            while (true) {
                epg = vm.shortEpg(request.refId, 6)
                delay(60_000)
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { boxFocus.requestFocus() } }
    LaunchedEffect(showTracks) {
        if (showTracks) {
            delay(80)
            runCatching { panelFocus.requestFocus() }
        } else {
            runCatching { boxFocus.requestFocus() }
        }
    }
    LaunchedEffect(errorText) {
        if (errorText != null) runCatching { retryFocus.requestFocus() }
    }

    // Gestos de brilho (lado esquerdo) e volume (lado direito) nos filmes e séries.
    var gesture by remember { mutableStateOf<Pair<Int, Float>?>(null) } // 0 = brilho, 1 = volume
    var gestureDragging by remember { mutableStateOf(false) }
    var gestureTick by remember { mutableStateOf(0) }
    LaunchedEffect(gestureTick) {
        delay(900)
        if (!gestureDragging) gesture = null
    }
    DisposableEffect(Unit) {
        // Ao sair do player, o brilho volta ao do sistema.
        onDispose { setBrightness(context.findActivity(), -1f) }
    }

    // Dica de como trocar de canal (aparece uma vez por sessão).
    var hintVisible by remember(request.key) { mutableStateOf(false) }
    LaunchedEffect(request.key) {
        if (isLive && !vm.zapHintShown) {
            vm.zapHintShown = true
            hintVisible = true
            delay(4_000)
            hintVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Deslizar para cima/baixo no canal ao vivo troca de canal (não consome o toque).
            .pointerInput(isLive, request.key, locked) {
                if (!isLive || locked) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var total = Offset.Zero
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                pressed = false
                            } else {
                                total += change.positionChange()
                                pressed = change.pressed
                            }
                        }
                        val threshold = 120.dp.toPx()
                        if (abs(total.y) > threshold && abs(total.y) > abs(total.x) * 1.5f) {
                            // Dedo sobe = próximo canal; dedo desce = canal anterior.
                            vm.zapChannel(if (total.y < 0f) 1 else -1)
                        }
                    }
                }
            }
            // Filmes/séries: deslizar para cima/baixo à esquerda = brilho, à direita = volume.
            .pointerInput(isLive, locked) {
                if (isLive || locked) return@pointerInput
                val activity = context.findActivity()
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val stream = android.media.AudioManager.STREAM_MUSIC
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // Ignora toques na barra de progresso (embaixo) e na borda de cima.
                        if (down.position.y > size.height - 90.dp.toPx() || down.position.y < 40.dp.toPx()) continue
                        val leftSide = down.position.x < size.width / 2f
                        val slop = 18.dp.toPx()
                        val maxVol = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
                        var total = Offset.Zero
                        var adjusting = false
                        var startValue = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            total += change.positionChange()
                            if (!adjusting && abs(total.y) > slop && abs(total.y) > abs(total.x) * 1.5f) {
                                adjusting = true
                                gestureDragging = true
                                startValue = if (leftSide) currentBrightness(activity)
                                else audio.getStreamVolume(stream).toFloat() / maxVol
                                total = Offset.Zero
                            }
                            if (adjusting) {
                                change.consume()
                                val v = (startValue - total.y / (size.height * 0.75f)).coerceIn(0f, 1f)
                                if (leftSide) {
                                    setBrightness(activity, v)
                                } else {
                                    runCatching { audio.setStreamVolume(stream, Math.round(v * maxVol), 0) }
                                }
                                gesture = (if (leftSide) 0 else 1) to v
                            }
                        }
                        if (adjusting) {
                            gestureDragging = false
                            gestureTick++
                        }
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (locked) {
                    // Bloqueado: ignora tudo; segurar OK/Enter desbloqueia (controle remoto).
                    if (event.type == KeyEventType.KeyDown) {
                        val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (isOk && event.nativeKeyEvent.repeatCount >= 10) {
                            locked = false
                        } else {
                            lockHint = true; lockHintTick++
                        }
                    }
                    true
                } else if (errorText != null || vm.nextUp != null || event.type != KeyEventType.KeyDown || showTracks) {
                    false
                } else if (buttonsFocused) {
                    // Foco nos botões do topo: setas movem entre eles; "baixo" volta ao vídeo.
                    if (event.key == Key.DirectionDown) {
                        runCatching { boxFocus.requestFocus() }
                        true
                    } else {
                        false
                    }
                } else {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                            player.playWhenReady = !player.playWhenReady
                            viewRef.value?.showController()
                            true
                        }
                        Key.MediaPlay -> {
                            player.play()
                            true
                        }
                        Key.MediaPause -> {
                            player.pause()
                            true
                        }
                        Key.DirectionLeft, Key.MediaRewind -> {
                            if (isLive) {
                                false
                            } else {
                                seekBy(player, -10_000L)
                                viewRef.value?.showController()
                                true
                            }
                        }
                        Key.DirectionRight, Key.MediaFastForward -> {
                            if (isLive) {
                                false
                            } else {
                                seekBy(player, 10_000L)
                                viewRef.value?.showController()
                                true
                            }
                        }
                        Key.ChannelUp -> {
                            if (isLive) { vm.zapChannel(1); true } else false
                        }
                        Key.ChannelDown -> {
                            if (isLive) { vm.zapChannel(-1); true } else false
                        }
                        Key.DirectionUp -> {
                            if (isLive && !overlayVisible) {
                                vm.zapChannel(-1)
                                true
                            } else if (overlayVisible) {
                                runCatching { buttonsFocus.requestFocus() }
                            } else {
                                viewRef.value?.showController()
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (isLive && !overlayVisible) {
                                vm.zapChannel(1)
                            } else {
                                viewRef.value?.showController()
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusRequester(boxFocus)
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            overlayVisible = visibility == View.VISIBLE
                        }
                    )
                    viewRef.value = this
                }
            },
            update = {
                it.useController = !vm.inPip && !locked
                it.player = player
                it.resizeMode = when (vm.videoScale) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
        )

        // Botões de áudio/legendas e tamanho da tela (aparecem junto com os controles).
        if ((overlayVisible || buttonsFocused) && !showTracks && errorText == null && !vm.inPip && !locked) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .onFocusChanged { buttonsFocused = it.hasFocus },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerIconButton(
                    icon = PlayerIcons.Subtitles,
                    description = "Áudio e legendas",
                    modifier = Modifier.focusRequester(buttonsFocus),
                ) {
                    buttonsFocused = false
                    showTracks = true
                }
                PlayerIconButton(icon = PlayerIcons.AspectRatio, description = "Tamanho da tela") {
                    val next = (vm.videoScale + 1) % 3
                    vm.updateVideoScale(next)
                    Toast.makeText(context, "Tela: " + scaleLabel(next), Toast.LENGTH_SHORT).show()
                    viewRef.value?.showController()
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    PlayerIconButton(icon = PlayerIcons.Pip, description = "Janela flutuante") {
                        (context.findActivity() as? com.example.iptvplayer.MainActivity)?.enterPip()
                    }
                }
                PlayerIconButton(icon = PlayerIcons.OpenExternal, description = "Player externo") {
                    player.pause()
                    openExternal(context, url, request.title)
                }
                PlayerIconButton(icon = Icons.Default.Lock, description = "Bloquear tela") {
                    buttonsFocused = false
                    locked = true
                    viewRef.value?.hideController()
                    runCatching { boxFocus.requestFocus() }
                }
            }
        }

        if (hintVisible && !vm.inPip && !locked) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    "Deslize para cima ou para baixo para trocar de canal",
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }

        if (showTracks && !vm.inPip && !locked) {
            TrackPanel(
                tracks = tracks,
                player = player,
                panelFocus = panelFocus,
                onClose = { showTracks = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (overlayVisible && !vm.inPip && !locked) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .widthIn(max = if (isLive) 360.dp else 240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                if (isLive) {
                    Text("AO VIVO", color = Palette.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    request.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = request.subtitle
                // Esconde subtítulo vazio ou "0" (ano não informado pelo servidor).
                if (!subtitle.isNullOrBlank() && subtitle.trim() != "0") {
                    Text(
                        subtitle,
                        color = Palette.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isLive) {
                    Spacer(Modifier.height(8.dp))
                    EpgList(epg, maxItems = 3)
                }
            }
        }

        gesture?.let { (kind, value) ->
            if (!vm.inPip && !locked) {
                GestureIndicator(
                    kind = kind,
                    value = value,
                    modifier = Modifier
                        .align(if (kind == 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 56.dp),
                )
            }
        }

        // Tela bloqueada: camada transparente que engole todos os toques.
        if (locked && !vm.inPip) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { lockHint = true; lockHintTick++ })
                    },
            ) {
                if (lockHint) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC000000))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { locked = false; lockHint = false },
                                        onTap = { lockHintTick++ },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tela bloqueada • segure o cadeado para desbloquear",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xCC000000))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        val message = errorText
        if (message != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Surface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    text = "Tentar novamente",
                    onClick = {
                        errorText = null
                        player.prepare()
                        player.play()
                    },
                    modifier = Modifier.focusRequester(retryFocus),
                    primary = true,
                )
                if (isLive) {
                    Spacer(Modifier.height(8.dp))
                    val other = if (vm.liveFormat == "m3u8") "ts" else "m3u8"
                    ActionButton(
                        text = "Tentar em " + if (other == "m3u8") "HLS (m3u8)" else "TS",
                        onClick = {
                            vm.updateLiveFormat(other)
                            errorText = null
                            val newUrl = vm.urlFor(request.copy(extension = other))
                            player.setMediaItem(MediaItem.fromUri(newUrl))
                            player.prepare()
                            player.play()
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    text = "Abrir em player externo",
                    onClick = { openExternal(context, url, request.title) },
                )
            }
        }

        // Próximo episódio: contagem regressiva.
        val upcoming = vm.nextUp
        if (upcoming != null && !vm.inPip) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6101014))
                    .padding(16.dp),
            ) {
                Text("A seguir em ${countdown}s", color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    upcoming.subtitle ?: upcoming.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        text = "Assistir agora",
                        onClick = { vm.confirmNext() },
                        modifier = Modifier.focusRequester(nextFocus),
                        primary = true,
                    )
                    ActionButton(text = "Cancelar", onClick = { vm.cancelNext() })
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun isSoftwareCodec(info: MediaCodecInfo): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= 29) info.softwareOnly
    else info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android.")

@OptIn(UnstableApi::class)
private fun createPlayer(context: Context, url: String, startMs: Long, decoderMode: Int, bufferMode: Int): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    // Decodificador: 0 = automático, 1 = prefere hardware, 2 = prefere software (sempre com alternativa).
    val selector = MediaCodecSelector { mimeType, secure, tunneling ->
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
        when (decoderMode) {
            1 -> all.sortedBy { if (isSoftwareCodec(it)) 1 else 0 }.toMutableList()
            2 -> all.sortedBy { if (isSoftwareCodec(it)) 0 else 1 }.toMutableList()
            else -> all
        }
    }
    val renderers = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setMediaCodecSelector(selector)

    // Buffer: 0 = normal, 1 = grande, 2 = máximo (mais estável em internet instável).
    val loadControl = when (bufferMode) {
        1 -> DefaultLoadControl.Builder().setBufferDurationsMs(30_000, 90_000, 4_000, 8_000).build()
        2 -> DefaultLoadControl.Builder().setBufferDurationsMs(60_000, 180_000, 8_000, 15_000).build()
        else -> DefaultLoadControl.Builder().build()
    }

    val player = ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
        .setLoadControl(loadControl)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    val item = MediaItem.fromUri(url)
    if (startMs > 0L) player.setMediaItem(item, startMs) else player.setMediaItem(item)
    player.prepare()
    player.playWhenReady = true
    return player
}

/** Abre o vídeo em outro app (VLC, MX Player…). O Android mostra a lista de apps instalados. */
private fun openExternal(context: Context, url: String, title: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir com…"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum player externo instalado (ex.: VLC).", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir o player externo.", Toast.LENGTH_LONG).show()
    }
}

private fun seekBy(player: ExoPlayer, deltaMs: Long) {
    val duration = player.duration
    var target = player.currentPosition + deltaMs
    if (target < 0L) target = 0L
    if (duration > 0L && target > duration) target = duration
    player.seekTo(target)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun scaleLabel(mode: Int): String = when (mode) {
    1 -> "Preencher"
    2 -> "Esticar"
    else -> "Ajustar"
}

// ---------------------------------------------------------------------------
// Painel de áudio / legendas
// ---------------------------------------------------------------------------
private class TrackOption(
    val label: String,
    val group: Tracks.Group,
    val index: Int,
    val selected: Boolean,
)

private fun channelsLabel(count: Int): String? = when {
    count <= 0 || count == Format.NO_VALUE -> null
    count == 1 -> "Mono"
    count == 2 -> "Estéreo"
    count == 6 -> "5.1"
    count == 8 -> "7.1"
    else -> "$count canais"
}

private fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und" || code == "mis" || code == "zxx") return null
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale("pt", "BR"))
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?.replaceFirstChar { it.uppercase() }
        ?: code.uppercase()
}

private fun Tracks.options(type: Int): List<TrackOption> {
    val raw = ArrayList<TrackOption>()
    var n = 0
    for (group in groups) {
        if (group.type != type) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            n++
            val format = group.getTrackFormat(i)
            val lang = languageName(format.language)
            val extra = format.label?.takeIf { it.isNotBlank() && !it.equals(lang, ignoreCase = true) }
            val base = when {
                lang != null && extra != null -> "$lang ($extra)"
                lang != null -> lang
                extra != null -> extra
                else -> "Faixa $n"
            }
            val label = if (type == C.TRACK_TYPE_AUDIO) {
                listOfNotNull(base, channelsLabel(format.channelCount)).joinToString(" • ")
            } else {
                base
            }
            raw += TrackOption(label, group, i, group.isTrackSelected(i))
        }
    }
    return raw
}

private fun selectTrack(player: Player, type: Int, option: TrackOption?) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option == null) {
        builder.setTrackTypeDisabled(type, true)
    } else {
        builder.setTrackTypeDisabled(type, false)
        builder.setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
    }
    player.trackSelectionParameters = builder.build()
}

@Composable
private fun TrackPanel(
    tracks: Tracks,
    player: Player,
    panelFocus: FocusRequester,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val audio = remember(tracks) { tracks.options(C.TRACK_TYPE_AUDIO) }
    val text = remember(tracks) { tracks.options(C.TRACK_TYPE_TEXT) }
    val textOff = text.none { it.selected }

    Column(
        modifier
            .fillMaxHeight()
            .width(340.dp)
            .background(Color(0xF2101014))
            .padding(vertical = 16.dp),
    ) {
        Text(
            "Áudio e legendas",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            item { PanelHeader("Áudio (idioma)") }
            if (audio.isEmpty()) {
                item { PanelNote("Este vídeo tem apenas uma faixa de áudio.") }
            } else {
                items(audio.size) { i ->
                    val opt = audio[i]
                    PanelRow(
                        label = opt.label,
                        selected = opt.selected,
                        modifier = if (i == 0) Modifier.focusRequester(panelFocus) else Modifier,
                    ) { selectTrack(player, C.TRACK_TYPE_AUDIO, opt) }
                }
            }
            item { PanelHeader("Legendas") }
            item {
                PanelRow(
                    label = "Desativadas",
                    selected = textOff,
                    modifier = if (audio.isEmpty()) Modifier.focusRequester(panelFocus) else Modifier,
                ) { selectTrack(player, C.TRACK_TYPE_TEXT, null) }
            }
            if (text.isEmpty()) {
                item { PanelNote("Nenhuma legenda disponível neste vídeo.") }
            } else {
                items(text.size) { i ->
                    val opt = text[i]
                    PanelRow(opt.label, opt.selected) { selectTrack(player, C.TRACK_TYPE_TEXT, opt) }
                }
            }
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ActionButton(text = "Fechar", onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PanelHeader(text: String) {
    Text(
        text,
        color = Palette.Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun PanelNote(text: String) {
    Text(
        text,
        color = Palette.TextMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun PanelRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .focusRing(shape, 2.dp)
            .clip(shape)
            .background(if (selected) Palette.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp)) {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.width(20.dp))
            }
        }
        Text(
            label,
            color = if (selected) Color.White else Palette.TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


// ---------------------------------------------------------------------------
// Botões redondos só com ícone (canto superior do player)
// ---------------------------------------------------------------------------
@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier
            .focusRing(shape, 2.dp)
            .size(42.dp)
            .clip(shape)
            .background(Color(0x8C000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Brilho da janela do app (0..1). -1 devolve o controle ao brilho do sistema. */
private fun setBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    val lp = window.attributes
    lp.screenBrightness = if (value < 0f) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else value.coerceIn(0.01f, 1f)
    window.attributes = lp
}

private fun currentBrightness(activity: Activity?): Float {
    val own = activity?.window?.attributes?.screenBrightness ?: -1f
    if (own >= 0f) return own
    return try {
        android.provider.Settings.System.getInt(activity!!.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Exception) {
        0.5f
    }
}

/** Indicador de brilho/volume enquanto o dedo desliza. */
@Composable
private fun GestureIndicator(kind: Int, value: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xAA000000))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (kind == 0) PlayerIcons.Brightness else PlayerIcons.Volume,
            contentDescription = if (kind == 0) "Brilho" else "Volume",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(6.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x44FFFFFF)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .background(Color.White),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("${Math.round(value * 100)}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Ícones do Material Design (Apache 2.0) desenhados aqui para não depender da biblioteca grande de ícones. */
private object PlayerIcons {
    private fun icon(name: String, path: String) =
        androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(path),
            fill = androidx.compose.ui.graphics.SolidColor(Color.White),
        ).build()

    val Subtitles = icon(
        "Subtitles",
        "M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM4,12h4v2H4v-2zM14,18H4v-2h10v2zM20,18h-4v-2h4v2zM20,14H10v-2h10v2z",
    )
    val AspectRatio = icon(
        "AspectRatio",
        "M19,12h-2v3h-3v2h5v-5zM7,9h3L10,7L5,7v5h2L7,9zM21,3L3,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,19.01L3,19.01L3,4.99h18v14.02z",
    )
    val Pip = icon(
        "PictureInPicture",
        "M19,11h-8v6h8v-6zM23,19L23,4.98C23,3.88 22.1,3 21,3L3,3c-1.1,0 -2,0.88 -2,1.98L1,19c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2zM21,19.02L3,19.02L3,4.97h18v14.05z",
    )
    val Brightness = icon(
        "Brightness",
        "M20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69L12,23.31 15.31,20H20v-4.69L23.31,12 20,8.69zM12,18c-3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6 6,2.69 6,6 -2.69,6 -6,6zM12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z",
    )
    val Volume = icon(
        "VolumeUp",
        "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
    )
    val OpenExternal = icon(
        "OpenInNew",
        "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z",
    )
}
