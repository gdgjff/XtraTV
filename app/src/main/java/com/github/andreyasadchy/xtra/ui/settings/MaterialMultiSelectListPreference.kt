package com.github.andreyasadchy.xtra.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.MultiSelectListPreference
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.focusTvChoice
import com.github.andreyasadchy.xtra.util.isTv
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

    // Android TV: rows that the remote can visibly select (the standard list shows no highlight on TV)

    private var tvValues: MutableSet<String>? = null

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        val pref = preference as? MultiSelectListPreference
        val entries = pref?.entries
        val entryValues = pref?.entryValues
        if (!requireContext().isTv() || pref == null || entries == null || entryValues == null) {
            super.onPrepareDialogBuilder(builder)
            return
        }
        val selected = pref.values.toMutableSet()
        tvValues = selected
        val adapter = object : ArrayAdapter<CharSequence>(requireContext(), R.layout.tv_multi_choice_item, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent) as CheckedTextView
                val value = entryValues[position].toString()
                row.isChecked = value in selected
                row.setOnClickListener {
                    if (!selected.remove(value)) {
                        selected.add(value)
                    }
                    row.isChecked = value in selected
                }
                return row
            }
        }
        builder.setAdapter(adapter, null)
    }

    override fun onStart() {
        super.onStart()
        if (requireContext().isTv()) {
            dialog?.focusTvChoice(0)
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        val values = tvValues
        if (values != null) {
            tvValues = null
            val pref = preference as MultiSelectListPreference
            if (positiveResult && pref.callChangeListener(values)) {
                pref.values = values
            }
            return
        }
        super.onDialogClosed(positiveResult)
    }
}