package com.example.iptvplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta escura estilo streaming. Troque Accent para mudar a cor de destaque do app inteiro. */
object Palette {
    val Background = Color(0xFF0B0B0D)
    val Surface = Color(0xFF16161A)
    val SurfaceHigh = Color(0xFF26262C)
    val Accent = Color(0xFFE50914)
    val TextSecondary = Color(0xFFB8B8C0)
    val TextMuted = Color(0xFF7C7C86)
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.White,
            background = Palette.Background,
            onBackground = Color.White,
            surface = Palette.Surface,
            onSurface = Color.White,
            surfaceVariant = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.TextSecondary,
            error = Color(0xFFFF6B6B),
        ),
        content = content,
    )
}
