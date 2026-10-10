package org.localsend.localsend_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dibuja TODO el widget en una imagen: fondo, título y frase.
 * Así podemos usar cualquier fuente, contorno y foto sin los límites de RemoteViews.
 */
object WidgetRenderer {

    private val DARK_INK = Color.parseColor("#2B2838")

    fun luminance(color: Int): Float =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * a).roundToInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * a).roundToInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * a).roundToInt()
        )
    }

    fun render(
        ctx: Context, cfg: WidgetStyle, widgetId: Int,
        w: Int, h: Int, phrase: String, dayOfYear: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radius = min(w, h) * 0.14f
        val bgLum = drawBackground(ctx, cfg, widgetId, canvas, rect, radius, dayOfYear)
        drawTexts(ctx, cfg, canvas, w, h, phrase, bgLum)
        return bmp
    }

    // ---------------------------------------------------------------- Fondo

    private fun dailyPalette(cfg: WidgetStyle): IntArray =
        if (cfg.dailyAuto || cfg.dailyColors.isEmpty()) PASTEL_COLORS else cfg.dailyColors.toIntArray()

    private fun drawBackground(
        ctx: Context, cfg: WidgetStyle, widgetId: Int,
        canvas: Canvas, rect: RectF, radius: Float, day: Int
    ): Float {
        val w = rect.width()
        val h = rect.height()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (cfg.mode == MODE_PHOTO) {
            val lum = drawPhoto(ctx, cfg, widgetId, canvas, rect, radius, day)
            if (lum != null) return lum
        }

        val pal = dailyPalette(cfg)
        val n = pal.size

        if (cfg.mode == MODE_GRADIENT) {
            val c1: Int
            val c2: Int
            if (cfg.daily) {
                val auto = cfg.dailyAuto || cfg.dailyColors.isEmpty()
                c1 = pal[day % n]
                c2 = if (n >= 2) pal[(day + (if (auto) 4 else 1)) % n] else blend(c1, Color.WHITE, 0.4f)
            } else {
                c1 = cfg.color1
                c2 = cfg.color2
            }
            paint.shader = LinearGradient(0f, 0f, w, h, c1, c2, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, paint)
            return (luminance(c1) + luminance(c2)) / 2f
        }

        // Color sólido (también es el plan B si no hay foto)
        val solid = if (cfg.daily && cfg.mode != MODE_PHOTO) pal[day % n] else cfg.color1
        paint.color = solid
        canvas.drawRoundRect(rect, radius, radius, paint)
        return luminance(solid)
    }

    /**
     * Foto del día respetando el recorte que eligió el usuario.
     * Devuelve la luminosidad del fondo, o null si no hay foto que mostrar.
     */
    private fun drawPhoto(
        ctx: Context, cfg: WidgetStyle, widgetId: Int,
        canvas: Canvas, rect: RectF, radius: Float, day: Int
    ): Float? {
        val item = WidgetStore.photoFor(cfg, widgetId, day) ?: return null
        val file = WidgetStore.photoFile(ctx, widgetId, item.id)
        if (!file.exists()) return null

        val w = rect.width().toInt()
        val h = rect.height().toInt()
        if (w <= 0 || h <= 0) return null

        val photo = loadPhoto(file, item, w, h) ?: return null
        val bw = photo.width.toFloat()
        val bh = photo.height.toFloat()

        val cl = item.l.coerceIn(0f, 1f) * bw
        val ct = item.t.coerceIn(0f, 1f) * bh
        val cr = max(item.r.coerceIn(0f, 1f) * bw, cl + 1f)
        val cb = max(item.b.coerceIn(0f, 1f) * bh, ct + 1f)
        val cw = cr - cl
        val ch = cb - ct

        val src = RectF(cl, ct, cr, cb)
        val dst = RectF(0f, 0f, w.toFloat(), h.toFloat())

        when (cfg.fitMode) {
            FIT_STRETCH -> {
                // el recorte completo se estira hasta llenar el widget
            }
            FIT_BARS -> {
                // se ve todo el recorte, sin deformarlo; lo que sobra se rellena con barras
                val s = min(w / cw, h / ch)
                val dw = cw * s
                val dh = ch * s
                dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            }
            else -> {
                // se llena el widget usando solo lo que está dentro del recorte
                val wa = w.toFloat() / h
                val ca = cw / ch
                if (ca > wa) {
                    val nw = ch * wa
                    val cx = (cl + cr) / 2f
                    src.set(cx - nw / 2f, ct, cx + nw / 2f, cb)
                } else {
                    val nh = cw / wa
                    val cy = (ct + cb) / 2f
                    src.set(cl, cy - nh / 2f, cr, cy + nh / 2f)
                }
            }
        }

        val srcI = Rect(
            src.left.roundToInt().coerceIn(0, photo.width - 1),
            src.top.roundToInt().coerceIn(0, photo.height - 1),
            src.right.roundToInt().coerceIn(1, photo.width),
            src.bottom.roundToInt().coerceIn(1, photo.height)
        )

        // Se arma en una capa aparte para que las esquinas redondeadas queden suaves
        val layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        lc.drawColor(cfg.barColor)
        val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        lc.drawBitmap(photo, srcI, dst, bp)

        var lum = photoLuminance(photo, srcI)
        if (cfg.dim > 0) {
            val dimPaint = Paint()
            dimPaint.color = Color.argb(cfg.dim * 255 / 100, 0, 0, 0)
            lc.drawRect(dst, dimPaint)
            lum *= (1f - cfg.dim / 100f)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(layer, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        layer.recycle()
        return lum
    }

    private var cachePath: String? = null
    private var cacheStamp = 0L
    private var cacheSample = 0
    private var cacheBmp: Bitmap? = null

    /** Libera la foto en memoria (se llama al terminar de dibujar el widget). */
    fun clearCache() {
        cacheBmp = null
        cachePath = null
    }

    private fun loadPhoto(f: java.io.File, item: PhotoItem, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Resolución suficiente para la parte recortada (no hace falta cargar más)
        val cropW = (item.r - item.l).coerceAtLeast(0.01f) * bounds.outWidth
        val cropH = (item.b - item.t).coerceAtLeast(0.01f) * bounds.outHeight
        var sample = 1
        while (cropW / (sample * 2) >= w && cropH / (sample * 2) >= h) sample *= 2

        val cached = cacheBmp
        if (cached != null && cachePath == f.path && cacheStamp == f.lastModified() && cacheSample == sample) {
            return cached
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(f.path, opts)
        cacheBmp = bmp
        cachePath = f.path
        cacheStamp = f.lastModified()
        cacheSample = sample
        return bmp
    }

    private fun photoLuminance(bmp: Bitmap, area: Rect): Float {
        var sum = 0f
        val n = 16
        for (i in 0 until n) {
            for (j in 0 until n) {
                val x = (area.left + (i + 0.5f) * area.width() / n).toInt().coerceIn(0, bmp.width - 1)
                val y = (area.top + (j + 0.5f) * area.height() / n).toInt().coerceIn(0, bmp.height - 1)
                sum += luminance(bmp.getPixel(x, y))
            }
        }
        return sum / (n * n)
    }

    // ---------------------------------------------------------------- Texto

    @Suppress("DEPRECATION")
    private fun layoutFor(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.05f, 0f, false)

    private fun drawTexts(
        ctx: Context, cfg: WidgetStyle,
        canvas: Canvas, w: Int, h: Int, phrase: String, bgLum: Float
    ) {
        if (!cfg.showTitle && !cfg.showPhrase) return

        val textColor = when (cfg.textMode) {
            TEXT_WHITE -> Color.WHITE
            TEXT_BLACK -> DARK_INK
            TEXT_CUSTOM -> cfg.customTextColor
            else -> if (bgLum > 0.6f) DARK_INK else Color.WHITE
        }
        // El contorno es del color contrario: la letra nunca "desaparece" en el fondo.
        val outlineColor =
            if (luminance(textColor) > 0.5f) Color.argb(210, 25, 22, 40) else Color.argb(210, 255, 255, 255)

        val base = min(w, h).toFloat()
        val pad = base * 0.09f
        val face = FontStore.resolve(ctx, cfg)
        val boldFace = Typeface.create(face, Typeface.BOLD)

        var top = pad + base * 0.06f // espacio libre arriba si no hay título (el botón de ajustes ocupa la esquina)

        // Título (opcional)
        if (cfg.showTitle) {
            val title = "📤 Enviar archivos"
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            titlePaint.typeface = boldFace
            titlePaint.color = textColor
            titlePaint.textSize = (base * 0.1f).coerceIn(16f, 60f)
            val availTitle = w - pad * 2 - base * 0.2f
            val measured = titlePaint.measureText(title)
            if (availTitle > 0f && measured > availTitle) {
                titlePaint.textSize = titlePaint.textSize * availTitle / measured
            }
            val titleY = pad - titlePaint.ascent()
            if (cfg.outline) {
                val sp = TextPaint(titlePaint)
                sp.style = Paint.Style.STROKE
                sp.strokeWidth = titlePaint.textSize * 0.12f
                sp.strokeJoin = Paint.Join.ROUND
                sp.color = outlineColor
                canvas.drawText(title, pad, titleY, sp)
            }
            canvas.drawText(title, pad, titleY, titlePaint)
            top = pad + (titlePaint.descent() - titlePaint.ascent()) + base * 0.05f
        }

        // Frase del día (opcional)
        if (!cfg.showPhrase) return
        val availW = (w - pad * 2).toInt()
        val availH = h - top - pad
        if (availW < 40 || availH < 24f || phrase.isBlank()) return

        val probe = TextPaint(Paint.ANTI_ALIAS_FLAG)
        probe.typeface = face
        var lo = 8f
        var hi = base * 0.3f
        repeat(14) {
            val mid = (lo + hi) / 2f
            probe.textSize = mid
            if (layoutFor(phrase, probe, availW).height <= availH) {
                lo = mid
            } else {
                hi = mid
            }
        }

        val fill = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fill.typeface = face
        fill.textSize = lo
        fill.color = textColor
        val layout = layoutFor(phrase, fill, availW)
        val y = top + (availH - layout.height) / 2f

        canvas.save()
        canvas.translate(pad, y)
        if (cfg.outline) {
            val stroke = TextPaint(fill)
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = lo * 0.1f
            stroke.strokeJoin = Paint.Join.ROUND
            stroke.color = outlineColor
            layoutFor(phrase, stroke, availW).draw(canvas)
        }
        layout.draw(canvas)
        canvas.restore()
    }
}
