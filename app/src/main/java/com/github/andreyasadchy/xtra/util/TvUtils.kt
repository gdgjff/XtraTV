package com.github.andreyasadchy.xtra.util

import android.app.Dialog
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.github.andreyasadchy.xtra.R

/**
 * True when running on an Android TV / Google TV device (or anything driven by a D-pad remote
 * rather than a touchscreen). Used to switch on remote-friendly navigation.
 */
fun Context.isTv(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        return true
    }
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

/**
 * TV: list dialogs (like the theme picker) show no highlight on the selected row by default.
 * Draw the same focus frame used elsewhere on the row the remote is on. Call after the dialog is shown.
 */
fun Dialog.applyTvListHighlight() {
    if (!context.isTv()) return
    (this as? androidx.appcompat.app.AlertDialog)?.listView?.apply {
        selector = ContextCompat.getDrawable(context, R.drawable.tv_focus_ring)
        setDrawSelectorOnTop(true)
    }
}
