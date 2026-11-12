package app.upmile.preview3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.upmile.R
import app.upmile.core.preview3d.PropKind
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Sign faces and the hazard stripe, drawn with Android Canvas; the top row of each bitmap is v = 0 in PropKit. */
internal object Bitmaps {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF111111.toInt()
    private const val GREEN = 0xFF006747.toInt()
    private const val BLUE = 0xFF0B4EA2.toInt()
    private const val YELLOW = 0xFFFFC20E.toInt()
    private const val RED = 0xFFD52B1E.toInt()
    private const val PILLAR_GREEN = 0xFF0A7D3B.toInt()

    fun sign(context: Context, face: SignFace): Bitmap {
        val font = Typeface.create(ResourcesCompat.getFont(context, R.font.asta_sans), 700, false)
        val t = face.text
        return when (face.kind) {
            PropKind.SIGN_GREEN, PropKind.GANTRY_GREEN -> board(font, t, GREEN, headline = true)
            PropKind.SIGN_BLUE -> board(font, t, BLUE, headline = false)
            PropKind.WARNING_CURVE_LEFT -> draw(256, 256) { warningTriangle(); curveLeftArrow() }
            PropKind.WARNING_CURVE_RIGHT -> mirrored(sign(context, face.copy(kind = PropKind.WARNING_CURVE_LEFT)))
            PropKind.WARNING_ACCIDENT -> draw(256, 256) { warningTriangle(); line("!", 128f, 172f, 150f, 120f, paint(font, BLACK)) }
            PropKind.YIELD_SIGN -> draw(256, 256) {
                drawColor(RED)
                drawPath(inset(PointF(0f, 0f), PointF(256f, 0f), PointF(128f, 256f), 24f), fill(WHITE))
                line("양보", 128f, 82f, 56f, 120f, paint(font, BLACK))
            }
            PropKind.SPEED_LIMIT -> draw(256, 256) {
                drawColor(RED)
                drawCircle(128f, 128f, 128f - .12f * 256f, fill(WHITE))
                line(t.firstOrNull() ?: "", 128f, 128f, 112f, 150f, paint(font, BLACK))
            }
            PropKind.ROUNDABOUT_SIGN -> draw(256, 256) { drawColor(BLUE); roundaboutArrows() }
            PropKind.CHEVRON_LEFT -> draw(128, 192) {
                drawColor(YELLOW)
                val p = stroke(BLACK, 18f).apply { strokeJoin = Paint.Join.MITER }
                for (x in listOf(22f, 64f)) drawPath(Path().apply { moveTo(x + 42f, 44f); lineTo(x, 96f); lineTo(x + 42f, 148f) }, p)
            }
            PropKind.CHEVRON_RIGHT -> mirrored(sign(context, face.copy(kind = PropKind.CHEVRON_LEFT)))
            PropKind.PILLAR -> draw(128, 128) { drawColor(WHITE); line(t.firstOrNull() ?: "", 64f, 64f, 80f, 112f, paint(font, PILLAR_GREEN)) }
            PropKind.HEIGHT_BAR -> draw(512, 144) { drawColor(YELLOW); line(t.joinToString(" "), 256f, 72f, 92f, 480f, paint(font, BLACK)) }
            PropKind.ROAD_TEXT -> draw(256, 512) {
                // road letters are long in the driving direction: drawn 2.5x tall; the first line is the nearest (bottom)
                val p = paint(font, WHITE); val n = t.size.coerceAtLeast(1); val rowH = 512f / n
                t.forEachIndexed { i, s ->
                    val cy = 512f - rowH * (i + .5f)
                    save(); scale(1f, 2.5f, 128f, cy); line(s, 128f, cy, rowH / 2.5f * .8f, 240f, p); restore()
                }
            }
            else -> draw(512, 256) { drawColor(WHITE); lines(t, paint(font, BLACK), List(t.size) { .8f / t.size.coerceAtLeast(2) }) }
        }
    }

    private fun board(font: Typeface, text: List<String>, bg: Int, headline: Boolean) = draw(512, 256) {
        drawColor(bg)
        drawRect(RectF(11f, 11f, 501f, 245f), stroke(WHITE, 6f)) // inner edge 8 px in from the plate edge
        val n = text.size.coerceAtLeast(1)
        val raw = if (headline) List(n) { if (it == 0) .40f else .30f } else List(n) { minOf(.4f, .8f / n) }
        val k = minOf(1f, .8f / raw.sum())
        lines(text, paint(font, WHITE), raw.map { it * k })
    }

