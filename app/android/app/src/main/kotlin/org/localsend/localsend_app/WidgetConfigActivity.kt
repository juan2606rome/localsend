package org.localsend.localsend_app

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min

class WidgetConfigActivity : Activity() {

    companion object {
        const val REQ_PHOTO = 4711
        const val REQ_FONT = 4712
        const val REQ_CROP = 4713
    }

    private enum class Page { MAIN, BACKGROUND, TEXT, BUTTON }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var style: WidgetStyle

    private var page = Page.MAIN
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var backArrow: TextView
    private var preview: ImageView? = null
    private lateinit var bottomButton: Button

    // Cola de fotos pendientes de recortar (cuando se eligen varias a la vez)
    private val cropQueue = ArrayList<Int>()
    private var cropTotal = 0
    private var cropDone = 0

    private val ink = Color.parseColor("#3B3355")
    private val soft = Color.parseColor("#7C7694")
    private val paper = Color.parseColor("#FBF8FF")
    private val chipBg = Color.parseColor("#EFE9FA")
    private val accent = Color.parseColor("#9C7BEA")

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
        if (style.fontIndex == FONT_CUSTOM && FontStore.userTypeface(this, style.customFontName) == null) {
            style.fontIndex = 0
        }
        buildShell()
        showPage(Page.MAIN)
    }

    override fun onDestroy() {
        WidgetRenderer.clearCache()
        super.onDestroy()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (page != Page.MAIN) {
            showPage(Page.MAIN)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("¿Salir sin guardar?")
            .setMessage("Los cambios que hiciste en el widget se perderán.")
            .setPositiveButton("Salir") { _, _ -> super.onBackPressed() }
            .setNegativeButton("Seguir editando", null)
            .show()
    }

    // ------------------------------------------------------------ utilidades de diseño

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radiusDp: Int, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor)
        return g
    }

    private fun label(text: String, sizeSp: Float = 14f, bold: Boolean = false, topDp: Int = 0, muted: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sizeSp
        t.setTextColor(if (muted) soft else ink)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, dp(topDp), 0, dp(4))
        return t
    }

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

    private fun hScroll(build: (LinearLayout) -> Unit): HorizontalScrollView {
        val s = HorizontalScrollView(this)
        s.isHorizontalScrollBarEnabled = false
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.HORIZONTAL
        inner.setPadding(0, dp(4), 0, dp(4))
        build(inner)
        s.addView(inner)
        return s
    }

    /** Fila horizontal de opciones: al tocar una se marca sin mover el scroll. */
    private fun <T> choiceRow(
        items: List<T>,
        selectedIdx: () -> Int,
        makeView: (T, Boolean) -> View,
        onPick: (Int, T) -> Unit
    ): HorizontalScrollView {
        return hScroll { inner ->
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
        }
    }

    private fun toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox {
        val c = CheckBox(this)
        c.text = text
        c.setTextColor(ink)
        c.buttonTintList = ColorStateList.valueOf(accent)
        c.isChecked = checked
        c.setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        return c
    }

    private fun colorRow(get: () -> Int, set: (Int) -> Unit, palette: IntArray = PASTEL_COLORS): View {
        val items = ArrayList<Int?>()
        palette.forEach { items.add(it) }
        items.add(null) // botón "color personalizado"
        return choiceRow(
            items,
            selectedIdx = {
                val i = palette.indexOf(get())
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
                        rebuildPage()
                    }
                }
            }
        )
    }

    // ------------------------------------------------------------ estructura de la pantalla

    @Suppress("DEPRECATION")
    private fun buildShell() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(paper)
        root.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }

        // Encabezado
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(14), dp(12), dp(20), dp(4))
        backArrow = TextView(this)
        backArrow.text = "‹"
        backArrow.textSize = 32f
        backArrow.setTextColor(ink)
        backArrow.setPadding(dp(8), 0, dp(14), dp(4))
        backArrow.setOnClickListener { showPage(Page.MAIN) }
        header.addView(backArrow)
        headerTitle = TextView(this)
        headerTitle.textSize = 22f
        headerTitle.setTextColor(ink)
        headerTitle.setTypeface(headerTitle.typeface, Typeface.BOLD)
        header.addView(headerTitle)
        root.addView(header)

        // Vista previa fija: siempre visible mientras se edita
        val box = LinearLayout(this)
        box.gravity = Gravity.CENTER
        box.setPadding(dp(10), dp(10), dp(10), dp(10))
        box.background = round(chipBg, 22)
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.leftMargin = dp(20)
        blp.rightMargin = dp(20)
        blp.topMargin = dp(6)
        box.layoutParams = blp
        val img = ImageView(this)
        img.adjustViewBounds = true
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        box.addView(img)
        preview = img
        root.addView(box)

        // Contenido que cambia según la página
        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20), dp(10), dp(20), dp(20))
        scroll.addView(content)
        root.addView(scroll)

        // Botón inferior siempre visible: "Guardar widget" en el menú y "Listo" dentro de cada sección
        val save = Button(this)
        bottomButton = save
        save.isAllCaps = false
        save.textSize = 17f
        save.setTextColor(Color.WHITE)
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
        slp.leftMargin = dp(20)
        slp.rightMargin = dp(20)
        slp.topMargin = dp(6)
        slp.bottomMargin = dp(14)
        save.layoutParams = slp
        root.addView(save)

        setContentView(root)
    }

    private fun showPage(p: Page, restoreY: Int = 0) {
        page = p
        content.removeAllViews()
        backArrow.visibility = if (p == Page.MAIN) View.GONE else View.VISIBLE
        headerTitle.text = when (p) {
            Page.MAIN -> "Personaliza tu widget"
            Page.BACKGROUND -> "Fondo"
            Page.TEXT -> "Texto"
            Page.BUTTON -> "Botón de ajustes"
        }
        when (p) {
            Page.MAIN -> buildMain()
            Page.BACKGROUND -> buildBackground()
            Page.TEXT -> buildText()
            Page.BUTTON -> buildButton()
        }
        if (p == Page.MAIN) {
            bottomButton.text = "Guardar widget"
            bottomButton.background = round(accent, 26)
            bottomButton.setOnClickListener { saveAndExit() }
        } else {
            bottomButton.text = "Listo"
            bottomButton.background = round(ink, 26)
            bottomButton.setOnClickListener { showPage(Page.MAIN) }
        }
        scroll.post { scroll.scrollTo(0, restoreY) }
        refreshPreview()
    }

    private fun rebuildPage() {
        showPage(page, scroll.scrollY)
    }

    // ------------------------------------------------------------ página principal

    private fun menuRow(title: String, summary: String, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), dp(16), dp(16), dp(16))
        row.background = round(chipBg, 18)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        row.layoutParams = lp

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        texts.addView(label(title, 17f, true))
        texts.addView(label(summary, 13f, muted = true))
        row.addView(texts)

        val arrow = TextView(this)
        arrow.text = "›"
        arrow.textSize = 28f
        arrow.setTextColor(soft)
        row.addView(arrow)

        row.setOnClickListener { onClick() }
        return row
    }

    private fun buildMain() {
        content.addView(menuRow("Fondo", backgroundSummary()) { showPage(Page.BACKGROUND) })
        content.addView(menuRow("Texto", textSummary()) { showPage(Page.TEXT) })
        content.addView(menuRow("Botón de ajustes", if (style.emoji.isEmpty()) "Oculto" else style.emoji) {
            showPage(Page.BUTTON)
        })
        content.addView(
            label("Cuando termines de configurar todo, toca «Guardar widget» (abajo).", 13f, topDp = 8, muted = true)
        )
    }

    private fun backgroundSummary(): String {
        val dailyText = if (style.daily) " · cambia " + everyText() else ""
        return when (style.mode) {
            MODE_SOLID -> "Color$dailyText"
            MODE_GRADIENT -> "Degradado$dailyText"
            else -> if (style.photos.isEmpty()) "Foto · sin fotos aún"
            else if (style.photos.size == 1) "Foto"
            else "${style.photos.size} fotos · " + (if (style.photoRandom) "aleatoria" else "en orden") +
                " · " + everyText()
        }
    }

    private fun everyText(): String {
        val idx = CHANGE_VALUES.indexOf(style.changeMinutes).coerceAtLeast(0)
        return if (idx == 0) "cada día" else "cada " + CHANGE_LABELS[idx]
    }

    private fun currentFontLabel(): String =
        if (style.fontIndex == FONT_CUSTOM) FontStore.label(style.customFontName)
        else FONTS[style.fontIndex.coerceIn(0, FONTS.size - 1)].label

    private fun textSummary(): String {
        val parts = ArrayList<String>()
        parts.add(currentFontLabel())
        if (!style.showTitle && !style.showPhrase) parts.add("sin texto")
        else if (!style.showPhrase) parts.add("sin frase")
        else if (!style.showTitle) parts.add("sin título")
        return parts.joinToString(" · ")
    }

    // ------------------------------------------------------------ página: fondo

    private fun buildBackground() {
        content.addView(label("Tipo de fondo", 13f, muted = true))
        content.addView(
            choiceRow(
                listOf("Color", "Degradado", "Foto"),
                selectedIdx = { style.mode },
                makeView = { name, sel -> chip(name, sel) },
                onPick = { i, _ ->
                    style.mode = i
                    rebuildPage()
                }
            )
        )
        if (style.mode == MODE_PHOTO) buildPhotoOptions() else buildColorOptions()
    }

    private fun buildColorOptions() {
        content.addView(toggle("Cambiar de color automáticamente", style.daily) {
            style.daily = it
            rebuildPage()
        })

        if (style.daily) {
            content.addView(
                choiceRow(
                    listOf("Automático (pastel)", "Mis colores"),
                    selectedIdx = { if (style.dailyAuto) 0 else 1 },
                    makeView = { name, sel -> chip(name, sel) },
                    onPick = { i, _ ->
                        style.dailyAuto = (i == 0)
                        rebuildPage()
                    }
                )
            )
            if (style.dailyAuto) {
                content.addView(label("Se turnan los 12 colores pastel.", 13f, topDp = 4, muted = true))
            } else {
                buildDailyColorsEditor()
            }
            buildIntervalRow()
        } else if (style.mode == MODE_SOLID) {
            content.addView(label("Color", 13f, topDp = 8, muted = true))
            content.addView(colorRow({ style.color1 }, { style.color1 = it }))
        } else {
            content.addView(label("Primer color", 13f, topDp = 8, muted = true))
            content.addView(colorRow({ style.color1 }, { style.color1 = it }))
            content.addView(label("Segundo color", 13f, topDp = 8, muted = true))
            content.addView(colorRow({ style.color2 }, { style.color2 = it }))
        }
    }

    /** Cada cuánto cambian los colores o las fotos. */
    private fun buildIntervalRow() {
        content.addView(label("Cambiar cada", 13f, topDp = 10, muted = true))
        content.addView(
            choiceRow(
                CHANGE_LABELS,
                selectedIdx = { CHANGE_VALUES.indexOf(style.changeMinutes).coerceAtLeast(0) },
                makeView = { name, sel -> chip(name, sel) },
                onPick = { i, _ ->
                    style.changeMinutes = CHANGE_VALUES[i]
                    rebuildPage()
                }
            )
        )
        content.addView(
            label(
                if (style.changeMinutes >= 1440) "Cambia una vez al día."
                else "Los intervalos cortos gastan un poco más de batería. El cambio ocurre mientras usas el celular, para no gastar de más.",
                12f, muted = true
            )
        )
    }

    private fun buildDailyColorsEditor() {
        val chosen = style.dailyColors
        content.addView(label("Tus colores, en orden (toca uno para quitarlo)", 13f, topDp = 8, muted = true))
        if (chosen.isEmpty()) {
            content.addView(label("Aún no elegiste ninguno. Mientras tanto se usa el modo automático.", 12f, muted = true))
        } else {
            content.addView(hScroll { inner ->
                chosen.forEachIndexed { index, color ->
                    val s = swatch(color, false, "${index + 1}")
                    s.setOnClickListener {
                        val list = style.dailyColors.toMutableList()
                        list.removeAt(index)
                        style.dailyColors = list
                        rebuildPage()
                    }
                    inner.addView(s)
                }
            })
            if (style.mode == MODE_GRADIENT && chosen.size == 1) {
                content.addView(label("Con un solo color el degradado usa una versión más clara. Agrega otro para combinarlos.", 12f, muted = true))
            }
        }

        content.addView(label("Toca un color para agregarlo", 13f, topDp = 8, muted = true))
        content.addView(hScroll { inner ->
            PASTEL_COLORS.forEach { color ->
                val s = swatch(color, false)
                s.setOnClickListener { addDailyColor(color) }
                inner.addView(s)
            }
            val custom = swatch(0, false, "+", rainbow = true)
            custom.setOnClickListener {
                openColorDialog(PASTEL_COLORS[6]) { picked -> addDailyColor(picked) }
            }
            inner.addView(custom)
        })
    }

    private fun addDailyColor(color: Int) {
        if (style.dailyColors.size >= 12) {
            Toast.makeText(this, "Máximo 12 colores", Toast.LENGTH_SHORT).show()
            return
        }
        style.dailyColors = style.dailyColors + color
        rebuildPage()
    }

    // ------------------------------------------------------------ fotos

    private fun buildPhotoOptions() {
        val photos = style.photos
        content.addView(label("Tus fotos (${photos.size} de $MAX_PHOTOS)", 15f, true, 14))

        content.addView(hScroll { inner ->
            photos.forEach { item ->
                val v = ImageView(this)
                v.scaleType = ImageView.ScaleType.CENTER_CROP
                v.background = round(chipBg, 14)
                v.outlineProvider = ViewOutlineProvider.BACKGROUND
                v.clipToOutline = true
                val thumb = decodeThumb(item)
                if (thumb != null) v.setImageBitmap(thumb)
                val lp = LinearLayout.LayoutParams(dp(76), dp(76))
                lp.rightMargin = dp(10)
                v.layoutParams = lp
                v.setOnClickListener {
                    cropQueue.clear()
                    openCrop(item.id, 1, 1)
                }
                inner.addView(v)
            }
            if (photos.size < MAX_PHOTOS) {
                val add = TextView(this)
                add.text = "+"
                add.textSize = 30f
                add.gravity = Gravity.CENTER
                add.setTextColor(Color.WHITE)
                add.background = round(accent, 14)
                val lp = LinearLayout.LayoutParams(dp(76), dp(76))
                lp.rightMargin = dp(10)
                add.layoutParams = lp
                add.setOnClickListener { pickPhotos() }
                inner.addView(add)
            }
        })

        if (photos.isEmpty()) {
            content.addView(label("Toca + para elegir una o varias fotos.", 13f, muted = true))
            return
        }
        content.addView(
            label(
                "Toca una foto para recortarla: eliges qué parte se verá. Con 2 o más fotos, el widget las va cambiando.",
                13f, muted = true
            )
        )

        if (photos.size >= 2) {
            content.addView(label("Orden de las fotos", 13f, topDp = 10, muted = true))
            content.addView(
                choiceRow(
                    listOf("En orden", "Aleatorio"),
                    selectedIdx = { if (style.photoRandom) 1 else 0 },
                    makeView = { name, sel -> chip(name, sel) },
                    onPick = { i, _ -> style.photoRandom = (i == 1) }
                )
            )
            buildIntervalRow()
        }

        content.addView(label("Si cambias el tamaño del widget", 13f, topDp = 10, muted = true))
        val fitNames = listOf("Llenar", "Estirar", "Barras")
        content.addView(
            choiceRow(
                fitNames,
                selectedIdx = { style.fitMode },
                makeView = { name, sel -> chip(name, sel) },
                onPick = { i, _ ->
                    style.fitMode = i
                    rebuildPage()
                }
            )
        )
        content.addView(
            label(
                when (style.fitMode) {
                    FIT_STRETCH -> "Estirar: el recorte se deforma para llenar todo el widget."
                    FIT_BARS -> "Barras: se ve todo tu recorte sin deformarlo y el resto se rellena con el color de abajo."
                    else -> "Llenar: se llena el widget usando solo lo que está dentro de tu recorte. Nunca se muestra el resto de la foto."
                },
                12f, muted = true
            )
        )
        if (style.fitMode == FIT_BARS) {
            content.addView(label("Color de las barras", 13f, topDp = 8, muted = true))
            content.addView(colorRow({ style.barColor }, { style.barColor = it }, BAR_COLORS))
        }

        content.addView(label("Oscurecer foto (ayuda a leer la letra)", 13f, topDp = 10, muted = true))
        val dimValues = listOf(0, 25, 45, 65)
        content.addView(
            choiceRow(
                listOf("Nada", "Poco", "Medio", "Mucho"),
                selectedIdx = {
                    var best = 0
                    for (k in dimValues.indices) {
                        if (Math.abs(dimValues[k] - style.dim) < Math.abs(dimValues[best] - style.dim)) best = k
                    }
                    best
                },
                makeView = { name, sel -> chip(name, sel) },
                onPick = { i, _ -> style.dim = dimValues[i] }
            )
        )
    }

    private fun decodeThumb(item: PhotoItem): Bitmap? {
        val f = WidgetStore.photoFile(this, widgetId, item.id)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 300) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(f.path, opts) ?: return null
        // la miniatura muestra solo la parte recortada
        val x = (item.l.coerceIn(0f, 1f) * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val y = (item.t.coerceIn(0f, 1f) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val w = ((item.r - item.l) * bmp.width).toInt().coerceIn(1, bmp.width - x)
        val h = ((item.b - item.t) * bmp.height).toInt().coerceIn(1, bmp.height - y)
        return try {
            Bitmap.createBitmap(bmp, x, y, w, h)
        } catch (e: Exception) {
            bmp
        }
    }

    /** Primero la galería de fotos (con sus álbumes); el explorador de archivos queda como segunda opción. */
    private fun pickPhotos() {
        val remaining = MAX_PHOTOS - style.photos.size
        if (remaining <= 0) {
            Toast.makeText(this, "Ya tienes el máximo de $MAX_PHOTOS fotos", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Elegir fotos de…")
            .setItems(arrayOf("Galería (fotos y álbumes)", "Explorador de archivos")) { _, which ->
                if (which == 0) pickFromGallery(remaining) else pickFromFiles()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickFromGallery(remaining: Int) {
        try {
            // Selector de fotos de Android: muestra recientes y álbumes, y permite elegir varias
            val i = Intent(MediaStore.ACTION_PICK_IMAGES)
            if (remaining > 1) {
                val limit = if (Build.VERSION.SDK_INT >= 33) MediaStore.getPickImagesMaxLimit() else 100
                i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, min(remaining, limit))
            }
            startActivityForResult(i, REQ_PHOTO)
        } catch (e: Exception) {
            try {
                // Celulares sin ese selector: se abre la app de galería
                val i = Intent(Intent.ACTION_PICK)
                i.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                startActivityForResult(i, REQ_PHOTO)
            } catch (e2: Exception) {
                pickFromFiles()
            }
        }
    }

    private fun pickFromFiles() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT)
        pick.addCategory(Intent.CATEGORY_OPENABLE)
        pick.type = "image/*"
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(pick, REQ_PHOTO)
    }

    private fun openCrop(photoId: Int, index: Int, total: Int) {
        val i = Intent(this, CropActivity::class.java)
        i.putExtra(CropActivity.EXTRA_WIDGET_ID, widgetId)
        i.putExtra(CropActivity.EXTRA_PHOTO_ID, photoId)
        i.putExtra(CropActivity.EXTRA_INDEX, index)
        i.putExtra(CropActivity.EXTRA_TOTAL, total)
        startActivityForResult(i, REQ_CROP)
    }

    /** Abre el recorte de la siguiente foto pendiente, o vuelve a la pantalla si ya no quedan. */
    private fun nextCrop() {
        if (cropQueue.isEmpty()) {
            rebuildPage()
            return
        }
        val id = cropQueue.removeAt(0)
        cropDone++
        openCrop(id, cropDone, cropTotal)
    }

    // ------------------------------------------------------------ página: texto

    private fun buildText() {
        content.addView(toggle("Mostrar el título «Enviar archivos»", style.showTitle) {
            style.showTitle = it
            refreshPreview()
        })
        content.addView(toggle("Mostrar la frase del día", style.showPhrase) {
            style.showPhrase = it
            refreshPreview()
        })
        content.addView(label("Si apagas las dos, el widget muestra solo el fondo.", 12f, muted = true))

        content.addView(label("Fuente", 13f, topDp = 14, muted = true))
        val dropdown = TextView(this)
        dropdown.text = currentFontLabel() + "   ▾"
        dropdown.textSize = 19f
        dropdown.setTextColor(ink)
        dropdown.typeface = FontStore.resolve(this, style)
        dropdown.setPadding(dp(16), dp(13), dp(16), dp(13))
        dropdown.background = round(chipBg, 14, accent, 1)
        dropdown.setOnClickListener { openFontDialog() }
        content.addView(dropdown)
        content.addView(
            label(
                "Con «Importar mi fuente» puedes agregar cualquier archivo .ttf u .otf. Quedan guardadas (★) para usarlas cuando quieras; mantén presionada una para borrarla.",
                12f, topDp = 4, muted = true
            )
        )

        content.addView(label("Color de la letra", 13f, topDp = 12, muted = true))
        content.addView(
            choiceRow(
                listOf("Automático", "Blanco", "Negro", "Otro color"),
                selectedIdx = { style.textMode },
                makeView = { name, sel -> chip(name, sel) },
                onPick = { i, _ ->
                    style.textMode = i
                    if (i == TEXT_CUSTOM) {
                        openColorDialog(style.customTextColor) { picked ->
                            style.customTextColor = picked
                            rebuildPage()
                        }
                    }
                }
            )
        )

        content.addView(toggle("Contorno en la letra (se lee sobre cualquier fondo)", style.outline) {
            style.outline = it
            refreshPreview()
        })
    }

    private fun openFontDialog() {
        val user = FontStore.userFonts(this)
        val labels = ArrayList<String>()
        FONTS.forEach { labels.add(it.label) }
        user.forEach { labels.add("★ " + FontStore.label(it)) }
        labels.add("Importar mi fuente…")

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.textSize = 18f
                v.setTextColor(ink)
                v.typeface = when {
                    position < FONTS.size -> FontStore.forIndex(context, position)
                    position < FONTS.size + user.size ->
                        FontStore.userTypeface(context, user[position - FONTS.size]) ?: Typeface.DEFAULT
                    else -> Typeface.DEFAULT
                }
                return v
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Elige una fuente")
            .setAdapter(adapter) { _, which ->
                if (which < FONTS.size) {
                    style.fontIndex = which
                    rebuildPage()
                } else if (which < FONTS.size + user.size) {
                    style.fontIndex = FONT_CUSTOM
                    style.customFontName = user[which - FONTS.size]
                    rebuildPage()
                } else {
                    pickFont()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.show()

        // Mantener presionada una fuente propia (★) para borrarla
        dialog.listView.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos >= FONTS.size && pos < FONTS.size + user.size) {
                val name = user[pos - FONTS.size]
                AlertDialog.Builder(this)
                    .setTitle("Borrar fuente")
                    .setMessage("¿Borrar «" + FontStore.label(name) + "» de tus fuentes importadas?")
                    .setPositiveButton("Borrar") { _, _ ->
                        FontStore.deleteUser(this, name)
                        if (style.fontIndex == FONT_CUSTOM && style.customFontName == name) style.fontIndex = 0
                        dialog.dismiss()
                        rebuildPage()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                true
            } else {
                false
            }
        }
    }

    private fun pickFont() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, REQ_FONT)
    }

    // ------------------------------------------------------------ página: botón de ajustes

    private fun buildButton() {
        content.addView(label("Este icono aparece en la esquina del widget. Al tocarlo vuelves a esta pantalla.", 13f, muted = true))
        val options = ArrayList<String>()
        options.addAll(EMOJIS)
        options.add("") // oculto
        content.addView(
            choiceRow(
                options,
                selectedIdx = { options.indexOf(style.emoji).coerceAtLeast(0) },
                makeView = { e, sel ->
                    if (e.isEmpty()) chip("Oculto", sel) else chip(e, sel, null, 22f)
                },
                onPick = { _, e -> style.emoji = e }
            )
        )
        content.addView(
            label(
                "Si lo ocultas, el botón sigue funcionando: toca la esquina superior derecha del widget. También puedes mantener presionado el widget y elegir configurar (Android 9 o superior).",
                12f, topDp = 6, muted = true
            )
        )
        content.addView(label("Título del widget", 13f, topDp = 16, muted = true))
        content.addView(toggle("Mostrar el título «Enviar archivos»", style.showTitle) {
            style.showTitle = it
            refreshPreview()
        })
    }

    // ------------------------------------------------------------ vista previa

    private fun refreshPreview() {
        val img = preview ?: return
        val o = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).let { if (it < 110) 250 else it }
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).let { if (it < 70) 130 else it }

        var pw = resources.displayMetrics.widthPixels - dp(60)
        var ph = pw * hDp / wDp
        val maxH = dp(150)
        if (ph > maxH) {
            ph = maxH
            pw = ph * wDp / hDp
        }
        pw = min(pw, 1100)
        img.setImageBitmap(
            WidgetRenderer.render(this, style, widgetId, pw, ph, WidgetStore.phraseFor(this, WidgetStore.today()))
        )
    }

    // ------------------------------------------------------------ selector de color propio

    private fun openColorDialog(initial: Int, onPick: (Int) -> Unit) {
        showColorPickerDialog(this, initial, accent, ink, soft, onPick)
    }

    // ------------------------------------------------------------ resultados y guardado

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CROP) {
            // el recorte o la eliminación ya quedaron guardados: se vuelven a leer las fotos
            style.photos = WidgetStore.loadPhotos(this, widgetId)
            if (resultCode == RESULT_CANCELED) {
                cropQueue.clear() // «Atrás»: el resto de fotos se recorta después, tocándolas
                rebuildPage()
            } else {
                nextCrop()
            }
            return
        }
        if (resultCode != RESULT_OK || data == null) return

        if (requestCode == REQ_PHOTO) {
            val uris = ArrayList<android.net.Uri>()
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            } else if (data.data != null) {
                uris.add(data.data!!)
            }

            val added = ArrayList<PhotoItem>()
            var failed = 0
            for (u in uris) {
                val item = WidgetStore.addPhoto(this, widgetId, u)
                if (item != null) added.add(item) else failed++
            }
            style.photos = WidgetStore.loadPhotos(this, widgetId)
            if (added.isNotEmpty()) style.mode = MODE_PHOTO
            if (failed > 0) {
                Toast.makeText(
                    this,
                    "No se pudieron agregar $failed foto(s). El máximo es $MAX_PHOTOS.",
                    Toast.LENGTH_LONG
                ).show()
            }
            // se abre el recorte de cada foto nueva, una tras otra
            cropQueue.clear()
            added.forEach { cropQueue.add(it.id) }
            cropTotal = added.size
            cropDone = 0
            nextCrop()
        } else if (requestCode == REQ_FONT) {
            val uri = data.data ?: return
            val name = FontStore.saveUser(this, uri)
            if (name != null) {
                style.fontIndex = FONT_CUSTOM
                style.customFontName = name
                rebuildPage()
            } else {
                Toast.makeText(this, "Ese archivo no es una fuente válida (usa .ttf u .otf)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAndExit() {
        WidgetStore.save(this, widgetId, style)
        SendWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
        WidgetScheduler.sync(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
