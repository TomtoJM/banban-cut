package app.banban.cut

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout as TextLayout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Decoded photos, kept small enough for a phone and shared by the editor and the export. */
object Photos {
    private const val MAX_SIDE = 2160
    private val cache = object : LruCache<String, Bitmap>(160 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun peek(uri: Uri): Bitmap? = cache.get(uri.toString())

    /** Call off the main thread. */
    fun load(context: Context, uri: Uri): Bitmap? = peek(uri) ?: runCatching {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val s = info.size
            val scale = MAX_SIDE.toFloat() / maxOf(s.width, s.height)
            if (scale < 1f) decoder.setTargetSize((s.width * scale).toInt(), (s.height * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()?.also { put(uri, it) }

    fun put(uri: Uri, bitmap: Bitmap) { cache.put(uri.toString(), bitmap) }
}

/**
 * Draws a slide. The editor and the saved image use this same code, so the screen shows exactly
 * what gets posted. Words are white and bold with a soft shadow over a darkened photo, like the
 * reference carousel.
 */
object Art {
    private var typeface: Typeface? = null

    fun typeface(context: Context): Typeface = typeface ?: runCatching {
        Typeface.Builder(context.assets, "fonts/Montserrat.ttf").setFontVariationSettings("'wght' 700").build()
    }.getOrDefault(Typeface.DEFAULT_BOLD).also { typeface = it }

    /** The area of [part] on a slide of the given size. */
    fun area(slide: Slide, part: Int, w: Float, h: Float): RectF =
        if (slide.layout == Layout.Single) RectF(0f, 0f, w, h)
        else if (part == 0) RectF(0f, 0f, w, h / 2) else RectF(0f, h / 2, w, h)

    fun draw(canvas: Canvas, slide: Slide, w: Float, h: Float, typeface: Typeface, photo: (Uri) -> Bitmap?) {
        slide.frames.forEachIndexed { part, frame ->
            val r = area(slide, part, w, h)
            canvas.save()
            canvas.clipRect(r)
            val bmp = frame.uri?.let(photo)
            if (bmp != null) drawPhoto(canvas, bmp, frame, r) else canvas.drawColor(EMPTY)
            if (frame.dim > 0f) canvas.drawColor(Color.argb((frame.dim * 255).toInt(), 0, 0, 0))
            drawWords(canvas, slide, part, w, h, typeface)
            canvas.restore()
        }
    }

    /** Covers the area with the photo, then applies the frame's zoom and position. */
    private fun drawPhoto(canvas: Canvas, bmp: Bitmap, f: Frame, r: RectF) {
        val scale = maxOf(r.width() / bmp.width, r.height() / bmp.height) * f.zoom.coerceAtLeast(1f)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val slackX = (dw - r.width()) / 2
        val slackY = (dh - r.height()) / 2
        val left = r.left - slackX + f.x.coerceIn(-1f, 1f) * slackX
        val top = r.top - slackY + f.y.coerceIn(-1f, 1f) * slackY
        canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** Where nothing of Instagram's sits on top of the post. */
    fun safe(w: Float, h: Float) = RectF(w * Deck.SAFE_SIDE, h * Deck.SAFE_TOP, w * (1 - Deck.SAFE_SIDE), h * (1 - Deck.SAFE_BOTTOM))

    /**
     * Lays out a frame's words and finds where they go: centred on the frame's chosen spot, then
     * pushed back inside both the photo's own area and Instagram's safe zone, so a button or the
     * caption never covers them. Null when there are no words.
     */
    fun words(slide: Slide, part: Int, w: Float, h: Float, typeface: Typeface): Pair<StaticLayout, RectF>? {
        val f = slide.frame(part)
        if (f.text.isBlank()) return null
        val r = area(slide, part, w, h)
        val bounds = RectF(r).apply { if (!intersect(safe(w, h))) set(r) }
        val size = w * f.textSize
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            color = Color.WHITE
            setShadowLayer(size / 5f, 0f, size / 20f, Color.argb(140, 0, 0, 0))
        }
        val maxW = bounds.width().toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(f.text, 0, f.text.length, paint, maxW)
            .setAlignment(TextLayout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .build()
        var textW = 0f
        for (i in 0 until layout.lineCount) textW = maxOf(textW, layout.getLineWidth(i))
        val bw = textW.coerceAtMost(bounds.width())
        val bh = layout.height.toFloat()
        fun place(centre: Float, half: Float, lo: Float, hi: Float) =
            if (hi - lo <= half * 2) (lo + hi) / 2 else centre.coerceIn(lo + half, hi - half)
        val cx = place(r.left + f.textX * r.width(), bw / 2, bounds.left, bounds.right)
        val cy = place(r.top + f.textY * r.height(), bh / 2, bounds.top, bounds.bottom)
        return layout to RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
    }

    private fun drawWords(canvas: Canvas, slide: Slide, part: Int, w: Float, h: Float, typeface: Typeface) {
        val (layout, box) = words(slide, part, w, h, typeface) ?: return
        canvas.save()
        // The layout is as wide as the bounds; slide it so its centred lines sit over the box.
        canvas.translate(box.centerX() - layout.width / 2f, box.top)
        layout.draw(canvas)
        canvas.restore()
    }

    private val EMPTY = Color.rgb(0x33, 0x3D, 0x4B)

    /** Renders every slide at full size and saves them in order to Pictures/BanbanCut. Call off the main thread. */
    fun export(context: Context, deck: Deck, onProgress: (Int) -> Unit): List<Uri> {
        val face = typeface(context)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return deck.slides.mapIndexed { i, slide ->
            val bmp = Bitmap.createBitmap(Deck.WIDTH, Deck.HEIGHT, Bitmap.Config.ARGB_8888)
            draw(Canvas(bmp), slide, Deck.WIDTH.toFloat(), Deck.HEIGHT.toFloat(), face) { Photos.load(context, it) }
            val uri = save(context, bmp, "banban-$stamp-${"%02d".format(i + 1)}.jpg")
            bmp.recycle()
            onProgress(i + 1)
            uri
        }
    }

    private fun save(context: Context, bmp: Bitmap, name: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BanbanCut")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
        resolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

}
