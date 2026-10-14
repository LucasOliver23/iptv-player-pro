package com.example.iptvplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.example.iptvplayer.NavTab
import com.example.iptvplayer.R
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Logo do app: ícone (play em degradê) + nome VORTEX PLAY. */
@Composable
fun AppLogo(modifier: Modifier = Modifier, textSize: TextUnit = 22.sp) {
    val h = textSize.value
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = null,
            modifier = Modifier.size((h * 1.5f).dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("VORTEX PLAY", color = Color.White, fontSize = textSize, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}


/** Ícone de cada aba do menu (os de Ao vivo, Filmes e Séries são desenhos próprios do app). */
@Composable
fun NavIcon(tab: NavTab, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val m = modifier.size(size)
    when (tab) {
        NavTab.HOME -> Icon(Icons.Default.Home, tab.label, tint = tint, modifier = m)
        NavTab.LIVE -> Icon(painterResource(R.drawable.ic_nav_live), tab.label, tint = tint, modifier = m)
        NavTab.MOVIES -> Icon(painterResource(R.drawable.ic_nav_movies), tab.label, tint = tint, modifier = m)
        NavTab.SERIES -> Icon(painterResource(R.drawable.ic_nav_series), tab.label, tint = tint, modifier = m)
        NavTab.LIST -> Icon(Icons.Default.Favorite, tab.label, tint = tint, modifier = m)
        NavTab.SEARCH -> Icon(Icons.Default.Search, tab.label, tint = tint, modifier = m)
    }
}
