package com.bitoneko.kouecanvas

import android.graphics.Bitmap

data class CanvasLayer(
    val id: String,
    var name: String,
    var bitmap: Bitmap,
    var isVisible: Boolean = true,
    var alpha: Int = 255
)
