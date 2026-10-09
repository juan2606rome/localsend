package org.localsend.localsend_app

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WidgetConfigActivity : Activity() {

    companion object {
        const val CODE_CUSTOM = 1000
        const val CODE_IMPORT = 1001
        const val REQ_PHOTO = 4711
        const val REQ_FONT = 4712
    }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var style: WidgetStyle
    private var preview: ImageView? = null
    private var mainScroll: ScrollView? = null

    private val ink = Color.parseColor("#3B3355")
    private val paper = Color.parseColor("#FBF8FF")
    private val chipBg = Color.parseColor("#EFE9FA")
    private val accent = Color.parseColor("#9C7BEA")

    // Gestos sobre la foto (valores con decimales para que el movimiento sea suave)
    private var fxF = 50f
    private var fyF = 50f
    private var zoomF = 100f
    private var lastX = 0f
    private var lastY = 0f
    private var photoW = 0
    private var photoH = 0
    private var previewBmpW = 0
    private var previewBmpH = 0
    private lateinit var scaleDetector: ScaleGestureDetector

    private val dimValues = listOf(0, 25, 45, 65)
    private val dimLabels = listOf("Nada", "Poco", "Medio", "Mucho")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        style = WidgetStore.load(this, widgetId)
        if (style.fontIndex == FONT_CUSTOM && !FontStore.customFile(this, widgetId).exists()) {
            style.fontIndex = 0
        }
        fxF = style.focusX.toFloat()
        fyF = style.focusY.toFloat()
        zoomF = style.zoom.toFloat()
        loadPhotoSize()

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomF = (zoomF * detector.scaleFactor).coerceIn(100f, 300f)
                syncPhotoStyle()
                refreshPreview()
                return true
            }
        })
        buildUi(0)
    }

    override fun onDestroy() {
        WidgetRenderer.clearCache()
        super.onDestroy()
    }

    // ------------------------------------------------------------ utilidades

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun loadPhotoSize() {
        val size = WidgetStore.photoSize(this, widgetId)
        photoW = size?.first ?: 0
        photoH = size?.second ?: 0
    }

    private fun syncPhotoStyle() {
        style.focusX = fxF.roundToInt()
        style.focusY = fyF.roundToInt()
        style.zoom = zoomF.roundToInt()
    }

    private fun round(color: Int, radiusDp: Int, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor)
        return g
    }

    private fun label(text: String, sizeSp: Float = 14f, bold: Boolean = false, topDp: Int = 0): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sizeSp
        t.setTextColor(ink)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, dp(topDp), 0, dp(4))
        return t
    }

    private fun section(title: String): TextView = label(title, 17f, true, 22)

    private fun chip(text: String, selected: Boolean, face: Typeface? = null, sizeSp: Float = 14f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sizeSp
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(9), dp(14), dp(9))
        t.setTextColor(if (selected) Color.WHITE else ink)
        t.background = round(if (selected) accent else chipBg, 20)
        if (face != null) t.typeface = face
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = dp(8)
        t.layoutParams = lp
        return t
    }

    private fun swatch(color: Int, selected: Boolean, text: String = "", rainbow: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 18f
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.setTypeface(t.typeface, Typeface.BOLD)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        if (rainbow) {
            g.gradientType = GradientDrawable.SWEEP_GRADIENT
            g.colors = intArrayOf(
                PASTEL_COLORS[0], PASTEL_COLORS[2], PASTEL_COLORS[4],
                PASTEL_COLORS[5], PASTEL_COLORS[6], PASTEL_COLORS[8], PASTEL_COLORS[0]
            )
            g.setGradientCenter(0.5f, 0.5f)
        } else {
            g.setColor(color)
        }
        if (selected) g.setStroke(dp(3), ink) else g.setStroke(dp(1), Color.argb(70, 0, 0, 0))
        t.background = g
        val lp = LinearLayout.LayoutParams(dp(44), dp(44))
        lp.rightMargin = dp(10)
        t.layoutParams = lp
        return t
    }

    /** Fila horizontal de opciones: al tocar una se marca sin mover el scroll. */
    private fun <T> choiceRow(
        items: List<T>,
        selectedIdx: () -> Int,
        makeView: (T, Boolean) -> View,
        onPick: (Int, T) -> Unit
    ): HorizontalScrollView {
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.HORIZONTAL
        inner.setPadding(0, dp(4), 0, dp(4))
        scroll.addView(inner)

        fun fill() {
            inner.removeAllViews()
            items.forEachIndexed { i, item ->
                val v = makeView(item, i == selectedIdx())
                v.setOnClickListener {
                    onPick(i, item)
                    fill()
                    refreshPreview()
                }
                inner.addView(v)
            }
        }
        fill()
        return scroll
    }

    private fun colorRow(get: () -> Int, set: (Int) -> Unit): View {
        val items = ArrayList<Int?>()
        PASTEL_COLORS.forEach { items.add(it) }
        items.add(null) // botón "color personalizado"
        return choiceRow(
            items,
            selectedIdx = {
                val i = PASTEL_COLORS.indexOf(get())
                if (i >= 0) i else items.size - 1
            },
            makeView = { c, sel ->
                if (c != null) swatch(c, sel)
                else if (sel) swatch(get(), true, "✎")
                else swatch(0, false, "+", rainbow = true)
            },
            onPick = { _, c ->
                if (c != null) {
                    set(c)
                } else {
                    openColorDialog(get()) { picked ->
                        set(picked)
                        rebuildKeepingScroll()
                    }
                }
            }
        )
    }

    private fun dailyCheckbox(): CheckBox {
        val c = CheckBox(this)
        c.text = "🌈 Cambiar de color cada día"
        c.setTextColor(ink)
        c.isChecked = style.daily
        c.setOnCheckedChangeListener { _, checked ->
            style.daily = checked
            rebuildKeepingScroll()
        }
        return c
    }

    private fun rebuildKeepingScroll() {
        val y = mainScroll?.scrollY ?: 0
        buildUi(y)
    }

    // ------------------------------------------------------------ pantalla

    @Suppress("DEPRECATION")
    private fun buildUi(restoreScrollY: Int) {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(paper)
        scroll.isVerticalScrollBarEnabled = false

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(20), dp(20), dp(28))
        scroll.addView(col)
        scroll.setOnApplyWindowInsetsListener { _, ins ->
            col.setPadding(dp(20), dp(20) + ins.systemWindowInsetTop, dp(20), dp(28) + ins.systemWindowInsetBottom)
            ins
        }

        col.addView(label("Personaliza tu widget", 24f, true))
        col.addView(label("Así se verá en tu pantalla:", 13f))

        // Vista previa
        val box = previewBox()
        val img = ImageView(this)
        img.adjustViewBounds = true
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        box.addView(img)
        preview = img
        col.addView(box)
        if (style.mode == MODE_PHOTO && style.hasPhoto) attachPhotoGestures(img)

        // ---- Fondo
        col.addView(section("🎨 Fondo"))
        val modeNames = listOf("Color", "Degradado", "Mi foto")
        col.addView(
            choiceRow(
                modeNames,
                selectedIdx = { style.mode },
                makeView = { name, sel -> chip(name, sel) },
                onPick = { i, _ ->
                    style.mode = i
                    rebuildKeepingScroll()
                }
            )
        )

        when (style.mode) {
            MODE_SOLID, MODE_GRADIENT -> {
                col.addView(dailyCheckbox())
                if (style.daily) {
                    col.addView(label("Los colores pastel cambian solos cada día 🌈", 13f, topDp = 6))
                } else if (style.mode == MODE_SOLID) {
                    col.addView(label("Elige un color pastel o crea el tuyo (+)", 13f, topDp = 6))
                    col.addView(colorRow({ style.color1 }, { style.color1 = it }))
                } else {
                    col.addView(label("Primer color", 13f, topDp = 6))
                    col.addView(colorRow({ style.color1 }, { style.color1 = it }))
                    col.addView(label("Segundo color", 13f, topDp = 6))
                    col.addView(colorRow({ style.color2 }, { style.color2 = it }))
                }
            }
            MODE_PHOTO -> {
                val b = Button(this)
                b.text = if (style.hasPhoto) "📷 Cambiar foto" else "📷 Elegir foto"
                b.isAllCaps = false
                b.setTextColor(Color.WHITE)
                b.background = round(accent, 22)
                b.setOnClickListener {
                    val pick = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    pick.addCategory(Intent.CATEGORY_OPENABLE)
                    pick.type = "image/*"
                    startActivityForResult(pick, REQ_PHOTO)
                }
                col.addView(b)

                if (style.hasPhoto) {
                    col.addView(
                        label(
                            "👆 Arrastra la vista previa para mover la foto y pellizca con dos dedos para acercar o alejar.",
                            13f, topDp = 8
                        )
                    )
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPadding(0, dp(4), 0, dp(4))
                    val center = chip("🎯 Centrar y reiniciar zoom", false)
                    center.setOnClickListener {
                        fxF = 50f
                        fyF = 50f
                        zoomF = 100f
                        syncPhotoStyle()
                        refreshPreview()
                    }
                    row.addView(center)
                    col.addView(row)

                    col.addView(label("Oscurecer foto (ayuda a leer la letra)", 13f, topDp = 8))
                    col.addView(
                        choiceRow(
                            dimLabels,
                            selectedIdx = {
                                var best = 0
                                for (k in dimValues.indices) {
                                    if (abs(dimValues[k] - style.dim) < abs(dimValues[best] - style.dim)) best = k
                                }
                                best
                            },
                            makeView = { name, sel -> chip(name, sel) },
                            onPick = { i, _ -> style.dim = dimValues[i] }
                        )
                    )
                } else {
                    col.addView(label("Primero elige una foto.", 13f, topDp = 8))
                }
            }
        }

        // ---- Letra
        col.addView(section("🔤 Letra"))
        val codes = ArrayList<Int>()
        for (i in FONTS.indices) codes.add(i)
        if (style.customFontName.isNotEmpty() && FontStore.customFile(this, widgetId).exists()) codes.add(CODE_CUSTOM)
        codes.add(CODE_IMPORT)
        col.addView(
            choiceRow(
                codes,
                selectedIdx = { codes.indexOf(if (style.fontIndex == FONT_CUSTOM) CODE_CUSTOM else style.fontIndex) },
                makeView = { code, sel ->
                    if (code == CODE_IMPORT) {
                        chip("📁 Importar mi fuente", false)
                    } else if (code == CODE_CUSTOM) {
                        chip(
                            "★ " + style.customFontName, sel,
                            FontStore.resolve(this, widgetId, WidgetStyle(fontIndex = FONT_CUSTOM)), 16f
                        )
                    } else {
                        chip(FONTS[code].label, sel, FontStore.forIndex(this, code), 16f)
                    }
                },
                onPick = { _, code ->
                    if (code == CODE_IMPORT) {
                        pickFont()
                    } else if (code == CODE_CUSTOM) {
                        style.fontIndex = FONT_CUSTOM
                    } else {
                        style.fontIndex = code
                    }
                }
            )
        )
        col.addView(label("Con «Importar» puedes elegir cualquier fuente .ttf u .otf guardada en tu celular.", 12f, topDp = 2))

        col.addView(label("Color de la letra", 13f, topDp = 8))
        val textNames = listOf("Automático", "Blanco", "Negro", "Otro color")
        col.addView(
            choiceRow(
                textNames,
                selectedIdx = { style.textMode },
                makeView = { name, sel -> chip(name, sel) },
                onPick = { i, _ ->
                    style.textMode = i
                    if (i == TEXT_CUSTOM) {
                        openColorDialog(style.customTextColor) { picked ->
                            style.customTextColor = picked
                            rebuildKeepingScroll()
                        }
                    }
                }
            )
        )

        val outline = CheckBox(this)
        outline.text = "Contorno en la letra (se lee sobre cualquier fondo)"
        outline.setTextColor(ink)
        outline.isChecked = style.outline
        outline.setOnCheckedChangeListener { _, checked ->
            style.outline = checked
            refreshPreview()
        }
        col.addView(outline)

        val title = CheckBox(this)
        title.text = "Mostrar el texto «Enviar archivos»"
        title.setTextColor(ink)
        title.isChecked = style.showTitle
        title.setOnCheckedChangeListener { _, checked ->
            style.showTitle = checked
            refreshPreview()
        }
        col.addView(title)

        // ---- Emoji de ajustes
        col.addView(section("✨ Botón de ajustes del widget"))
        col.addView(label("Este emoji aparece en la esquina; tócalo para volver aquí.", 13f))
        col.addView(
            choiceRow(
                EMOJIS,
                selectedIdx = { EMOJIS.indexOf(style.emoji).coerceAtLeast(0) },
                makeView = { e, sel -> chip(e, sel, null, 22f) },
                onPick = { _, e -> style.emoji = e }
            )
        )

        // ---- Guardar
        val save = Button(this)
        save.text = "Guardar widget"
        save.isAllCaps = false
        save.textSize = 17f
        save.setTextColor(Color.WHITE)
        save.background = round(accent, 26)
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
        slp.topMargin = dp(26)
        save.layoutParams = slp
        save.setOnClickListener { saveAndExit() }
        col.addView(save)

        setContentView(scroll)
        mainScroll = scroll
        if (restoreScrollY > 0) scroll.post { scroll.scrollTo(0, restoreScrollY) }
        refreshPreview()
    }

    private fun previewBox(): LinearLayout {
        val b = LinearLayout(this)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(12), dp(12), dp(12), dp(12))
        b.background = round(chipBg, 22)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        b.layoutParams = lp
        return b
    }

    private fun refreshPreview() {
        val img = preview ?: return
        val o = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).let { if (it < 110) 250 else it }
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).let { if (it < 70) 130 else it }

        var pw = resources.displayMetrics.widthPixels - dp(64)
        var ph = pw * hDp / wDp
        val maxH = dp(240)
        if (ph > maxH) {
            ph = maxH
            pw = ph * wDp / hDp
        }
        pw = min(pw, 1100)
        previewBmpW = pw
        previewBmpH = ph
        val day = WidgetStore.today()
        img.setImageBitmap(
            WidgetRenderer.render(this, style, widgetId, pw, ph, WidgetStore.phraseFor(this, day), day)
        )
    }

    // ------------------------------------------------------------ mover y hacer zoom a la foto

    private fun attachPhotoGestures(img: ImageView) {
        img.setOnTouchListener { v, ev ->
            scaleDetector.onTouchEvent(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.x
                    lastY = ev.y
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    val idx = if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP && ev.actionIndex == 0) 1 else 0
                    lastX = ev.getX(idx)
                    lastY = ev.getY(idx)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress && ev.pointerCount == 1) {
                        panBy(ev.x - lastX, ev.y - lastY, v)
                    }
                    lastX = ev.x
                    lastY = ev.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
    }

    /** Mueve la foto con el dedo: la imagen sigue al dedo, igual que en cualquier galería. */
    private fun panBy(dx: Float, dy: Float, v: View) {
        val bw = previewBmpW
        val bh = previewBmpH
        if (photoW <= 0 || photoH <= 0 || bw <= 0 || bh <= 0 || v.width <= 0) return

        val k = bw.toFloat() / v.width // píxeles de pantalla -> píxeles de la imagen
        val zoom = zoomF / 100f
        val base = max(bw.toFloat() / photoW, bh.toFloat() / photoH)
        val overflowX = photoW * base * zoom - bw
        val overflowY = photoH * base * zoom - bh

        if (overflowX > 1f) fxF = (fxF - dx * k / overflowX * 100f).coerceIn(0f, 100f)
        if (overflowY > 1f) fyF = (fyF - dy * k / overflowY * 100f).coerceIn(0f, 100f)
        syncPhotoStyle()
        refreshPreview()
    }

    // ------------------------------------------------------------ selector de color propio

    private fun openColorDialog(initial: Int, onPick: (Int) -> Unit) {
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(16), dp(24), dp(8))

        val sample = View(this)
        sample.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        box.addView(sample)

        fun current(): Int = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2]))
        fun update() {
            sample.background = round(current(), 14, Color.argb(60, 0, 0, 0), 1)
        }

        fun addSlider(title: String, top: Int, start: Int, onChange: (Int) -> Unit) {
            box.addView(label(title, 13f, topDp = 10))
            val s = SeekBar(this)
            s.max = top
            s.progress = start
            s.progressTintList = ColorStateList.valueOf(accent)
            s.thumbTintList = ColorStateList.valueOf(accent)
            s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    onChange(p)
                    update()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            box.addView(s)
        }

        update()
        addSlider("Tono", 360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
        addSlider("Suavidad (pastel ← → intenso)", 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f }
        addSlider("Brillo", 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f }

        AlertDialog.Builder(this)
            .setTitle("Crea tu color")
            .setView(box)
            .setPositiveButton("Usar este color") { _, _ -> onPick(current()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------ fuente, foto y guardado

    private fun pickFont() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, REQ_FONT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        if (requestCode == REQ_PHOTO) {
            if (WidgetStore.savePhoto(this, widgetId, uri)) {
                style.hasPhoto = true
                style.mode = MODE_PHOTO
                fxF = 50f
                fyF = 50f
                zoomF = 100f
                syncPhotoStyle()
                loadPhotoSize()
                rebuildKeepingScroll()
            } else {
                Toast.makeText(this, "No pude abrir esa foto", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQ_FONT) {
            val name = FontStore.saveCustom(this, widgetId, uri)
            if (name != null) {
                style.fontIndex = FONT_CUSTOM
                style.customFontName = name
                rebuildKeepingScroll()
            } else {
                Toast.makeText(this, "Ese archivo no es una fuente válida (usa .ttf u .otf)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAndExit() {
        syncPhotoStyle()
        WidgetStore.save(this, widgetId, style)
        SendWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
