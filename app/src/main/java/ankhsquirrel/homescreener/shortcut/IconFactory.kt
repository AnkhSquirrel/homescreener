package ankhsquirrel.homescreener.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders full-bleed adaptive icon bitmaps (108dp layer, of which the launcher shows roughly the
 * central 72dp). Does file IO; call off the main thread.
 */
object IconFactory {

    private const val TAG = "IconFactory"
    private const val ADAPTIVE_SIZE_DP = 108
    private const val MAX_SIZE_PX = 432

    private const val PDF_PAGE_WIDTH = 0.6f
    private const val PDF_PAGE_TOP = 0.22f
    private const val PDF_BACKGROUND = 0xFFE8EAED.toInt()
    private const val PDF_SHADOW = 0x33000000

    fun create(context: Context, uri: Uri, mimeType: String, fileName: String): Bitmap {
        val size = (ADAPTIVE_SIZE_DP * context.resources.displayMetrics.density)
            .roundToInt().coerceAtMost(MAX_SIZE_PX)
        val kind = FileKind.of(mimeType, fileName)

        val rendered = if (kind == FileKind.PDF) attempt { pdfIcon(context, uri, size) } else null
        return rendered
            ?: attempt { thumbnailIcon(context, uri, mimeType, size) }
            ?: glyphIcon(kind, FileKind.badge(fileName, kind), size)
    }

    private inline fun attempt(block: () -> Bitmap?): Bitmap? = try {
        block()
    } catch (e: Exception) {
        Log.d(TAG, "Icon rendering failed, falling back", e)
        null
    }

    /** First page of the PDF, top-aligned like a sheet of paper peeking out of the icon. */
    private fun pdfIcon(context: Context, uri: Uri, size: Int): Bitmap? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount == 0) return null
            renderer.openPage(0).use { page ->
                val pageWidth = (size * PDF_PAGE_WIDTH).roundToInt()
                val scale = pageWidth.toFloat() / page.width
                val top = (size * PDF_PAGE_TOP).roundToInt()
                val visibleHeight = min((page.height * scale).roundToInt(), size - top).coerceAtLeast(1)

                val sheet = createBitmap(pageWidth, visibleHeight)
                sheet.eraseColor(Color.WHITE)
                page.render(
                    sheet, null, Matrix().apply { setScale(scale, scale) },
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                )

                val icon = createBitmap(size, size)
                val canvas = Canvas(icon)
                canvas.drawColor(PDF_BACKGROUND)
                val left = (size - pageWidth) / 2f
                val shadowOffset = size * 0.01f
                canvas.drawRect(
                    RectF(left + shadowOffset, top + shadowOffset, left + pageWidth + shadowOffset, (top + visibleHeight).toFloat() + shadowOffset),
                    Paint().apply { color = PDF_SHADOW },
                )
                canvas.drawBitmap(sheet, left, top.toFloat(), null)
                sheet.recycle()
                return icon
            }
        }
    }

    private fun thumbnailIcon(context: Context, uri: Uri, mimeType: String, size: Int): Bitmap? {
        val source = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            mimeType.startsWith("image/") -> decodeSampled(context, uri, size)
            else -> null
        } ?: return null
        return centerCrop(source, size)
    }

    private fun decodeSampled(context: Context, uri: Uri, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun centerCrop(source: Bitmap, size: Int): Bitmap {
        val side = min(source.width, source.height)
        val src = Rect(
            (source.width - side) / 2, (source.height - side) / 2,
            (source.width + side) / 2, (source.height + side) / 2,
        )
        val icon = createBitmap(size, size)
        Canvas(icon).drawBitmap(source, src, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        if (source != icon) source.recycle()
        return icon
    }

    private fun glyphIcon(kind: FileKind, badge: String, size: Int): Bitmap {
        val icon = createBitmap(size, size)
        val canvas = Canvas(icon)
        canvas.drawColor(kind.color)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.2f
        }
        val maxWidth = size * 0.5f
        val width = paint.measureText(badge)
        if (width > maxWidth) paint.textSize *= maxWidth / max(width, 1f)

        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(badge, size / 2f, baseline, paint)
        return icon
    }
}
