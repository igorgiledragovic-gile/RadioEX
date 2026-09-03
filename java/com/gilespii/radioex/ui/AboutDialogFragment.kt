package com.gilespii.radioex.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import com.gilespii.radioex.R

class AboutDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_about, container, false)
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

        val tvSubtitle = view.findViewById<android.widget.TextView>(R.id.tv_subtitle)
        tvSubtitle?.text = getString(R.string.about_subtitle)

        val btnClose = view.findViewById<ImageButton>(R.id.btn_close)
        btnClose.setOnClickListener {
            dismiss()
        }
        btnClose.requestFocus()
    }

    companion object {
        const val TAG = "AboutDialogFragment"
    }
}
