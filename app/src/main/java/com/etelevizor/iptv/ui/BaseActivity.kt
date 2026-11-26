package com.etelevizor.iptv.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.etelevizor.iptv.R
import com.etelevizor.iptv.data.AppPreferences
import com.etelevizor.iptv.model.FontStyle

/**
 * Base Activity applying global text scale (85%..170%) and readable TV font family
 * across every screen, dialog, OSD, and list in E-Televizor.
 */
open class BaseActivity : AppCompatActivity() {

    protected open val isPlayerTheme: Boolean = false

    override fun attachBaseContext(newBase: Context) {
        val prefs = AppPreferences(newBase)
        val scale = prefs.fontScale.coerceIn(0.85f, 1.75f)
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = scale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = AppPreferences(this)
        if (!isPlayerTheme) {
            val themeRes = when (prefs.fontStyle) {
                FontStyle.CLEAR_BOLD -> R.style.Theme_ETelevizor_FontClearBold
                FontStyle.STANDARD -> R.style.Theme_ETelevizor_FontStandard
                FontStyle.CONDENSED -> R.style.Theme_ETelevizor_FontCondensed
                FontStyle.MONOSPACE -> R.style.Theme_ETelevizor_FontMono
            }
            setTheme(themeRes)
        }
        super.onCreate(savedInstanceState)
    }
}