    /** [heights]: each line's text size as a fraction of the bitmap height; lines spread evenly, centred. */
    private fun Canvas.lines(text: List<String>, p: Paint, heights: List<Float>) {
        val hs = heights.map { it * height }
        val gap = (height - hs.sum()) / (text.size + 1)
        var y = gap
        text.forEachIndexed { i, s -> line(s, width / 2f, y + hs[i] / 2, hs[i], width - 48f, p); y += hs[i] + gap }
    }

    /** One line of text centred on (cx, cy), shrunk to fit [maxW]. */
    private fun Canvas.line(s: String, cx: Float, cy: Float, size: Float, maxW: Float, p: Paint) {
        p.textSize = size
        val w = p.measureText(s)
        if (w > maxW) p.textSize = size * maxW / w
        val fm = p.fontMetrics
        drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, p)
    }

    private fun Canvas.warningTriangle() {
        drawColor(RED)
        drawPath(inset(PointF(128f, 0f), PointF(256f, 256f), PointF(0f, 256f), 24f), fill(YELLOW))
    }

    /** Up from the bottom, then bending left into an arrowhead. */
    private fun Canvas.curveLeftArrow() {
        drawPath(Path().apply { moveTo(150f, 224f); lineTo(150f, 178f); quadTo(150f, 142f, 112f, 142f) }, stroke(BLACK, 20f))
        drawPath(Path().apply { moveTo(80f, 142f); lineTo(114f, 116f); lineTo(114f, 168f); close() }, fill(BLACK))
    }

    /** Three arcs with heads pointing counter-clockwise (as the sign is seen), the way traffic circles. */
    private fun Canvas.roundaboutArrows() {
        val r = 66f; val c = 128f
        fun polar(deg: Float, rad: Float) = PointF(c + rad * cos(Math.toRadians(deg.toDouble())).toFloat(), c + rad * sin(Math.toRadians(deg.toDouble())).toFloat())
        val arc = stroke(WHITE, 20f)
        for (i in 0 until 3) {
            val a0 = -90f + 120f * i
            drawArc(RectF(c - r, c - r, c + r, c + r), a0, 78f, false, arc) // canvas angles run clockwise; the head sits at a0
            val tip = polar(a0 - 30f, r); val b1 = polar(a0 + 2f, r - 24f); val b2 = polar(a0 + 2f, r + 24f)
            drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }, fill(WHITE))
        }
    }

    /** The triangle a-b-c shrunk about its incentre so every edge moves [d] px inward. */
    private fun inset(a: PointF, b: PointF, c: PointF, d: Float): Path {
        val la = hypot(b.x - c.x, b.y - c.y); val lb = hypot(a.x - c.x, a.y - c.y); val lc = hypot(a.x - b.x, a.y - b.y)
        val per = la + lb + lc
        val ix = (la * a.x + lb * b.x + lc * c.x) / per; val iy = (la * a.y + lb * b.y + lc * c.y) / per
        val area = kotlin.math.abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) / 2
        val k = (2 * area / per - d) / (2 * area / per)
        fun pt(p: PointF) = PointF(ix + k * (p.x - ix), iy + k * (p.y - iy))
        val (p, q, r) = listOf(pt(a), pt(b), pt(c))
        return Path().apply { moveTo(p.x, p.y); lineTo(q.x, q.y); lineTo(r.x, r.y); close() }
    }

    /** 45 degree yellow and black stripes, 32 px each; the 256 px tile repeats seamlessly. */
    fun hazard(): Bitmap {
        val px = IntArray(256 * 256) { i -> if (((i % 256) + i / 256) % 64 < 32) 0xFFF5C400.toInt() else 0xFF1A1A1A.toInt() }
        return Bitmap.createBitmap(px, 256, 256, Bitmap.Config.ARGB_8888)
    }

    private fun draw(w: Int, h: Int, block: Canvas.() -> Unit): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).block() }

    private fun mirrored(b: Bitmap): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { preScale(-1f, 1f) }, false)

    private fun paint(font: Typeface, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font; this.color = color; textAlign = Paint.Align.CENTER
        fontVariationSettings = "'wght' 700" // asta_sans is a variable font
    }
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }
}
