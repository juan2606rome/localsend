package org.localsend.localsend_app

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Vista que muestra la foto completa y un recuadro de recorte que se mueve y se redimensiona con el dedo. */
class CropView(context: Context) : View(context) {

    private enum class Drag { NONE, MOVE, TL, TR, BL, BR, L, T, R, B }

    private var bitmap: Bitmap? = null
    private val crop = RectF(0f, 0f, 1f, 1f) // proporciones de la foto (0 a 1)
    private var lockAspect: Float? = null    // ancho / alto del recorte en píxeles de la foto; null = libre

    private val img = RectF()
    private val d = resources.displayMetrics.density
    private var drag = Drag.NONE
    private var lastX = 0f
    private var lastY = 0f

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * d
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    fun setPhoto(b: Bitmap, l: Float, t: Float, r: Float, bt: Float) {
        bitmap = b
        crop.set(l, t, r, bt)
        computeImageRect()
        invalidate()
    }

    fun cropRect(): RectF = RectF(crop)

    /** Cambia la forma del recorte. null = libre. Mantiene el centro y usa el mayor rectángulo posible. */
    fun setAspect(a: Float?) {
        lockAspect = a
        val bmp = bitmap
        if (a != null && bmp != null) {
            val k = bmp.width / (a * bmp.height) // alto normalizado = ancho normalizado * k
            val cx = crop.centerX()
            val cy = crop.centerY()
            var w = crop.width()
            var h = w * k
            if (h > crop.height()) {
                h = crop.height()
                w = h / k
            }
            w = min(w, 1f)
            h = min(h, 1f)
            val l = (cx - w / 2f).coerceIn(0f, 1f - w)
            val t = (cy - h / 2f).coerceIn(0f, 1f - h)
            crop.set(l, t, l + w, t + h)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeImageRect()
    }

    private fun computeImageRect() {
        val b = bitmap ?: return
        val pad = 16f * d
        val aw = width - 2 * pad
        val ah = height - 2 * pad
        if (aw <= 0f || ah <= 0f) return
        val s = min(aw / b.width, ah / b.height)
        val dw = b.width * s
        val dh = b.height * s
        img.set((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
    }

    private fun screenCrop(): RectF = RectF(
        img.left + crop.left * img.width(),
        img.top + crop.top * img.height(),
        img.left + crop.right * img.width(),
        img.top + crop.bottom * img.height()
    )

    override fun onDraw(c: Canvas) {
        val b = bitmap ?: return
        c.drawBitmap(b, null, img, bmpPaint)

        val r = screenCrop()
        // se oscurece lo que queda fuera del recorte
        c.drawRect(img.left, img.top, img.right, r.top, dimPaint)
        c.drawRect(img.left, r.bottom, img.right, img.bottom, dimPaint)
        c.drawRect(img.left, r.top, r.left, r.bottom, dimPaint)
        c.drawRect(r.right, r.top, img.right, r.bottom, dimPaint)

        c.drawRect(r, borderPaint)
        // guías en tercios
        for (i in 1..2) {
            val x = r.left + r.width() * i / 3f
            val y = r.top + r.height() * i / 3f
            c.drawLine(x, r.top, x, r.bottom, gridPaint)
            c.drawLine(r.left, y, r.right, y, gridPaint)
        }
        // esquinas
        val hr = 8f * d
        c.drawCircle(r.left, r.top, hr, handlePaint)
        c.drawCircle(r.right, r.top, hr, handlePaint)
        c.drawCircle(r.left, r.bottom, hr, handlePaint)
        c.drawCircle(r.right, r.bottom, hr, handlePaint)
        // lados (solo cuando la forma es libre)
        if (lockAspect == null) {
            val sr = 5f * d
            c.drawCircle(r.centerX(), r.top, sr, handlePaint)
            c.drawCircle(r.centerX(), r.bottom, sr, handlePaint)
            c.drawCircle(r.left, r.centerY(), sr, handlePaint)
            c.drawCircle(r.right, r.centerY(), sr, handlePaint)
        }
    }

    private fun hit(x: Float, y: Float): Drag {
        val r = screenCrop()
        val t = 28f * d
        if (hypot(x - r.left, y - r.top) <= t) return Drag.TL
        if (hypot(x - r.right, y - r.top) <= t) return Drag.TR
        if (hypot(x - r.left, y - r.bottom) <= t) return Drag.BL
        if (hypot(x - r.right, y - r.bottom) <= t) return Drag.BR
        if (lockAspect == null) {
            val e = 22f * d
            if (abs(x - r.left) <= e && y >= r.top && y <= r.bottom) return Drag.L
            if (abs(x - r.right) <= e && y >= r.top && y <= r.bottom) return Drag.R
            if (abs(y - r.top) <= e && x >= r.left && x <= r.right) return Drag.T
            if (abs(y - r.bottom) <= e && x >= r.left && x <= r.right) return Drag.B
        }
        if (r.contains(x, y)) return Drag.MOVE
        return Drag.NONE
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drag = hit(e.x, e.y)
                lastX = e.x
                lastY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (drag != Drag.NONE && img.width() > 0f && img.height() > 0f) {
                    apply((e.x - lastX) / img.width(), (e.y - lastY) / img.height())
                    lastX = e.x
                    lastY = e.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = Drag.NONE
        }
        return true
    }

    private fun apply(nx: Float, ny: Float) {
        val minS = 0.06f
        when (drag) {
            Drag.MOVE -> {
                val w = crop.width()
                val h = crop.height()
                val l = (crop.left + nx).coerceIn(0f, 1f - w)
                val t = (crop.top + ny).coerceIn(0f, 1f - h)
                crop.set(l, t, l + w, t + h)
            }
            Drag.L -> crop.left = (crop.left + nx).coerceIn(0f, crop.right - minS)
            Drag.R -> crop.right = (crop.right + nx).coerceIn(crop.left + minS, 1f)
            Drag.T -> crop.top = (crop.top + ny).coerceIn(0f, crop.bottom - minS)
            Drag.B -> crop.bottom = (crop.bottom + ny).coerceIn(crop.top + minS, 1f)
            Drag.TL, Drag.TR, Drag.BL, Drag.BR -> resizeCorner(nx, ny)
            Drag.NONE -> {}
        }
    }

    private fun resizeCorner(nx: Float, ny: Float) {
        val minS = 0.06f
        val left = drag == Drag.TL || drag == Drag.BL
        val top = drag == Drag.TL || drag == Drag.TR
        val a = lockAspect

        if (a == null) {
            if (left) crop.left = (crop.left + nx).coerceIn(0f, crop.right - minS)
            else crop.right = (crop.right + nx).coerceIn(crop.left + minS, 1f)
            if (top) crop.top = (crop.top + ny).coerceIn(0f, crop.bottom - minS)
            else crop.bottom = (crop.bottom + ny).coerceIn(crop.top + minS, 1f)
            return
        }

        val bmp = bitmap ?: return
        val k = bmp.width / (a * bmp.height)
        val ax = if (left) crop.right else crop.left   // ancla: la esquina opuesta
        val ay = if (top) crop.bottom else crop.top
        val px = (if (left) crop.left else crop.right) + nx
        val maxW = if (left) ax else 1f - ax
        val maxH = if (top) ay else 1f - ay
        var nw = abs(px - ax)
        nw = min(nw, min(maxW, maxH / k))
        nw = max(nw, minS)
        val nh = nw * k
        val l = if (left) ax - nw else ax
        val t = if (top) ay - nh else ay
        crop.set(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), (l + nw).coerceIn(0f, 1f), (t + nh).coerceIn(0f, 1f))
    }
}

/** Pantalla para recortar una foto del widget: se elige qué parte de la foto se verá. */
class CropActivity : Activity() {

    companion object {
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_PHOTO_ID = "photo_id"
        const val EXTRA_INDEX = "index"   // posición de esta foto cuando se recortan varias seguidas
        const val EXTRA_TOTAL = "total"   // cuántas fotos se están recortando seguidas
    }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var photoId = -1
    private lateinit var cropView: CropView

    private val accent = Color.parseColor("#9C7BEA")
    private val chipBg = Color.parseColor("#3A3550")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pill(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        photoId = intent.getIntExtra(EXTRA_PHOTO_ID, -1)
        val index = intent.getIntExtra(EXTRA_INDEX, 1)
        val total = intent.getIntExtra(EXTRA_TOTAL, 1)

        val item = WidgetStore.loadPhotos(this, widgetId).firstOrNull { it.id == photoId }
        val file = WidgetStore.photoFile(this, widgetId, photoId)
        val bmp = if (item != null && file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (item == null || bmp == null) {
            finish()
            return
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#14121C"))
        root.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }

        val title = TextView(this)
        title.text = if (total > 1) "Recorta tu foto ($index de $total)" else "Recorta tu foto"
        title.textSize = 22f
        title.setTextColor(Color.WHITE)
        title.setPadding(dp(20), dp(16), dp(20), dp(2))
        root.addView(title)

        val hint = TextView(this)
        hint.text = "Arrastra las esquinas o los lados para elegir qué parte se verá. Arrastra dentro del recuadro para moverlo." +
            (if (total > 1) " «Omitir» deja esta foto completa; con «Atrás» dejas el resto para después." else "")
        hint.textSize = 13f
        hint.setTextColor(Color.parseColor("#B9B3CC"))
        hint.setPadding(dp(20), 0, dp(20), dp(8))
        root.addView(hint)

        cropView = CropView(this)
        cropView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        cropView.setPhoto(bmp, item.l, item.t, item.r, item.b)
        root.addView(cropView)

        // Forma del recorte
        val shapes = listOf<Pair<String, Float?>>(
            Pair("Libre", null),
            Pair("Como el widget", widgetAspect()),
            Pair("1:1", 1f),
            Pair("16:9", 16f / 9f),
            Pair("9:16", 9f / 16f)
        )
        var selected = 0
        val shapeScroll = HorizontalScrollView(this)
        shapeScroll.isHorizontalScrollBarEnabled = false
        val shapeRow = LinearLayout(this)
        shapeRow.orientation = LinearLayout.HORIZONTAL
        shapeRow.setPadding(dp(16), dp(10), dp(16), dp(4))
        shapeScroll.addView(shapeRow)

        fun fillShapes() {
            shapeRow.removeAllViews()
            shapes.forEachIndexed { i, s ->
                val t = TextView(this)
                t.text = s.first
                t.textSize = 14f
                t.gravity = Gravity.CENTER
                t.setPadding(dp(14), dp(8), dp(14), dp(8))
                t.setTextColor(Color.WHITE)
                t.background = pill(if (i == selected) accent else chipBg, 20)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = dp(8)
                t.layoutParams = lp
                t.setOnClickListener {
                    selected = i
                    cropView.setAspect(s.second)
                    fillShapes()
                }
                shapeRow.addView(t)
            }
        }
        fillShapes()
        root.addView(shapeScroll)

        // Botones
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(16), dp(8), dp(16), dp(16))

        fun button(text: String, color: Int, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = text
            b.isAllCaps = false
            b.setTextColor(Color.WHITE)
            b.background = pill(color, 24)
            val lp = LinearLayout.LayoutParams(0, dp(48), 1f)
            lp.leftMargin = dp(4)
            lp.rightMargin = dp(4)
            b.layoutParams = lp
            b.setOnClickListener { onClick() }
            return b
        }
        bar.addView(button("Eliminar", Color.parseColor("#7A3B4A")) { confirmDelete() })
        if (total > 1) {
            // varias fotos seguidas: omitir pasa a la siguiente (Atrás las deja todas para después)
            bar.addView(button("Omitir", chipBg) {
                setResult(RESULT_FIRST_USER)
                finish()
            })
        } else {
            bar.addView(button("Cancelar", chipBg) { finish() })
        }
        bar.addView(button(if (index < total) "Siguiente" else "Listo", accent) { saveAndExit() })
        root.addView(bar)

        setContentView(root)
    }

    private fun widgetAspect(): Float {
        val o = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).let { if (it < 110) 250 else it }
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).let { if (it < 70) 130 else it }
        return wDp.toFloat() / hDp
    }

    private fun saveAndExit() {
        val r = cropView.cropRect()
        WidgetStore.updateCrop(this, widgetId, photoId, r.left, r.top, r.right, r.bottom)
        setResult(RESULT_OK)
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar foto")
            .setMessage("¿Quitar esta foto del widget?")
            .setPositiveButton("Eliminar") { _, _ ->
                WidgetStore.deletePhoto(this, widgetId, photoId)
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
