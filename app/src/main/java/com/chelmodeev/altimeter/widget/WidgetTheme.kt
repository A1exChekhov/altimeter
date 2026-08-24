package com.chelmodeev.altimeter.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import com.chelmodeev.altimeter.R

internal fun RemoteViews.applyWidgetTheme(
    context: Context,
    darkTheme: Boolean,
    rootId: Int,
    primaryTextIds: IntArray,
    secondaryTextIds: IntArray = intArrayOf(),
) {
    setInt(
        rootId,
        "setBackgroundResource",
        if (darkTheme) R.drawable.widget_background_dark else R.drawable.widget_background_light,
    )
    val primary = context.getColor(
        if (darkTheme) R.color.widget_dark_text else R.color.widget_light_text,
    )
    val secondary = context.getColor(
        if (darkTheme) R.color.widget_dark_secondary else R.color.widget_light_secondary,
    )
    primaryTextIds.forEach { setTextColor(it, primary) }
    secondaryTextIds.forEach { setTextColor(it, secondary) }
}

internal fun RemoteViews.setWidgetColor(
    context: Context,
    viewId: Int,
    @ColorRes colorId: Int,
) {
    setTextColor(viewId, context.getColor(colorId))
}
