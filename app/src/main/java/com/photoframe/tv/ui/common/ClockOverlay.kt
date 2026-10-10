package com.photoframe.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A translucent clock chip, refreshed every 10 seconds. */
@Composable
fun ClockOverlay(use24h: Boolean, modifier: Modifier = Modifier) {
    val pattern = if (use24h) "HH:mm" else "h:mm a"
    val formatter = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = formatter.format(Date(now)),
            color = Color.White,
            fontSize = 28.sp,
        )
    }
}
