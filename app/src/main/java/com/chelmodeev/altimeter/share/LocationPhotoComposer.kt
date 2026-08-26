package com.chelmodeev.altimeter.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class LocationPhotoStamp(
    val altitude: String,
    val pressure: String,
    val coordinates: String,
    val localTime: String,
)

/** Creates a share-only JPEG copy. The source camera/gallery image is never modified. */
object LocationPhotoComposer {
    fun compose(context: Context, source: Uri, stamp: LocationPhotoStamp): Uri {
        val sourceBitmap = decode(context, source)
        val bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("Unable to prepare photo")
        if (bitmap !== sourceBitmap) sourceBitmap.recycle()
        drawStamp(bitmap, stamp)

        val directory = File(context.cacheDir, "location-photos").apply { mkdirs() }
        val output = File.createTempFile("altimeter-location-", ".jpg", directory)
        output.outputStream().buffered().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream))
        }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", output)
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_EDGE) {
                    val scale = MAX_IMAGE_EDGE.toFloat() / longest.toFloat()
                    decoder.setTargetSize(
                        max(1, (info.size.width * scale).toInt()),
                        max(1, (info.size.height * scale).toInt()),
                    )
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Unable to read photo")
    }

    private fun drawStamp(bitmap: Bitmap, stamp: LocationPhotoStamp) {
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val margin = (min(width, height) * 0.035f).coerceAtLeast(24f)
        val titleSize = (width * 0.030f).coerceIn(26f, 54f)
        val altitudeSize = (width * 0.060f).coerceIn(46f, 96f)
        val detailSize = (width * 0.027f).coerceIn(24f, 48f)
        val lineGap = detailSize * 0.55f
        val panelHeight = titleSize + altitudeSize + detailSize * 3 + lineGap * 5 + margin
        val panel = RectF(margin, height - panelHeight - margin, width - margin, height - margin)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(205, 8, 13, 22) }
        canvas.drawRoundRect(panel, margin * 0.55f, margin * 0.55f, background)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(239, 190, 92)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var baseline = panel.top + margin + titleSize
        white.textSize = titleSize
        white.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ALTIMETER KAILAS", panel.left + margin, baseline, white)

        baseline += lineGap + altitudeSize
        gold.textSize = altitudeSize
        canvas.drawText(stamp.altitude, panel.left + margin, baseline, gold)

        white.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        white.textSize = detailSize
        baseline += lineGap + detailSize
        canvas.drawText(stamp.pressure, panel.left + margin, baseline, white)
        baseline += lineGap + detailSize
        canvas.drawText(stamp.coordinates, panel.left + margin, baseline, white)
        baseline += lineGap + detailSize
        canvas.drawText(stamp.localTime, panel.left + margin, baseline, white)

        val signature = "Errarium™ by Aleksey Hermes"
        white.textSize = detailSize * 0.72f
        white.color = Color.argb(205, 255, 255, 255)
        canvas.drawText(
            signature,
            panel.right - margin - white.measureText(signature),
            panel.bottom - margin * 0.55f,
            white,
        )
    }

    private const val MAX_IMAGE_EDGE = 2_560
}
