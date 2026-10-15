package org.localsend.localsend_app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Convierte "#RRGGBB", "RRGGBB" o "RGB" en un color. Devuelve null si el texto no es un color válido. */
fun parseHexColor(text: String): Int? {
    var t = text.trim().removePrefix("#")
    if (t.length == 3) t = "" + t[0] + t[0] + t[1] + t[1] + t[2] + t[2]
    if (t.length != 6) return null
    val v = t.toIntOrNull(16) ?: return null
    return Color.rgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
}

fun colorToHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

/** Cuadro grande: a la derecha el color es más intenso, arriba es más claro, abajo es más oscuro. */
class SatValView(context: Context) : View(context) {
    var hue = 0f
    var sat = 1f
    var value = 1f
    var onChange: ((Float, Float) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val hPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
        color = Color.WHITE
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * d
        color = Color.argb(90, 0, 0, 0)
    }
    private val rect = RectF()
    private val radius = 14f * d

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(c: Canvas) {
        val pure = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        hPaint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, radius, radius, hPaint)
        vPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, radius, radius, vPaint)

        val x = sat * width
        val y = (1f - value) * height
        c.drawCircle(x, y, 11f * d, shadow)
        c.drawCircle(x, y, 11f * d, ring)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (width <= 0 || height <= 0) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                sat = (e.x / width).coerceIn(0f, 1f)
                value = 1f - (e.y / height).coerceIn(0f, 1f)
                invalidate()
                onChange?.invoke(sat, value)
            }
        }
        return true
    }
}

/** Barra arcoíris para elegir el tono del color. */
class HueBarView(context: Context) : View(context) {
    var hue = 0f
    var onChange: ((Float) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
        color = Color.WHITE
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * d
        color = Color.argb(90, 0, 0, 0)
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val pad = 12f * d
        val barH = 14f * d
        val cy = height / 2f
        rect.set(pad, cy - barH / 2f, width - pad, cy + barH / 2f)

        val colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        barPaint.shader = LinearGradient(rect.left, 0f, rect.right, 0f, colors, null, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, barH / 2f, barH / 2f, barPaint)

        val x = rect.left + (hue / 360f) * rect.width()
        thumbFill.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        c.drawCircle(x, cy, 12f * d, thumbFill)
        c.drawCircle(x, cy, 12f * d, shadow)
        c.drawCircle(x, cy, 12f * d, ring())
    }

    private fun ring(): Paint = thumbRing

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pad = 12f * d
        val w = width - 2 * pad
        if (w <= 0f) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                hue = ((e.x - pad) / w).coerceIn(0f, 1f) * 360f
                invalidate()
                onChange?.invoke(hue)
            }
        }
        return true
    }
}

/**
 * Selector de color: cuadro de intensidad y brillo + barra de tono, con caja para escribir el código
 * (#RRGGBB) y botones para copiar y pegar colores.
 */
fun showColorPickerDialog(
    activity: Activity, initial: Int, accent: Int, ink: Int, soft: Int, onPick: (Int) -> Unit
) {
    val d = activity.resources.displayMetrics.density
    fun dp(v: Int): Int = (v * d).toInt()

    val hsv = FloatArray(3)
    Color.colorToHSV(initial, hsv)

    val root = LinearLayout(activity)
    root.orientation = LinearLayout.VERTICAL
    root.setPadding(dp(20), dp(12), dp(20), dp(4))

    val sv = SatValView(activity)
    sv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200))
    root.addView(sv)

    val hueBar = HueBarView(activity)
    val hlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
    hlp.topMargin = dp(8)
    hueBar.layoutParams = hlp
    root.addView(hueBar)

    // Fila: muestra del color + código
    val row = LinearLayout(activity)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    rlp.topMargin = dp(10)
    row.layoutParams = rlp

    val swatch = View(activity)
    swatch.layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
    row.addView(swatch)

    val hexBox = LinearLayout(activity)
    hexBox.orientation = LinearLayout.VERTICAL
    val hblp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    hblp.leftMargin = dp(14)
    hexBox.layoutParams = hblp

    val hexField = EditText(activity)
    hexField.setSingleLine(true)
    hexField.textSize = 20f
    hexField.setTextColor(ink)
    hexField.typeface = Typeface.MONOSPACE
    hexField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    hexField.filters = arrayOf(InputFilter.LengthFilter(7))
    hexField.hint = "#RRGGBB"
    hexBox.addView(hexField)

    val rgbLabel = TextView(activity)
    rgbLabel.textSize = 12f
    rgbLabel.setTextColor(soft)
    hexBox.addView(rgbLabel)
    row.addView(hexBox)
    root.addView(row)

    // Botones: copiar y pegar
    val actions = LinearLayout(activity)
    actions.orientation = LinearLayout.HORIZONTAL
    val alp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    alp.topMargin = dp(10)
    actions.layoutParams = alp

    fun smallButton(text: String): TextView {
        val t = TextView(activity)
        t.text = text
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.setTextColor(ink)
        t.setPadding(dp(16), dp(9), dp(16), dp(9))
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#EFE9FA"))
        g.cornerRadius = dp(20).toFloat()
        t.background = g
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = dp(8)
        t.layoutParams = lp
        return t
    }

    var updating = false
    fun current(): Int = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2]))

    fun refresh(keepText: Boolean) {
        val c = current()
        val g = GradientDrawable()
        g.setColor(c)
        g.cornerRadius = dp(14).toFloat()
        g.setStroke(dp(1), Color.argb(70, 0, 0, 0))
        swatch.background = g
        rgbLabel.text = "RGB  ${Color.red(c)}, ${Color.green(c)}, ${Color.blue(c)}"
        sv.hue = hsv[0]
        sv.sat = hsv[1]
        sv.value = hsv[2]
        sv.invalidate()
        hueBar.hue = hsv[0]
        hueBar.invalidate()
        if (!keepText) {
            updating = true
            hexField.setText(colorToHex(c))
            updating = false
        }
    }

    fun copyColor() {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("color", colorToHex(current())))
        Toast.makeText(activity, "Color copiado: " + colorToHex(current()), Toast.LENGTH_SHORT).show()
    }

    fun applyColor(c: Int) {
        Color.colorToHSV(c, hsv)
        refresh(false)
    }

    val copy = smallButton("Copiar")
    copy.setOnClickListener { copyColor() }
    swatch.setOnClickListener { copyColor() } // tocar la muestra también copia el color
    val paste = smallButton("Pegar")
    paste.setOnClickListener {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        val c = parseHexColor(text)
        if (c != null) {
            applyColor(c)
        } else {
            Toast.makeText(activity, "El portapapeles no tiene un color como #RRGGBB", Toast.LENGTH_SHORT).show()
        }
    }
    actions.addView(copy)
    actions.addView(paste)
    root.addView(actions)

    sv.onChange = { s, v ->
        hsv[1] = s
        hsv[2] = v
        refresh(false)
    }
    hueBar.onChange = { h ->
        hsv[0] = h
        refresh(false)
    }
    hexField.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(e: Editable?) {
            if (updating) return
            val c = parseHexColor(e?.toString() ?: "") ?: return
            Color.colorToHSV(c, hsv)
            refresh(true)
        }
    })

    refresh(false)

    val scroll = ScrollView(activity)
    scroll.addView(root)

    val dialog = AlertDialog.Builder(activity)
        .setTitle("Elige un color")
        .setView(scroll)
        .setPositiveButton("Usar este color") { _, _ -> onPick(current()) }
        .setNegativeButton("Cancelar", null)
        .create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    dialog.show()
}
