package com.photoframe.tv.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframe.tv.auth.DeviceAuthManager
import com.photoframe.tv.auth.DeviceCode
import com.photoframe.tv.auth.PollResult
import com.photoframe.tv.ui.common.QrImage
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface DState {
    data object Loading : DState
    data class Waiting(val code: DeviceCode) : DState
    data class Failed(val message: String) : DState
}

/**
 * Google Drive sign-in via the Device Authorization Grant: shows a short code + URL on the TV,
 * polls until the user approves on their phone. No WebView, fully D-pad friendly.
 */
@Composable
fun DeviceConnectScreen(
    deviceAuth: DeviceAuthManager,
    onConnected: () -> Unit,
    onBack: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<DState>(DState.Loading) }

    LaunchedEffect(attempt) {
        state = DState.Loading
        val codeResult = withContext(Dispatchers.IO) { runCatching { deviceAuth.requestDeviceCode() } }
        codeResult.onSuccess { code ->
            state = DState.Waiting(code)
            val poll = withContext(Dispatchers.IO) {
                deviceAuth.pollForToken(code.deviceCode, code.interval, code.expiresIn)
            }
            when (poll) {
                PollResult.Success -> onConnected()
                PollResult.Denied -> state = DState.Failed("Access was denied on the phone.")
                PollResult.Expired -> state = DState.Failed("The code expired. Please try again.")
                is PollResult.Error -> state = DState.Failed(poll.message)
            }
        }.onFailure {
            state = DState.Failed(it.message ?: "Couldn't start sign-in.")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == Key.Back) { onBack(); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 1000.dp).padding(40.dp),
        ) {
            Text("Connect Google Drive", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)

            when (val s = state) {
                DState.Loading ->
                    Text("Preparing…", color = Color(0xFFB0B8C4), fontSize = 20.sp)

                is DState.Waiting -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(14.dp),
                    ) {
                        QrImage(s.code.qrContent, modifier = Modifier.size(260.dp))
                    }
                    Text("Scan with the Photo Frame app on your phone", color = Color(0xFFB0B8C4), fontSize = 20.sp)
                    Text(
                        "— or open ${s.code.url} and enter:",
                        color = Color(0xFF8A94A6),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(s.code.userCode, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text("Waiting for approval…", color = Color(0xFF8A94A6), fontSize = 16.sp)
                }

                is DState.Failed -> {
                    Text(s.message, color = Color(0xFFE06C75), fontSize = 20.sp, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { attempt++ }) { Text("Try again", fontSize = 18.sp) }
                        Button(onClick = onBack) { Text("Back", fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}
