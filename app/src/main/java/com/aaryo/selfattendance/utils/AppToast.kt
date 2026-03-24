package com.aaryo.selfattendance.utils

import android.content.Context
import android.widget.Toast

object ToastUtils {

    fun showToast(
        context: Context,
        message: String
    ) {

        Toast.makeText(
            context.applicationContext,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }
}