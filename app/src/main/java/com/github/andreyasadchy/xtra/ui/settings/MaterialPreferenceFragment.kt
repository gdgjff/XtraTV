package com.github.andreyasadchy.xtra.ui.settings

import androidx.preference.PreferenceScreen
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

/** Settings that only exist on the Android TV version. */
private val TV_ONLY_SETTINGS = listOf(
    "tv_live_preview", "tv_live_preview_sound", "tv_back_closes_player",
    "tv_live_catch_up", "tv_live_catch_up_start", "tv_live_catch_up_stop", "tv_live_catch_up_speed",
)

/** Settings that only matter on phones (notches, portrait, mobile data, touch, screen lock). */
private val TV_HIDDEN_SETTINGS = listOf(
    "ui_draw_behind_cutouts", // camera notch
    "columnsPortrait", // TV is always landscape
    "player_rounded_corner_padding", // rounded screen corners
    "player_default_cellular_quality", // mobile data
    "download_wifi_only", // mobile data
    "player_doubletap", // touch gesture
    "player_move_freely", // dragging the mini player by touch
    "player_chatbartoggle", // chat input (view-only on TV)
    "player_menu_chat_bar", // chat input (view-only on TV)
    "player_volumebutton", // volume button is hidden on TV; the remote controls volume
    "sleep_timer_lock", // locking the phone after the sleep timer
    "admin_settings", // device admin, only needed for the above
    "player_background_audio_locked", // phone screen lock
    "player_background_audio_pip_locked", // phone screen lock
    "player_show_stream_notification_seekbar", // phone media notification
)

abstract class MaterialPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView?.applyTvRowHighlight() // TV: outline the setting the remote is on
        if (requireContext().isTv()) {
            // No slide/fade when settings are shown or hidden: changes appear instantly
            listView?.itemAnimator = null
            setupTvDescriptions()
        }
    }

    override fun setPreferenceScreen(preferenceScreen: PreferenceScreen?) {
        super.setPreferenceScreen(preferenceScreen)
        // TV: hide phone-only settings before the list is drawn, so they never appear and vanish.
        // Their values are unchanged, so behavior stays the same.
        if (context?.isTv() == true) {
            TV_HIDDEN_SETTINGS.forEach { findPreference<Preference>(it)?.isVisible = false }
        } else {
            TV_ONLY_SETTINGS.forEach { findPreference<Preference>(it)?.isVisible = false }
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