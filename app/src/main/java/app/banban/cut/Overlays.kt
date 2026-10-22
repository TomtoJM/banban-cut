package app.banban.cut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/** Draws the words that sit over the video. Everything stays inside the template's safe area. */
object Overlays {
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    /** Full-frame transparent layer: time caption in the top half, hook on the seam. */
    fun captions(t: Template, caption: String, hook: String): Bitmap {
        val bmp = Bitmap.createBitmap(t.width, t.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (caption.isNotBlank()) {
            val p = textPaint(t.timeSize, t.timeColor)
            c.drawText(caption, t.width / 2f, t.timeY + t.timeSize / 3f, p)
        }
        if (hook.isNotBlank()) drawHook(c, t, hook)
        return bmp
    }

    private fun drawHook(c: Canvas, t: Template, hook: String) {
        val p = TextPaint(textPaint(t.hookSize, t.hookColor))
        p.textAlign = Paint.Align.LEFT
        val maxWidth = t.width - 2 * t.safeSide
        val layout = StaticLayout.Builder.obtain(hook, 0, hook.length, p, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(t.hookMaxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save()
        c.translate(t.safeSide.toFloat(), t.hookY - layout.height / 2f)
        layout.draw(c)
        c.restore()
    }

    private fun textPaint(size: Int, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bold
        textSize = size.toFloat()
        this.color = color
        textAlign = Paint.Align.CENTER
        // A soft shadow keeps white words readable on bright footage and on the white app screen.
        setShadowLayer(size / 6f, 0f, size / 24f, Color.argb(170, 0, 0, 0))
    }

    /** The closing card, drawn whole and then cut into the top and bottom halves. */
    fun endCard(t: Template): Pair<Bitmap, Bitmap> {
        val full = Bitmap.createBitmap(t.width, t.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(full)
        c.drawColor(t.endBackground)
        val cx = t.width / 2f
        val cy = t.height / 2f
        val icon = t.width * 0.16f
        val iconTop = cy - icon - 40
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.endAccent }
        c.drawRoundRect(RectF(cx - icon / 2, iconTop, cx + icon / 2, iconTop + icon), icon * 0.27f, icon * 0.27f, fill)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(cx + icon * 0.18f, iconTop + icon * 0.32f, icon * 0.15f, dot)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 96f; color = Color.rgb(25, 31, 40); textAlign = Paint.Align.CENTER
        }
        c.drawText(t.endTitle, cx, cy + 90, title)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 52f; color = Color.rgb(107, 118, 132); textAlign = Paint.Align.CENTER
        }
        c.drawText(t.endSubtitle, cx, cy + 170, sub)
        val top = Bitmap.createBitmap(full, 0, 0, t.width, t.halfHeight)
        val bottom = Bitmap.createBitmap(full, 0, t.halfHeight, t.width, t.halfHeight)
        full.recycle()
        return top to bottom
    }
}
