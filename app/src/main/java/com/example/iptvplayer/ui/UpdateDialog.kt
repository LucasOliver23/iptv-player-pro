package com.example.iptvplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptvplayer.MainViewModel

/** Aviso de nova versão (aparece sozinho quando o GitHub tem uma versão mais nova). */
@Composable
fun UpdateDialog(vm: MainViewModel) {
    val info = vm.update
    if (!vm.showUpdate || info == null) return
    val downloading = vm.updateProgress >= 0f

    AlertDialog(
        onDismissRequest = { if (!downloading) vm.showUpdate = false },
        containerColor = Palette.Surface,
        title = { Text("Nova versão ${info.version}", color = Color.White) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Versão instalada: ${vm.appVersion}",
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        Text(info.notes, color = Color.White, fontSize = 13.sp)
                    }
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = vm.updateProgress, modifier = Modifier.fillMaxWidth(), color = Palette.Accent)
                    Text("Baixando… ${(vm.updateProgress * 100).toInt()}%", color = Palette.TextSecondary, fontSize = 12.sp)
                }
                val msg = vm.updateMessage
                if (msg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = Palette.TextSecondary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.downloadAndInstall() }, enabled = !downloading) {
                Text("Atualizar agora", color = Palette.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showUpdate = false }, enabled = !downloading) {
                Text("Depois", color = Palette.TextSecondary)
            }
        },
    )
}
