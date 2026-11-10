package pl.dakil.dakilio.feature.export.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.graphics.shapes.toPath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.color.TagColor
import pl.dakil.dakilio.core.ui.color.deriveAccentColors
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.mood.moodGlyph
import pl.dakil.dakilio.core.designsystem.R as DesignR

/**
 * The journal as a PDF, on the framework's `PdfDocument`: A4 pages, a heading per day and each entry
 * with its mood face, time, title, note, tags and (optionally) photos. Notes split across pages line
 * by line; photos are decoded small (about [PHOTO_PIXELS] on the long edge) so the file stays small.
 */
class PdfJournalWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Writes to [out] and closes it. [onProgress] gets photos drawn so far and their total. */
    fun write(out: OutputStream, entries: List<Post>, labels: ExportLabels, withPhotos: Boolean, onProgress: (done: Int, total: Int) -> Unit) {
        val total = if (withPhotos) entries.sumOf { post -> post.photos.count { File(it.path).isFile } } else 0
        val document = PdfDocument()
        try {
            val pager = Pager(document)
            var done = 0
            pager.header(labels.title, labels.subtitle)
            val showDiary = entries.map { it.diaryId }.distinct().size > 1
            var day: LocalDate? = null
            entries.forEach { post ->
                if (post.date != day) {
                    day = post.date
                    pager.day(labels.day(post.date))
                }
                pager.entry(post, labels, showDiary)
                if (withPhotos) {
                    post.photos.forEach { photo ->
                        val file = File(photo.path)
                        if (file.isFile) {
                            pager.photo(file)
                            onProgress(++done, total)
                        }
                    }
                }
                pager.gap(ENTRY_GAP)
            }
            pager.finish()
            out.buffered().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private val typeface: Typeface = runCatching { ResourcesCompat.getFont(context, DesignR.font.google_sans_flex) }.getOrNull() ?: Typeface.DEFAULT

    private fun textPaint(size: Float, color: Int, weight: Int = 400, rounded: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@PdfJournalWriter.typeface
        textSize = size
        this.color = color
        // A variable font: weight and roundness are axes (the app's headings use the rounded ROND axis).
        setFontVariationSettings("'wght' $weight, 'ROND' ${if (rounded) 100 else 0}")
    }

    /** Lays content out top to bottom, starting a new page whenever something doesn't fit. */
    private inner class Pager(private val document: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var number = 0
        private var y = 0f

        private val canvas: Canvas get() = (page ?: newPage()).canvas
        private val bottom = PAGE_HEIGHT - MARGIN - FOOTER

        private val titlePaint = textPaint(26f, TEXT, weight = 600, rounded = true)
        private val subtitlePaint = textPaint(11f, SECONDARY)
        private val dayPaint = textPaint(14f, TEXT, weight = 600, rounded = true)
        private val metaPaint = textPaint(10f, SECONDARY, weight = 500)
        // Between the note and the day heading, so the two headings never look alike.
        private val entryTitlePaint = textPaint(11.5f, TEXT, weight = 600)
        private val notePaint = textPaint(10.5f, TEXT)
        private val tagPaint = textPaint(8.5f, TEXT, weight = 500)
        private val footerPaint = textPaint(8.5f, SECONDARY)
        private val linePaint = Paint().apply {
            color = DIVIDER
            strokeWidth = 0.6f
        }

        private fun newPage(): PdfDocument.Page {
            page?.let(::finishPage)
            number++
            val created = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())
            page = created
            y = MARGIN.toFloat()
            return created
        }

        private fun finishPage(done: PdfDocument.Page) {
            val text = number.toString()
            done.canvas.drawText(text, (PAGE_WIDTH - footerPaint.measureText(text)) / 2, PAGE_HEIGHT - MARGIN / 2f, footerPaint)
            document.finishPage(done)
        }

        /** Moves to a new page unless [height] still fits on this one. */
        private fun ensure(height: Float) {
            if (page == null || y + height > bottom) newPage()
        }

        fun finish() {
            if (page == null) newPage()
            page?.let(::finishPage)
            page = null
        }

        fun gap(points: Float) {
            y += points
        }

        fun header(title: String, subtitle: String) {
            val titleLayout = layout(title, titlePaint)
            val subtitleLayout = layout(subtitle, subtitlePaint)
            ensure(titleLayout.height + subtitleLayout.height + 24f)
            draw(titleLayout)
            y += 4f
            draw(subtitleLayout)
            y += 18f
            canvas.drawLine(MARGIN.toFloat(), y, PAGE_WIDTH - MARGIN.toFloat(), y, linePaint)
            y += 10f
        }

        fun day(text: String) {
            val layout = layout(text, dayPaint)
            // A day heading never sits alone at the bottom of a page.
            ensure(layout.height + 18f + KEEP_WITH_NEXT)
            y += 14f
            draw(layout)
            y += 8f
        }

        fun entry(post: Post, labels: ExportLabels, showDiary: Boolean) {
            val meta = buildList {
                add(labels.time(post.dateTime.toLocalTime()))
                post.mood?.let { add(it.label) }
                if (showDiary) labels.diaryName(post.diaryId)?.let(::add)
            }.joinToString("  ·  ")
            val metaLayout = layout(meta, metaPaint, CONTENT_WIDTH - BADGE - 8f)
            ensure(maxOf(BADGE, metaLayout.height.toFloat()) + 8f)
            val rowTop = y
            post.mood?.let { drawMood(it, MARGIN.toFloat(), rowTop) }
            val textX = if (post.mood != null) MARGIN + BADGE + 8f else MARGIN.toFloat()
            y = rowTop + (BADGE - metaLayout.height) / 2f
            draw(metaLayout, textX)
            y = rowTop + maxOf(BADGE, metaLayout.height.toFloat()) + 6f

            post.title?.takeIf { it.isNotBlank() }?.let {
                flow(layout(it.trim(), entryTitlePaint))
                y += 3f
            }
            post.note?.takeIf { it.isNotBlank() }?.let {
                flow(layout(it.trim(), notePaint, spacing = 1.3f))
                y += 4f
            }
            if (post.tags.isNotEmpty()) tags(post.tags)
        }

        /** Tags as soft pills in their own colors, wrapping onto new rows. */
        private fun tags(tags: List<Tag>) {
            val height = tagPaint.textSize + 8f
            var x = MARGIN.toFloat()
            y += 2f
            ensure(height)
            tags.forEach { tag ->
                val width = tagPaint.measureText(tag.label) + 14f
                if (x + width > PAGE_WIDTH - MARGIN && x > MARGIN) {
                    x = MARGIN.toFloat()
                    y += height + 4f
                    ensure(height)
                }
                val colors = TagColor.fromKey(tag.color)?.let { deriveAccentColors(it.seed, dark = false) }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors?.soft?.toArgb() ?: TAG_FILL }
                val rect = RectF(x, y, x + width, y + height)
                canvas.drawRoundRect(rect, height / 2, height / 2, fill)
                tagPaint.color = colors?.accent?.toArgb() ?: TEXT
                canvas.drawText(tag.label, x + 7f, y + height / 2 - (tagPaint.ascent() + tagPaint.descent()) / 2, tagPaint)
                x += width + 4f
            }
            y += height + 4f
        }

        fun photo(file: File) {
            val bitmap = decode(file) ?: return
            try {
                val (w, h) = photoBox(bitmap.width, bitmap.height, CONTENT_WIDTH, PHOTO_MAX_HEIGHT, maxScale = 0.75f)
                y += 4f
                ensure(h)
                val left = MARGIN + (CONTENT_WIDTH - w) / 2
                canvas.drawBitmap(bitmap, null, RectF(left, y, left + w, y + h), PHOTO_PAINT)
                y += h + 6f
            } finally {
                bitmap.recycle()
            }
        }

        private fun decode(file: File): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), PHOTO_PIXELS) }
            return BitmapFactory.decodeFile(file.path, options)
        }

        /** The level's face, as in the app: its shape in the mood's color with eyes and a mouth. */
        private fun drawMood(mood: Mood, x: Float, top: Float) {
            val colors = moodColors(mood, dark = false)
            val glyph = moodGlyph(mood)
            val path = glyph.rest.toPath(Path()).apply { transform(Matrix().apply { setScale(BADGE, BADGE); postTranslate(x, top) }) }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.container.toArgb() })
            val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.onContainer.toArgb() }
            val eyeY = top + BADGE * 0.42f
            canvas.drawCircle(x + BADGE * 0.37f, eyeY, BADGE * 0.055f, face)
            canvas.drawCircle(x + BADGE * 0.63f, eyeY, BADGE * 0.055f, face)
            val mouthY = top + BADGE * 0.62f
            val half = BADGE * 0.15f
            val mouth = Path().apply {
                moveTo(x + BADGE / 2 - half, mouthY - glyph.smile * BADGE * 0.02f)
                quadTo(x + BADGE / 2, mouthY + glyph.smile * BADGE * 0.15f, x + BADGE / 2 + half, mouthY - glyph.smile * BADGE * 0.02f)
            }
            face.style = Paint.Style.STROKE
            face.strokeWidth = BADGE * 0.068f
            face.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(mouth, face)
        }

        private fun layout(text: CharSequence, paint: TextPaint, width: Float = CONTENT_WIDTH, spacing: Float = 1.15f): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, spacing)
                .setIncludePad(false)
                .build()

        /** Draws a layout that fits whole, at the current position. */
        private fun draw(layout: StaticLayout, x: Float = MARGIN.toFloat()) {
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
        }

        /** Draws a layout that may run onto further pages, splitting it between lines. */
        private fun flow(layout: StaticLayout) {
            val bottoms = FloatArray(layout.lineCount) { layout.getLineBottom(it).toFloat() }
            var start = 0
            while (start < layout.lineCount) {
                ensure(bottoms[start] - (if (start == 0) 0f else bottoms[start - 1]))
                val fit = linesThatFit(bottoms, start, bottom - y, force = y == MARGIN.toFloat())
                if (fit == 0) {
                    newPage()
                    continue
                }
                val top = layout.getLineTop(start).toFloat()
                val end = layout.getLineBottom(start + fit - 1).toFloat()
                canvas.save()
                canvas.translate(MARGIN.toFloat(), y - top)
                canvas.clipRect(0f, top, CONTENT_WIDTH, end)
                layout.draw(canvas)
                canvas.restore()
                y += end - top
                start += fit
            }
        }
    }

    private companion object {
        // A4 in PostScript points.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48
        const val FOOTER = 14
        const val CONTENT_WIDTH = (PAGE_WIDTH - 2 * MARGIN).toFloat()
        const val BADGE = 22f
        const val ENTRY_GAP = 14f
        const val KEEP_WITH_NEXT = 60f
        const val PHOTO_MAX_HEIGHT = 300f
        const val PHOTO_PIXELS = 900

        val TEXT = Color(0xFF1D1B20).toArgb()
        val SECONDARY = Color(0xFF5F5B66).toArgb()
        val DIVIDER = Color(0xFFDDD9E2).toArgb()
        val TAG_FILL = Color(0xFFECE8F1).toArgb()
        val PHOTO_PAINT = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    }
}
