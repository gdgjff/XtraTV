package com.github.andreyasadchy.xtra.ui.settings

import com.github.andreyasadchy.xtra.util.applyTvListHighlight
import android.app.Dialog
import android.os.Bundle
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialMultiSelectListPreference : MultiSelectListPreferenceDialogFragmentCompat() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.dialogTitle)
            .setIcon(preference.dialogIcon)
            .setPositiveButton(preference.positiveButtonText, this)
            .setNegativeButton(preference.negativeButtonText, this)
        val contentView = onCreateDialogView(requireContext())
        if (contentView != null) {
            onBindDialogView(contentView)
            builder.setView(contentView)
        } else {
            builder.setMessage(preference.dialogMessage)
        }
        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.applyTvListHighlight()
    }
}