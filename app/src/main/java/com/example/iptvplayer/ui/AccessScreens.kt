package com.example.iptvplayer.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dados de venda exibidos no app. Para mudar preço, número ou chave Pix,
 * altere só aqui e gere uma nova versão.
 */
object Sales {
    /** Número do WhatsApp que recebe os pedidos (55 + DDD + número). */
    const val WHATSAPP = "5562982606779"
    const val WHATSAPP_LABEL = "(62) 98260-6779"

    /** Chave Pix (celular). Copiada no formato +55 para o banco não confundir com CPF. */
    const val PIX_KEY = "+5562982606779"
    const val PIX_LABEL = "(62) 98260-6779"
    const val PIX_TYPE = "Celular"

    const val PLAN_NAME = "Plano mensal"
    const val PLAN_PRICE = "R$ 20,00"
    const val PLAN_DAYS = 30

    /** Preço da 1ª tela e de cada tela a mais (em reais). */
    const val BASE_PRICE = 20
    const val EXTRA_SCREEN_PRICE = 10
    const val MAX_SCREENS = 4

    fun price(screens: Int): Int = BASE_PRICE + EXTRA_SCREEN_PRICE * (screens.coerceIn(1, MAX_SCREENS) - 1)
    fun brl(value: Int): String = "R$ $value,00"
    fun screensLabel(n: Int): String = if (n == 1) "1 tela" else "$n telas"

    const val TRIAL_HOURS = 8
}

/** Tipo de pedido enviado pelo WhatsApp. */
enum class AccessRequest { TRIAL, SUBSCRIBE, RENEW }

/** Abre o WhatsApp (ou o navegador) com a mensagem pronta. Devolve false se nada abriu. */
fun openWhatsApp(context: Context, message: String): Boolean {
    val uri = Uri.parse("https://wa.me/${Sales.WHATSAPP}?text=" + Uri.encode(message))
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun copyPix(context: Context) {
    try {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("Chave Pix", Sales.PIX_KEY))
        Toast.makeText(context, "Chave Pix copiada", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
    }
}

/** Usuário: só letras, números, ponto e sublinhado (o painel costuma recusar o resto). */
private fun cleanUser(v: String) = v.filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20)
private fun cleanPass(v: String) = v.filter { !it.isWhitespace() }.take(20)

private fun buildMessage(kind: AccessRequest, name: String, user: String, pass: String, screens: Int): String {
    val total = Sales.brl(Sales.price(screens))
    val telas = Sales.screensLabel(screens)
    return when (kind) {
        AccessRequest.TRIAL ->
            "Olá! Quero um *teste grátis de ${Sales.TRIAL_HOURS} horas* no Vortex Play.\n\n" +
                "Nome: $name\nUsuário: $user\nSenha: $pass"
        AccessRequest.SUBSCRIBE ->
            "Olá! Quero *assinar o ${Sales.PLAN_NAME.lowercase()}* do Vortex Play.\n\n" +
                "Nome: $name\nUsuário: $user\nSenha: $pass\nTelas: $telas\nValor: $total\n\n" +
                "Vou enviar o comprovante do Pix aqui."
        AccessRequest.RENEW ->
            "Olá! Quero *renovar* minha assinatura do Vortex Play.\n\n" +
                "Usuário: $user\nTelas: $telas\nValor: $total\n\nVou enviar o comprovante do Pix aqui."
    }
}

/**
 * Janela de pedido: teste grátis, assinatura ou renovação.
 * Monta a mensagem e abre o WhatsApp; a liberação é feita manualmente no painel.
 * [onSent] recebe usuário e senha para já deixar a tela de login preenchida.
 */
