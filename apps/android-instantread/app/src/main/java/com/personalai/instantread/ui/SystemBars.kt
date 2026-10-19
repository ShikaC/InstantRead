package com.personalai.instantread.ui

import android.app.Activity
import android.graphics.Color
import android.view.View

object SystemBars {
    fun applyFigmaCanvas(activity: Activity) {
        activity.window.statusBarColor = Color.WHITE
        activity.window.navigationBarColor = Color.WHITE
        activity.window.decorView.systemUiVisibility =
            activity.window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }
}
