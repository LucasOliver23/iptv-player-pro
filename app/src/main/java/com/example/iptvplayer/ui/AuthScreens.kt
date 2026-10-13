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
import androidx.compose.material3.CircularProgressIndicator
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
    val canSubmit = (fixedServers || server.isNotBlank()) && user.isNotBlank() && pass.isNotBlank() && !vm.loading

    fun submit() {
        if (canSubmit) vm.connect(server, user, pass)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("IPTV PRO", color = Palette.Accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Entre com os dados do seu serviço (Xtream Codes).",
                color = Palette.TextSecondary,
                fontSize = 14.sp,
            )

            if (!fixedServers || otherServer) OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Servidor (ex.: http://servidor.com:8080)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Usuário") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            if (fixedServers) {
                Text(
                    if (otherServer) "Usar o servidor padrão" else "Usar outro servidor",
                    color = Palette.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { otherServer = !otherServer; if (!otherServer) server = "" },
                )
            }

            Text("Formato dos canais ao vivo", color = Palette.TextSecondary, fontSize = 12.sp)
            LiveFormatPills(vm)

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
                        vm.store.user.trim().take(1).uppercase().ifEmpty { "?" },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conta", color = Palette.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(vm.store.user, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            InfoRow("Servidor") {
                Text(vm.store.server, color = Color.White, fontSize = 14.sp, maxLines = 1)
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
