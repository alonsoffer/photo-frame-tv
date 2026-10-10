package com.photoframe.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

private val RowMinWidth = 560.dp

/** Focus should highlight a row (color change) without scaling it up — scaling overflows wide rows. */
@Composable
private fun noGrowScale() = ButtonDefaults.scale(focusedScale = 1f)

/**
 * A settings row whose value is chosen from a drop-down list. Clicking the row opens an overlay of
 * all [options]; the current one starts focused, arrows move between them, OK selects, Back cancels.
 */
@Composable
fun <T> DropdownSetting(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Button(onClick = { expanded = true }, scale = noGrowScale(), modifier = Modifier.widthIn(min = RowMinWidth)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 18.sp)
            Text("${optionLabel(selected)}  ▾", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (expanded) {
        val focusRequester = remember { FocusRequester() }
        // Focus the current choice (or the first row if the value isn't in the list).
        val focusIndex = remember(options, selected) {
            options.indexOf(selected).let { if (it >= 0) it else 0 }
        }
        Dialog(onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 440.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141A22))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    label,
                    color = Color(0xFF5AA9E6),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                options.forEachIndexed { i, option ->
                    val isSelected = option == selected
                    Button(
                        onClick = { expanded = false; onSelect(option) },
                        scale = noGrowScale(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (i == focusIndex) Modifier.focusRequester(focusRequester) else Modifier),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(optionLabel(option), fontSize = 17.sp)
                            if (isSelected) Text("✓", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        }
    }
}

/** A settings row that runs an action (opens another screen, disconnects, …) rather than picking a value. */
@Composable
fun ActionRow(label: String, value: String, onClick: () -> Unit) {
    Button(onClick = onClick, scale = noGrowScale(), modifier = Modifier.widthIn(min = RowMinWidth)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 18.sp)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
