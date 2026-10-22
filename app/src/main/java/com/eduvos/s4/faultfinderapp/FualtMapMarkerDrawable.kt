
package com.eduvos.s4.faultfinderapp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
/**
This class creates a colored pin with a white symbol inside: Used for icons on the map
 to indicate priority using color.
*/

class FaultMapMarkerDrawable(
    private val context: android.content.Context,
    @ColorInt private val pinColor: Int,
    private val iconResource: Int
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = pinColor
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }

    private val symbol = ContextCompat.getDrawable(
        context, iconResource
    )?.mutate()?.let {
        DrawableCompat.wrap(it).also { wrapped ->
            DrawableCompat.setTint(wrapped, Color.WHITE)
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / 100f, b.height() / 120f)

        val pin = Path().apply {
            moveTo(50f, 116f)
            cubicTo(44f, 105f, 10f, 67f, 10f, 43f)
            arcTo(RectF(10f, 3f, 90f, 83f), 180f, 180f)
            cubicTo(90f, 67f, 56f, 105f, 50f, 116f)
            close()
        }

        canvas.drawPath(pin, paint)
        canvas.drawPath(pin, outlinePaint)

        symbol?.let {
            val left = 29
            val top = 22
            val right = 71
            val bottom = 64

            it.setBounds(left, top, right, bottom)
            it.draw(canvas)
        }

        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        outlinePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(
        colorFilter: android.graphics.ColorFilter?
    ) {
        paint.colorFilter = colorFilter
        outlinePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = 100

    override fun getIntrinsicHeight(): Int = 120
}
