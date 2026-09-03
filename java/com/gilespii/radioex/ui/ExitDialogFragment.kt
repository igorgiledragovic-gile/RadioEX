package com.gilespii.radioex.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.gilespii.radioex.R
import com.gilespii.radioex.RadioManager
import kotlin.system.exitProcess

class ExitDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_exit, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val params = attributes
            params.dimAmount = 0.85f
            attributes = params
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnCancel = view.findViewById<Button>(R.id.btn_exit_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_exit_confirm)

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnConfirm.setOnClickListener {
            val act = activity
            if (act != null) {
                RadioManager.kill(act)
                act.stopService(android.content.Intent(act, com.gilespii.radioex.RadioPlaybackService::class.java))
                act.finishAffinity()
            }
        }

        btnConfirm.requestFocus()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        (activity as? com.gilespii.radioex.MainActivity)?.let { mainAct ->
            mainAct.runOnUiThread {
                if (mainAct.isContinueSectionVisible()) {
                    mainAct.focusContinueCard(preferActive = true)
                } else {
                    mainAct.focusActiveStation()
                }
            }
        }
    }

    companion object {
        const val TAG = "ExitDialogFragment"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved) {
                ExitDialogFragment().show(fragmentManager, TAG)
            }
        }
    }
}
