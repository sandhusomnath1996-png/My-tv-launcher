package com.custom.tvlauncher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val bannerOrIcon: Drawable,
    val isBanner: Boolean
)
