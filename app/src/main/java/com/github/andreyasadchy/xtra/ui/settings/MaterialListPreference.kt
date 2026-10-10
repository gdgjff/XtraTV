package com.github.andreyasadchy.xtra.ui.settings

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.focusTvChoice
import com.github.andreyasadchy.xtra.util.isTv
import androidx.preference.ListPreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialListPreference : ListPreferenceDialogFragmentCompat() {

    private var mWhichButtonClicked = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
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

    override fun onClick(dialog: DialogInterface, which: Int) {
        mWhichButtonClicked = which
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
    }

    // Android TV: rows that the remote can visibly select (the standard list shows no highlight on TV)

    private var tvChoice = -1
    private var tvCurrent = -1

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        val pref = preference as? ListPreference
        val entries = pref?.entries
        if (!requireContext().isTv() || pref == null || entries == null || pref.entryValues == null) {
            super.onPrepareDialogBuilder(builder)
            return
        }
        tvCurrent = pref.findIndexOfValue(pref.value)
        val adapter = object : ArrayAdapter<CharSequence>(requireContext(), R.layout.tv_choice_item, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent) as CheckedTextView
                row.isChecked = position == tvCurrent
                row.setOnClickListener {
                    tvChoice = position
                    mWhichButtonClicked = DialogInterface.BUTTON_POSITIVE
                    dialog?.dismiss()
                }
                return row
            }
        }
        builder.setAdapter(adapter, null)
        builder.setPositiveButton(null, null)
    }

    override fun onStart() {
        super.onStart()
        if (requireContext().isTv()) {
            dialog?.focusTvChoice(tvCurrent)
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (tvChoice >= 0) {
            val pref = preference as ListPreference
            val value = pref.entryValues[tvChoice].toString()
            tvChoice = -1
            if (pref.callChangeListener(value)) {
                pref.value = value
            }
            return
        }
        super.onDialogClosed(positiveResult)
    }
}