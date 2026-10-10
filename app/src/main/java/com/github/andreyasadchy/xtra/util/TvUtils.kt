package com.github.andreyasadchy.xtra.util

import android.app.Dialog
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
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
 * TV: list dialogs (theme picker, column counts...) show no highlight on the row the remote is on.
 * Outline it, both through the list's selector and on the rows themselves, and start on the current choice.
 * Call after the dialog is shown.
 */
fun Dialog.applyTvListHighlight() {
    if (!context.isTv()) return
    val list = (this as? androidx.appcompat.app.AlertDialog)?.listView ?: return
    list.selector = ContextCompat.getDrawable(context, R.drawable.tv_focus_ring)
    list.setDrawSelectorOnTop(true)
    for (i in 0 until list.childCount) {
        list.getChildAt(i).foreground = ContextCompat.getDrawable(context, R.drawable.tv_row_highlight)
    }
    list.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) {
            child?.foreground = ContextCompat.getDrawable(list.context, R.drawable.tv_row_highlight)
        }

        override fun onChildViewRemoved(parent: View?, child: View?) {}
    })
    list.post {
        list.requestFocus()
        val checked = list.checkedItemPosition
        if (checked >= 0) {
            list.setSelection(checked)
        }
    }
}

/** TV: outline the row the remote is on in a RecyclerView list (e.g. the settings screens). */
fun RecyclerView.applyTvRowHighlight() {
    if (!context.isTv()) return
    addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            if (view.foreground == null) {
                view.foreground = ContextCompat.getDrawable(view.context, R.drawable.tv_row_highlight)
            }
        }

        override fun onChildViewDetachedFromWindow(view: View) {}
    })
}
