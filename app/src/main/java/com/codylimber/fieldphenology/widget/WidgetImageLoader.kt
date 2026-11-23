package com.codylimber.fieldphenology.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import java.io.File

object WidgetImageLoader {

    /**
     * Load a species photo as a bitmap, sized for the widget.
     * Returns null if no photo is available.
     */
    fun loadSpeciesPhoto(
        context: Context,
        repository: PhenologyRepository,
        species: Species,
        datasetKey: String,
        maxWidth: Int = 400,
        maxHeight: Int = 300
    ): Bitmap? {
        val photo = species.photos.firstOrNull() ?: return null
        return try {
            val uri = repository.getPhotoUri(datasetKey, photo.file)
            val bitmap = if (uri.startsWith("file:///android_asset/")) {
                val assetPath = uri.removePrefix("file:///android_asset/")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
                }
                context.assets.open(assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } else {
                val file = File(java.net.URI(uri))
                if (file.exists()) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
                    }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                } else null
            }

            bitmap?.let { scaleBitmap(it, maxWidth, maxHeight) }
        } catch (e: Exception) {
            Log.e("WidgetImageLoader", "Failed to load photo for ${species.commonName}", e)
            null
        }
    }

    /**
     * Load and crop a species photo into a circle for list-mode thumbnails.
     */
    fun loadCircularThumbnail(
        context: Context,
        repository: PhenologyRepository,
        species: Species,
        datasetKey: String,
        size: Int = 64
    ): Bitmap? {
        val photo = loadSpeciesPhoto(context, repository, species, datasetKey, size, size)
            ?: return null
        return cropCircle(photo, size)
    }

    /** Largest power-of-two sample size that keeps the decoded image at least the target size. */
    private fun sampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        if (srcW <= 0 || srcH <= 0) return 1
        var sample = 1
        while (srcW / (sample * 2) >= reqW && srcH / (sample * 2) >= reqH) sample *= 2
        return sample
    }

    private fun scaleBitmap(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(
            maxWidth.toFloat() / source.width,
            maxHeight.toFloat() / source.height
        )
        if (ratio >= 1f) return source

        val newWidth = (source.width * ratio).toInt()
        val newHeight = (source.height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }

    private fun cropCircle(source: Bitmap, size: Int): Bitmap {
        // Center-crop to a square instead of stretching non-square images
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(
            source, (source.width - side) / 2, (source.height - side) / 2, side, side
        )
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)

        return output
    }
}
