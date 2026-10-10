package com.photoframe.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.photoframe.tv.ui.PhotoFrameApp
import com.photoframe.tv.ui.phone.PhoneApp
import com.photoframe.tv.ui.theme.PhotoFrameTheme

class MainActivity : ComponentActivity() {

    // Apply the chosen UI language before any resources are resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as App).graph

        if (isTelevision()) {
            // TV: the slideshow frame — landscape, always-on, immersive.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            setContent { PhotoFrameTheme { PhotoFrameApp(graph = graph) } }
        } else {
            // Phone/tablet: the management app — portrait, touch, ordinary system bars.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            setContent { PhoneApp(graph = graph) }
        }
    }

    private fun isTelevision(): Boolean {
        val uiMode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}
