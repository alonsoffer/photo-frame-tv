package com.photoframe.tv.ui.phone

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoframe.tv.auth.DeviceAuthManager
import com.photoframe.tv.auth.DeviceCode
import com.photoframe.tv.auth.PollResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface PState {
    data object Loading : PState
    data class Waiting(val code: DeviceCode) : PState
    data class Failed(val message: String) : PState
}

@Composable
fun PhoneConnectScreen(deviceAuth: DeviceAuthManager, onConnected: () -> Unit) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<PState>(PState.Loading) }

    LaunchedEffect(attempt) {
        state = PState.Loading
        withContext(Dispatchers.IO) { runCatching { deviceAuth.requestDeviceCode() } }
            .onSuccess { c ->
                state = PState.Waiting(c)
                val poll = withContext(Dispatchers.IO) {
                    deviceAuth.pollForToken(c.deviceCode, c.interval, c.expiresIn)
                }
                when (poll) {
                    PollResult.Success -> onConnected()
                    PollResult.Denied -> state = PState.Failed("Access was denied.")
                    PollResult.Expired -> state = PState.Failed("The code expired. Please try again.")
                    is PollResult.Error -> state = PState.Failed(poll.message)
                }
            }
            .onFailure { state = PState.Failed(it.message ?: "Couldn't start sign-in.") }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Photo Frame", fontSize = 34.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(10.dp))
        Text(
            "Connect your Google Drive to manage the photos on your frame.",
            fontSize = 16.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        when (val s = state) {
            PState.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Preparing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is PState.Waiting -> {
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(s.code.qrContent))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Approve in browser") }
                Spacer(Modifier.height(14.dp))
                Text(
                    "or open ${s.code.url} and enter code:",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(s.code.userCode, fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Waiting for approval…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is PState.Failed -> {
                Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { attempt++ }) { Text("Try again") }
            }
        }
    }
}
