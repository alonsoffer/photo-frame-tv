package com.photoframe.tv.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photoframe.tv.AppLanguages
import com.photoframe.tv.LocaleStore
import com.photoframe.tv.R
import com.photoframe.tv.auth.DeviceAuthManager
import com.photoframe.tv.data.PlayOrder
import com.photoframe.tv.data.ScaleMode
import com.photoframe.tv.data.SettingsRepository
import com.photoframe.tv.data.Transition
import com.photoframe.tv.ui.slideshow.simpleFactory
import androidx.tv.material3.Button
import androidx.tv.material3.Text

private val IntervalOptions = listOf(3, 5, 8, 10, 15, 20, 30, 60, 120, 300)
// Kept well under Drive's thumbnail-link expiry (~hours) so screen-sized links never go stale.
private val RefreshOptions = listOf(5, 10, 15, 20, 30)
private val BoolOptions = listOf(true, false)
private val HourOptions = (0..23).toList()

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    deviceAuth: DeviceAuthManager,
    isDemoMode: Boolean,
    onManageSources: () -> Unit,
    onClose: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(
        factory = simpleFactory { SettingsViewModel(settingsRepository, deviceAuth) },
    )
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Localized value labels (resolved here so the plain option-label lambdas can stay non-composable).
    val onStr = stringResource(R.string.val_on)
    val offStr = stringResource(R.string.val_off)
    val onOff: (Boolean) -> String = { if (it) onStr else offStr }

    val c12 = stringResource(R.string.clock_12h)
    val c24 = stringResource(R.string.clock_24h)
    val clockFmt: (Boolean) -> String = { if (it) c24 else c12 }

    val sFit = stringResource(R.string.scale_fit)
    val sFill = stringResource(R.string.scale_fill)
    val sStretch = stringResource(R.string.scale_stretch)
    val scaleLabel: (ScaleMode) -> String = {
        when (it) { ScaleMode.FIT -> sFit; ScaleMode.FILL -> sFill; ScaleMode.STRETCH -> sStretch }
    }

    val tFade = stringResource(R.string.trans_fade)
    val tSlide = stringResource(R.string.trans_slide)
    val tSlideUp = stringResource(R.string.trans_slide_up)
    val tZoom = stringResource(R.string.trans_zoom)
    val tKen = stringResource(R.string.trans_kenburns)
    val tNone = stringResource(R.string.trans_none)
    val transLabel: (Transition) -> String = {
        when (it) {
            Transition.FADE -> tFade
            Transition.SLIDE -> tSlide
            Transition.SLIDE_UP -> tSlideUp
            Transition.ZOOM -> tZoom
            Transition.KEN_BURNS -> tKen
            Transition.NONE -> tNone
        }
    }

    val oSeq = stringResource(R.string.order_sequential)
    val oShuffle = stringResource(R.string.order_shuffle)
    val orderLabel: (PlayOrder) -> String = {
        when (it) { PlayOrder.SEQUENTIAL -> oSeq; PlayOrder.SHUFFLE -> oShuffle }
    }

    val currentLang = remember { LocaleStore.current(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == Key.Back) { onClose(); true } else false
            }
            .padding(48.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.settings_title), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        SectionLabel(stringResource(R.string.section_language))
        DropdownSetting(
            stringResource(R.string.row_language),
            AppLanguages,
            currentLang,
            { "${it.flag}  ${it.displayName}" },
            { lang ->
                if (lang.code != LocaleStore.getCode(context)) {
                    LocaleStore.setCode(context, lang.code)
                    (context as? Activity)?.recreate()
                }
            },
        )

        SectionLabel(stringResource(R.string.section_photos))
        ActionRow(
            stringResource(R.string.sources_manage),
            s.sources.size.toString(),
            onClick = onManageSources,
        )
        DropdownSetting(stringResource(R.string.row_interval), IntervalOptions, s.intervalSeconds, { "${it}s" }, vm::setInterval)
        DropdownSetting(stringResource(R.string.row_order), PlayOrder.entries, s.order, orderLabel, vm::setOrder)
        DropdownSetting(stringResource(R.string.row_subfolders), BoolOptions, s.includeSubfolders, onOff, vm::setSubfolders)
        DropdownSetting(stringResource(R.string.row_refresh), RefreshOptions, s.refreshMinutes, ::refreshLabel, vm::setRefreshMinutes)

        SectionLabel(stringResource(R.string.section_display))
        DropdownSetting(stringResource(R.string.row_scaling), ScaleMode.entries, s.scaleMode, scaleLabel, vm::setScale)
        DropdownSetting(stringResource(R.string.row_transition), Transition.entries, s.transition, transLabel, vm::setTransition)
        DropdownSetting(stringResource(R.string.row_show_clock), BoolOptions, s.showClock, onOff, vm::setShowClock)
        DropdownSetting(stringResource(R.string.row_clock_format), BoolOptions, s.clock24h, clockFmt, vm::setClock24h)

        SectionLabel(stringResource(R.string.section_night))
        DropdownSetting(stringResource(R.string.row_dim_overnight), BoolOptions, s.nightModeEnabled, onOff, vm::setNightMode)
        DropdownSetting(stringResource(R.string.row_from), HourOptions, s.nightStartHour, ::hourLabel, vm::setNightStart)
        DropdownSetting(stringResource(R.string.row_until), HourOptions, s.nightEndHour, ::hourLabel, vm::setNightEnd)

        SectionLabel(stringResource(R.string.section_startup))
        DropdownSetting(stringResource(R.string.row_start_on_boot), BoolOptions, s.startOnBoot, onOff, vm::setStartOnBoot)

        if (isDemoMode) {
            SectionLabel(stringResource(R.string.section_account))
            Text(stringResource(R.string.demo_note), color = Color(0xFF8A94A6), fontSize = 15.sp)
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onClose) { Text(stringResource(R.string.action_close)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF5AA9E6),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

private fun hourLabel(h: Int) = "%02d:00".format(h)
private fun refreshLabel(m: Int) = if (m < 60) "${m}m" else "${m / 60}h"

