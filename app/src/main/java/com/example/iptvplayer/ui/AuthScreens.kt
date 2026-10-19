package com.example.iptvplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptvplayer.MainViewModel
import com.example.iptvplayer.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NOTICE = "Use somente serviços e conteúdos para os quais você tenha autorização."

// ---------------------------------------------------------------------------
// Carregando catálogo
// ---------------------------------------------------------------------------
@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(textSize = 34.sp)
        Spacer(Modifier.height(18.dp))
        CircularProgressIndicator(color = Palette.Accent)
        Spacer(Modifier.height(12.dp))
        Text("Carregando catálogo…", color = Palette.TextSecondary, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------------------
// Login (Xtream Codes)
// ---------------------------------------------------------------------------
@Composable
fun LoginScreen(vm: MainViewModel) {
    val fixedServers = Store.DEFAULT_SERVERS.isNotEmpty()
    var otherServer by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(if (fixedServers) "" else vm.store.server) }
    var user by remember { mutableStateOf(vm.store.user) }
    var pass by remember { mutableStateOf(vm.store.password) }
    var useM3u by remember { mutableStateOf(vm.store.sourceType == "m3u") }
    var m3uLink by remember { mutableStateOf("") }
    val canSubmit = if (useM3u) {
        m3uLink.isNotBlank() && !vm.loading
    } else {
        (fixedServers || server.isNotBlank()) && user.isNotBlank() && pass.isNotBlank() && !vm.loading
    }

    fun submit() {
        if (!canSubmit) return
        if (useM3u) vm.connectM3u(m3uLink) else vm.connect(server, user, pass)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("VORTEX PLAY", color = Palette.Accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (useM3u) "Cole o link da sua lista M3U/M3U8." else "Entre com os dados do seu serviço (Xtream Codes).",
                color = Palette.TextSecondary,
                fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Usuário e senha", !useM3u) { useM3u = false }
                Pill("Lista M3U (link)", useM3u) { useM3u = true }
            }

            if (useM3u) OutlinedTextField(
                value = m3uLink,
                onValueChange = { m3uLink = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Link da lista (http://... .m3u)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            if (!useM3u && (!fixedServers || otherServer)) OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Servidor (ex.: http://servidor.com:8080)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            if (!useM3u) OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Usuário") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            if (!useM3u) OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            if (fixedServers && !useM3u) {
                Text(
                    if (otherServer) "Usar o servidor padrão" else "Usar outro servidor",
                    color = Palette.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { otherServer = !otherServer; if (!otherServer) server = "" },
                )
            }

            if (!useM3u) {
                Text("Formato dos canais ao vivo", color = Palette.TextSecondary, fontSize = 12.sp)
                LiveFormatPills(vm)
            }

            val message = vm.errorMessage
            if (message != null) {
                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            ActionButton(
                text = if (vm.loading) "Conectando…" else "Entrar",
                onClick = { submit() },
                primary = true,
                enabled = canSubmit,
            )
            if (vm.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.Accent)
            }

            Text(NOTICE, color = Palette.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LiveFormatPills(vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("TS (padrão)", vm.liveFormat == "ts") { vm.updateLiveFormat("ts") }
        Pill("HLS (m3u8)", vm.liveFormat == "m3u8") { vm.updateLiveFormat("m3u8") }
    }
}

// ---------------------------------------------------------------------------
// Configurações
// ---------------------------------------------------------------------------
@Composable
fun SettingsScreen(vm: MainViewModel) {
    BackHandler { vm.showSettings = false }

    val catalog = vm.catalog
    val account = vm.account
    var transmissionOpen by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf<PinAction?>(null) }
    var newPin by remember { mutableStateOf<String?>(null) }

    when (pinAction) {
        PinAction.CREATE -> PinDialog(
            title = "Criar senha",
            message = "Escolha uma senha de 4 a 8 números.",
            onDismiss = { pinAction = null },
        ) { pin ->
            if (pin.length < 4) "A senha precisa ter ao menos 4 números."
            else { vm.enableParental(pin); pinAction = null; null }
        }
        PinAction.UNLOCK -> PinDialog(
            title = "Liberar conteúdo adulto",
            message = "Digite a senha do controle parental.",
            onDismiss = { pinAction = null },
        ) { pin ->
            if (!vm.checkPin(pin)) "Senha incorreta."
            else { vm.unlockAdult(); pinAction = null; null }
        }
        PinAction.DISABLE -> PinDialog(
            title = "Desativar controle parental",
            message = "Digite a senha atual.",
            onDismiss = { pinAction = null },
        ) { pin ->
            if (!vm.checkPin(pin)) "Senha incorreta."
            else { vm.clearParental(); pinAction = null; null }
        }
        PinAction.CHANGE -> PinDialog(
            title = if (newPin == null) "Trocar senha" else "Nova senha",
            message = if (newPin == null) "Digite a senha atual." else "Escolha a nova senha (4 a 8 números).",
            onDismiss = { pinAction = null; newPin = null },
        ) { pin ->
            if (newPin == null) {
                if (!vm.checkPin(pin)) "Senha incorreta." else { newPin = ""; null }
            } else {
                if (pin.length < 4) "A senha precisa ter ao menos 4 números."
                else { vm.enableParental(pin); pinAction = null; newPin = null; null }
            }
        }
        null -> Unit
    }

    val expSec = account?.expiresAtSec?.takeIf { it > 0L }
    val expiryDate = expSec?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(it * 1000L))
    }
    val leftMs = expSec?.let { it * 1000L - System.currentTimeMillis() }
    val days = leftMs?.let { it / 86_400_000L }
    val expiryColor = when {
        leftMs == null -> Palette.TextSecondary
        leftMs <= 0L || (days ?: 0L) <= 3L -> Palette.Accent
        (days ?: 0L) <= 15L -> Color(0xFFFFB020)
        else -> Color(0xFF46D369)
    }
    val expiryLabel = when {
        leftMs == null -> null
        leftMs <= 0L -> "Vencida"
        days == 0L -> "Vence hoje"
        days == 1L -> "Falta 1 dia"
        else -> "Faltam $days dias"
    }
    val statusOk = account?.status.equals("Active", ignoreCase = true)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Cabeçalho
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Voltar",
                tint = Color.White,
                modifier = Modifier
                    .focusRing(CircleShape, 2.dp)
                    .clip(CircleShape)
                    .clickable { vm.showSettings = false }
                    .padding(10.dp)
                    .size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Configurações", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // ---- Conta
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFFF2A3A), Color(0xFFA3000F)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (vm.isM3u) "M" else vm.store.user.trim().take(1).uppercase().ifEmpty { "?" },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conta", color = Palette.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        if (vm.isM3u) "Lista M3U" else vm.store.user,
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                }
            }
            InfoRow(if (vm.isM3u) "Origem" else "Servidor") {
                Text(
                    if (vm.isM3u) (android.net.Uri.parse(vm.store.m3uUrl).host ?: "lista") else vm.store.server,
                    color = Color.White, fontSize = 14.sp, maxLines = 1,
                )
            }
            if (account != null) {
                InfoRow("Status") {
                    StatusChip(
                        text = if (statusOk) "Ativa" else account.status,
                        color = if (statusOk) Color(0xFF46D369) else Color(0xFFFFB020),
                    )
                }
                if (expiryDate != null) {
                    InfoRow("Vencimento") {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(expiryDate, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (expiryLabel != null) {
                                Text(expiryLabel, color = expiryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (account.maxConnections != null) {
                    InfoRow("Conexões") {
                        Text(account.maxConnections, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // ---- Catálogo
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Canais", catalog.live.size, Modifier.weight(1f))
            StatTile("Filmes", catalog.movies.size, Modifier.weight(1f))
            StatTile("Séries", catalog.series.size, Modifier.weight(1f))
        }

        // ---- Aba recolhível: Modo de transmissão
        SettingsCard {
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRing(shape, 2.dp)
                    .clip(shape)
                    .clickable { transmissionOpen = !transmissionOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Modo de transmissão", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Canais ao vivo: " + if (vm.liveFormat == "m3u8") "HLS (m3u8)" else "TS (padrão)",
                        color = Palette.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    if (transmissionOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (transmissionOpen) {
                Text("Formato dos canais ao vivo", color = Palette.TextSecondary, fontSize = 12.sp)
                LiveFormatPills(vm)
                Text(
                    "Se algum canal não abrir, tente trocar entre TS e HLS (m3u8).",
                    color = Palette.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }

        // ---- Player (vale a partir do próximo vídeo aberto)
        SettingsCard {
            Text("Player", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Decodificador de vídeo", color = Palette.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Automático", vm.decoderMode == 0) { vm.updateDecoderMode(0) }
                Pill("Hardware", vm.decoderMode == 1) { vm.updateDecoderMode(1) }
                Pill("Software", vm.decoderMode == 2) { vm.updateDecoderMode(2) }
            }
            Text("Buffer (memória de vídeo)", color = Palette.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Normal", vm.bufferMode == 0) { vm.updateBufferMode(0) }
                Pill("Grande", vm.bufferMode == 1) { vm.updateBufferMode(1) }
                Pill("Máximo", vm.bufferMode == 2) { vm.updateBufferMode(2) }
            }
            Text(
                "Se o vídeo travar ou ficar sem imagem, tente trocar o decodificador. Em internet instável, aumente o buffer. Vale a partir do próximo vídeo.",
                color = Palette.TextMuted,
                fontSize = 11.sp,
            )
            Text("Taxa de quadros da tela (24/30/60 Hz)", color = Palette.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Automático", vm.frameRateMode == 0) { vm.updateFrameRateMode(0) }
                Pill("Desligado", vm.frameRateMode == 1) { vm.updateFrameRateMode(1) }
                Pill("Forçar", vm.frameRateMode == 2) { vm.updateFrameRateMode(2) }
            }
            Text(
                "Casa a tela com o vídeo (ex.: 24 Hz nos filmes) para evitar engasgos nas imagens. \"Forçar\" exige Android 12+ e pode piscar a tela ao trocar. Em TV, ative também \"Combinar taxa de quadros\" nos ajustes do aparelho. Vale a partir do próximo vídeo.",
                color = Palette.TextMuted,
                fontSize = 11.sp,
            )
            Text("Próximo episódio automático", color = Palette.TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Desligado", !vm.autoNext) { vm.updateAutoNext(false) }
                Pill("5s", vm.autoNext && vm.autoNextSeconds == 5) { vm.updateAutoNext(true); vm.updateAutoNextSeconds(5) }
                Pill("10s", vm.autoNext && vm.autoNextSeconds == 10) { vm.updateAutoNext(true); vm.updateAutoNextSeconds(10) }
                Pill("15s", vm.autoNext && vm.autoNextSeconds == 15) { vm.updateAutoNext(true); vm.updateAutoNextSeconds(15) }
            }
        }

        // ---- Controle parental
        SettingsCard {
            Text("Controle parental", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (!vm.parentalOn) "Desativado. Ative para esconder categorias adultas com uma senha."
                else if (vm.adultUnlocked) "Ativo • conteúdo adulto liberado nesta sessão."
                else "Ativo • categorias adultas escondidas.",
                color = Palette.TextSecondary,
                fontSize = 12.sp,
            )
            if (!vm.parentalOn) {
                Pill("Ativar com senha", false) { pinAction = PinAction.CREATE }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.adultUnlocked) {
                        Pill("Bloquear de novo", false) { vm.lockAdult() }
                    } else {
                        Pill("Liberar adulto", false) { pinAction = PinAction.UNLOCK }
                    }
                    Pill("Trocar senha", false) { pinAction = PinAction.CHANGE }
                    Pill("Desativar", false) { pinAction = PinAction.DISABLE }
                }
            }
        }

        val message = vm.errorMessage
        if (message != null) {
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.Accent)
        }

        // ---- Botões
        StyledButton(
            text = if (vm.loading) "Atualizando…" else "Atualizar catálogo",
            icon = Icons.Default.Refresh,
            primary = true,
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth(),
            onClick = { vm.reload() },
        )
        StyledButton(
            text = if (vm.updateChecking) "Verificando…" else "Verificar atualizações (v${vm.appVersion})",
            icon = Icons.Default.Refresh,
            primary = false,
            enabled = !vm.updateChecking,
            modifier = Modifier.fillMaxWidth(),
            onClick = { vm.checkUpdate(manual = true) },
        )
        vm.updateMessage?.let { Text(it, color = Palette.TextSecondary, fontSize = 12.sp) }
        StyledButton(
            text = "Sair da conta",
            icon = Icons.Default.ExitToApp,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { vm.logout() },
        )

        Text(NOTICE, color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        value()
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            java.text.NumberFormat.getIntegerInstance(Locale("pt", "BR")).format(value),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(label, color = Palette.TextSecondary, fontSize = 12.sp)
    }
}

/** Botão grande: principal (degradê vermelho) ou secundário (contorno). */
@Composable
private fun StyledButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val base = modifier
        .alpha(if (enabled) 1f else 0.5f)
        .height(52.dp)
        .focusRing(shape, 2.dp)
        .clip(shape)
    val styled = if (primary) {
        base.background(Brush.horizontalGradient(listOf(Color(0xFFFF2A3A), Color(0xFFB0000F))))
    } else {
        base.background(Color(0x1AE50914)).border(1.5.dp, Palette.Accent, shape)
    }
    Row(
        styled
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private enum class PinAction { CREATE, UNLOCK, CHANGE, DISABLE }

/** Pede uma senha numérica. [onConfirm] devolve uma mensagem de erro, ou null se deu certo. */
@Composable
private fun PinDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?,
) {
    var pin by remember(title) { mutableStateOf("") }
    var error by remember(title) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, fontSize = 13.sp)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8); error = null },
                    singleLine = true,
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { error = onConfirm(pin) }),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { error = onConfirm(pin) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