@Composable
fun AccessRequestDialog(
    kind: AccessRequest,
    defaultUser: String,
    defaultScreens: Int = 1,
    onDismiss: () -> Unit,
    onSent: (user: String, pass: String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf(if (kind == AccessRequest.RENEW) cleanUser(defaultUser) else "") }
    var pass by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var fallback by remember { mutableStateOf<String?>(null) }
    var screens by remember { mutableStateOf(defaultScreens.coerceIn(1, Sales.MAX_SCREENS)) }
    val total = Sales.brl(Sales.price(screens))

    val needsAccount = kind != AccessRequest.RENEW
    val userOk = user.length >= 4
    val passOk = !needsAccount || pass.length >= 4
    val nameOk = !needsAccount || name.isNotBlank()
    val canSend = userOk && passOk && nameOk

    val title = when (kind) {
        AccessRequest.TRIAL -> "Teste grátis"
        AccessRequest.SUBSCRIBE -> "Assinar"
        AccessRequest.RENEW -> "Renovar assinatura"
    }
    val subtitle = when (kind) {
        AccessRequest.TRIAL -> "${Sales.TRIAL_HOURS} horas para testar, sem pagar nada."
        AccessRequest.SUBSCRIBE -> "${Sales.PLAN_NAME} • ${Sales.PLAN_DAYS} dias"
        AccessRequest.RENEW -> "Mais ${Sales.PLAN_DAYS} dias de acesso"
    }

    fun send() {
        if (!canSend) return
        val msg = buildMessage(kind, name.trim(), user, pass, if (kind == AccessRequest.TRIAL) 1 else screens)
        if (openWhatsApp(context, msg)) {
            fallback = null
        } else {
            fallback = msg
        }
        sent = true
        if (needsAccount) onSent(user, pass)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Surface)
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Palette.TextSecondary, fontSize = 13.sp)
                }
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Fechar",
                    tint = Color.White,
                    modifier = Modifier
                        .focusRing(RoundedCornerShape(50), 2.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(8.dp)
                        .size(22.dp),
                )
            }

            if (!sent) {
                if (needsAccount) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seu nome") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                }
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = cleanUser(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (needsAccount) "Usuário que você quer" else "Seu usuário") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    supportingText = { Text("Mínimo 4 caracteres: letras, números, ponto ou _") },
                )
                if (needsAccount) {
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = cleanPass(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha que você quer") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        supportingText = { Text("Mínimo 4 caracteres. Não use a senha de outras contas.") },
                    )
                }

                if (kind != AccessRequest.TRIAL) {
                    ScreenPicker(screens) { screens = it }
                    PixBox(total, onCopy = { copyPix(context) })
                }

                Text(
                    when (kind) {
                        AccessRequest.TRIAL -> "Ao tocar em enviar, o WhatsApp abre com o pedido pronto. É só apertar enviar lá."
                        else -> "Pague o Pix, toque em enviar e mande o comprovante na conversa do WhatsApp."
                    },
                    color = Palette.TextMuted,
                    fontSize = 12.sp,
                )

                WhatsButton(
                    text = "Enviar pelo WhatsApp",
                    enabled = canSend,
                    onClick = { send() },
                )
            } else {
                Text(
                    "Pedido pronto!",
                    color = Color(0xFF46D369),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                val fb = fallback
                if (fb != null) {
                    Text(
                        "Não encontramos o WhatsApp neste aparelho. Mande esta mensagem para ${Sales.WHATSAPP_LABEL}:",
                        color = Palette.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Text(
                        fb,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.SurfaceHigh)
                            .padding(12.dp),
                    )
                }
                Text(
                    if (needsAccount) {
                        "Assim que for liberado, você recebe a confirmação no WhatsApp. Depois é só tocar em " +
                            "\"Entrar\" com o usuário e a senha que você escolheu (se o usuário já existir, " +
                            "avisaremos qual ficou)."
                    } else {
                        "Assim que o pagamento for confirmado, sua assinatura é renovada. " +
                            "Se o app estiver aberto, toque em \"Atualizar catálogo\" nas Configurações."
                    },
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                )
                if (fb == null) {
                    WhatsButton(text = "Abrir o WhatsApp de novo", enabled = true, onClick = { send() })
                }
                Pill("Fechar", selected = true, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ScreenPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Quantas telas ao mesmo tempo?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 1..Sales.MAX_SCREENS) {
                Pill(n.toString(), selected = n == selected, accent = true, modifier = Modifier.weight(1f)) { onSelect(n) }
            }
        }
        Text(
            "1 tela ${Sales.brl(Sales.BASE_PRICE)} • cada tela a mais + ${Sales.brl(Sales.EXTRA_SCREEN_PRICE)}",
            color = Palette.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PixBox(total: String, onCopy: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.SurfaceHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("PAGAMENTO VIA PIX", color = Palette.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Valor", color = Palette.TextSecondary, fontSize = 12.sp)
                Text(total, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Chave (${Sales.PIX_TYPE.lowercase()})", color = Palette.TextSecondary, fontSize = 12.sp)
                Text(Sales.PIX_LABEL, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(2.dp))
        Pill("Copiar chave Pix", selected = true, accent = true, modifier = Modifier.fillMaxWidth(), onClick = onCopy)
    }
}

@Composable
private fun WhatsButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .height(50.dp)
            .focusRing(shape, 2.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E))))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Bloco "Ainda não tem acesso?" da tela de login. */
@Composable
fun AccessOffers(onRequest: (AccessRequest) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f).height(1.dp).background(Color(0x22FFFFFF)))
            Text("  Ainda não tem acesso?  ", color = Palette.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f).height(1.dp).background(Color(0x22FFFFFF)))
        }
        OfferCard(
            title = "Teste grátis",
            detail = "${Sales.TRIAL_HOURS} horas liberadas",
            highlight = false,
        ) { onRequest(AccessRequest.TRIAL) }
        OfferCard(
            title = "Assinar",
            detail = "a partir de ${Sales.PLAN_PRICE} / mês",
            highlight = true,
        ) { onRequest(AccessRequest.SUBSCRIBE) }
        Text(
            "Assinatura vencida? Renovar",
            color = Palette.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .focusRing(RoundedCornerShape(8.dp), 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onRequest(AccessRequest.RENEW) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun OfferCard(title: String, detail: String, highlight: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val base = Modifier
        .fillMaxWidth()
        .focusRing(shape, 2.dp)
        .clip(shape)
    val styled = if (highlight) {
        base.background(Brush.horizontalGradient(listOf(Color(0xFFFF2A3A), Color(0xFFB0000F))))
    } else {
        base.background(Color(0x1AE50914)).border(1.5.dp, Palette.Accent, shape)
    }
    Row(
        styled.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(detail, color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
    }
}
