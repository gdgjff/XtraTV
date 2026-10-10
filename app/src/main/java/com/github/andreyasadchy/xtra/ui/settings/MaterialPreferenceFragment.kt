package com.github.andreyasadchy.xtra.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.isTv
import com.github.andreyasadchy.xtra.util.applyTvRowHighlight
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs

private const val KEY_TV_HOLD_TIP_SHOWN = "tv_settings_hold_tip_shown"

abstract class MaterialPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView?.applyTvRowHighlight() // TV: outline the setting the remote is on
        if (requireContext().isTv()) {
            setupTvDescriptions()
        }
    }

    // Android TV: hold OK on a setting to see what it does

    private fun setupTvDescriptions() {
        val list = listView ?: return
        list.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.setOnLongClickListener {
                    showTvDescription(it)
                    true
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        val prefs = requireContext().prefs()
        if (!prefs.getBoolean(KEY_TV_HOLD_TIP_SHOWN, false)) {
            Toast.makeText(requireContext(), R.string.tv_settings_hold_tip, Toast.LENGTH_LONG).show()
            prefs.edit { putBoolean(KEY_TV_HOLD_TIP_SHOWN, true) }
        }
    }

    private fun showTvDescription(row: View) {
        val title = row.findViewById<TextView>(android.R.id.title)?.text?.takeIf { it.isNotBlank() } ?: return
        val preference = preferenceScreen?.let { findPreferenceByTitle(it, title.toString()) }
        // The main settings screen reuses one key from the player buttons screen; tell them apart
        val screen = if (findPreference<Preference>("ui_settings") != null) "SettingsFragment" else null
        val description = SettingDescriptions.get(preference?.key, screen) ?: getString(R.string.tv_setting_no_description)
        requireContext().getAlertDialogBuilder()
            .setTitle(title)
            .setMessage(description)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun findPreferenceByTitle(group: PreferenceGroup, title: String): Preference? {
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            if (preference !is PreferenceCategory && preference.isVisible && preference.title?.toString() == title) {
                return preference
            }
            if (preference is PreferenceGroup) {
                findPreferenceByTitle(preference, title)?.let { return it }
            }
        }
        return null
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (requireContext().prefs().getBoolean(C.UI_THEME_MATERIAL3, true)) {
            when (preference) {
                is ListPreference -> showPreferenceDialog(preference, MaterialListPreference())
                is MultiSelectListPreference -> showPreferenceDialog(preference, MaterialMultiSelectListPreference())
                is EditTextPreference -> showPreferenceDialog(preference, MaterialEditTextPreference())
                else -> super.onDisplayPreferenceDialog(preference)
            }
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    @Suppress("DEPRECATION")
    private fun showPreferenceDialog(preference: Preference, fragment: DialogFragment) {
        fragment.arguments = bundleOf("key" to preference.key)
        fragment.setTargetFragment(this, 0)
        fragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
    }
}