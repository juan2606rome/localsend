package org.localsend.localsend_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min

/**
 * Dibuja TODO el widget en una imagen: fondo, título y frase.
 * Así podemos usar cualquier tipo de letra, contorno y foto de fondo sin límites de RemoteViews.
 */
object WidgetRenderer {

    private val DARK_INK = Color.parseColor("#2B2838")

    fun luminance(color: Int): Float =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f

    fun render(
        ctx: Context, cfg: WidgetStyle, widgetId: Int,
        w: Int, h: Int, phrase: String, dayOfYear: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radius = min(w, h) * 0.14f
        val bgLum = drawBackground(ctx, cfg, widgetId, canvas, rect, radius, dayOfYear)
        drawTexts(ctx, cfg, widgetId, canvas, w, h, phrase, bgLum)
        return bmp
    }

    // ---------------------------------------------------------------- Fondo

    private fun drawBackground(
        ctx: Context, cfg: WidgetStyle, widgetId: Int,
        canvas: Canvas, rect: RectF, radius: Float, day: Int
    ): Float {
        val w = rect.width()
        val h = rect.height()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Foto (con zoom y centro ajustables; siempre llena todo el widget)
        if (cfg.mode == MODE_PHOTO && cfg.hasPhoto) {
            val zoom = cfg.zoom.coerceIn(100, 300) / 100f
            val photo = loadPhoto(ctx, widgetId, (w * zoom).toInt(), (h * zoom).toInt())
            if (photo != null) {
                val scale = max(w / photo.width, h / photo.height) * zoom
                val sw = photo.width * scale
                val sh = photo.height * scale
                val tx = -(sw - w) * cfg.focusX.coerceIn(0, 100) / 100f
                val ty = -(sh - h) * cfg.focusY.coerceIn(0, 100) / 100f
                val m = Matrix()
                m.setScale(scale, scale)
                m.postTranslate(tx, ty)
                val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                shader.setLocalMatrix(m)
                paint.shader = shader
                canvas.drawRoundRect(rect, radius, radius, paint)

                var lum = photoLuminance(photo)
                if (cfg.dim > 0) {
                    val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    dimPaint.color = Color.argb(cfg.dim * 255 / 100, 0, 0, 0)
                    canvas.drawRoundRect(rect, radius, radius, dimPaint)
                    lum *= (1f - cfg.dim / 100f)
                }
                return lum
            }
        }

        val n = PASTEL_COLORS.size

        // Degradado (fijo o cambiando cada día)
        if (cfg.mode == MODE_GRADIENT) {
            val c1 = if (cfg.daily) PASTEL_COLORS[day % n] else cfg.color1
            val c2 = if (cfg.daily) PASTEL_COLORS[(day + 4) % n] else cfg.color2
            paint.shader = LinearGradient(0f, 0f, w, h, c1, c2, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, paint)
            return (luminance(c1) + luminance(c2)) / 2f
        }

        // Color sólido (fijo o cambiando cada día; también es el plan B si no hay foto)
        val solid = if (cfg.daily && cfg.mode != MODE_PHOTO) PASTEL_COLORS[day % n] else cfg.color1
        paint.color = solid
        canvas.drawRoundRect(rect, radius, radius, paint)
        return luminance(solid)
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

    private fun loadPhoto(ctx: Context, id: Int, w: Int, h: Int): Bitmap? {
        val f = WidgetStore.photoFile(ctx, id)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2

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

    private fun photoLuminance(bmp: Bitmap): Float {
        var sum = 0f
        val n = 20
        for (i in 0 until n) {
            for (j in 0 until n) {
                val x = ((i + 0.5f) * bmp.width / n).toInt().coerceIn(0, bmp.width - 1)
                val y = ((j + 0.5f) * bmp.height / n).toInt().coerceIn(0, bmp.height - 1)
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
        ctx: Context, cfg: WidgetStyle, widgetId: Int,
        canvas: Canvas, w: Int, h: Int, phrase: String, bgLum: Float
    ) {
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
        val face = FontStore.resolve(ctx, widgetId, cfg)
        val boldFace = Typeface.create(face, Typeface.BOLD)

        var top = pad + base * 0.06f // espacio libre arriba si no hay título (el emoji de ajustes ocupa la esquina)

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

        // Frase del día
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
