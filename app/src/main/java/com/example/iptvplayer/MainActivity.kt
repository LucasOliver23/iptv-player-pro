package com.example.iptvplayer

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iptvplayer.ui.AccessRequestDialog
import com.example.iptvplayer.ui.DetailsScreen
import com.example.iptvplayer.ui.GuideScreen
import com.example.iptvplayer.ui.HomeShell
import com.example.iptvplayer.ui.IptvTheme
import com.example.iptvplayer.ui.LoginScreen
import com.example.iptvplayer.ui.Palette
import com.example.iptvplayer.ui.PlayerScreen
import com.example.iptvplayer.ui.SettingsScreen
import com.example.iptvplayer.ui.SplashScreen
import com.example.iptvplayer.ui.UpdateDialog

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(vm)
        }
    }

    /** Coloca o vídeo numa janela flutuante (Android 8+). */
    fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || vm.playing == null) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } catch (_: Exception) {
        }
    }

    // Apertou Home / trocou de app com um vídeo tocando: abre a janela flutuante.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (vm.playing != null) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.inPip = isInPictureInPictureMode
    }
}

/**
 * Navegação simples por estado (sem biblioteca extra):
 *   Player > Configurações > Login > Carregando > Detalhes > Início (abas)
 * O botão "voltar" de cada tela desfaz apenas o seu próprio nível.
 */
@Composable
private fun App(vm: MainViewModel) {
    IptvTheme {
        Surface(Modifier.fillMaxSize(), color = Palette.Background) {
            val playing = vm.playing
            val detail = vm.detail
            when {
                playing != null -> PlayerScreen(vm, playing)
                vm.showSettings -> SettingsScreen(vm)
                vm.showLogin -> LoginScreen(vm)
                vm.catalog.isEmpty -> SplashScreen()
                detail != null -> DetailsScreen(vm, detail)
                vm.showGuide -> GuideScreen(vm)
                else -> HomeShell(vm)
            }
            vm.accessRequest?.let { kind ->
                AccessRequestDialog(
                    kind = kind,
                    defaultUser = vm.store.user,
                    // Na renovação, já começa com as telas que a conta tem hoje.
                    defaultScreens = vm.account?.maxConnections?.toIntOrNull() ?: 1,
                    onDismiss = { vm.accessRequest = null },
                    onSent = { u, p -> vm.loginPrefill = u to p },
                )
            }
            UpdateDialog(vm)
        }
    }
}
