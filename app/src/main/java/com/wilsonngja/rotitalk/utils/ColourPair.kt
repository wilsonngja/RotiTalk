package com.wilsonngja.rotitalk.utils

import android.graphics.Color

data class ColorPair(val foreground: Int, val background: Int)

object ColourPair {
    val colorList: List<ColorPair> = listOf(
        ColorPair(Color.parseColor("#054B9D"), Color.parseColor("#78B9D1")),
        ColorPair(Color.parseColor("#6F2B7F"), Color.parseColor("#E8CBEF")),
        ColorPair(Color.parseColor("#A07617"), Color.parseColor("#F1D79C")),
        ColorPair(Color.parseColor("#168442"), Color.parseColor("#A8D5BA")),
        ColorPair(Color.parseColor("#AB221B"), Color.parseColor("#EFCBD8")),
        ColorPair(Color.parseColor("#FF4500"), Color.parseColor("#FFDAB9")),
        ColorPair(Color.parseColor("#003366"), Color.parseColor("#CCFFFF")),
    )

    // Helper function to get a random pair
    fun getRandomPair(): ColorPair {
        return colorList.random()
    }
}
