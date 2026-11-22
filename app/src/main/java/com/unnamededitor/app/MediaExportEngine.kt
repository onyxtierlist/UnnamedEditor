package com.unnamededitor.app

import android.content.Context
import android.os.Environment
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.ContentValues
import android.provider.MediaStore
import androidx.media3.transformer.ProgressHolder
import androidx.media3.common.MediaItem
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * Real single-source export pipeline. Timeline trim is applied to the imported source.
 * The output is an H.264 MP4 written into the app's Movies folder.
 */
object MediaExportEngine {
    @OptIn(androidx.media3.common.util.UnstableApi::class)
    fun export(
        context: Context,
        source: android.net.Uri,
        startMs: Long,
        endMs: Long?,
        resolution: String,
        quality: String,
        activeEffects: Map<String, Float>,
        onProgress: (Int) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "UnnamedEditor")
        if (!folder.exists() && !folder.mkdirs()) {
            onError("Couldn't create the export folder.")
            return
        }
        val output = File(folder, "Edit_${System.currentTimeMillis()}.mp4")
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs.coerceAtLeast(0L))
            .apply { if (endMs != null && endMs > startMs) setEndPositionMs(endMs) }
            .build()
        val item = MediaItem.Builder().setUri(source).setClippingConfiguration(clipping).build()
        val outputShortSide = when (resolution) { "720p" -> 720; "1440p" -> 1440; "4K" -> 2160; else -> 1080 }
        val baseBitrate = when (outputShortSide) { 720 -> 4_000_000; 1440 -> 12_000_000; 2160 -> 24_000_000; else -> 8_000_000 }
        val qualityMultiplier = when (quality) { "Standard" -> 0.65f; "Maximum" -> 1.35f; else -> 1f }
        val videoSettings = VideoEncoderSettings.Builder().setBitrate((baseBitrate * qualityMultiplier).toInt()).build()
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(videoSettings)
            .build()
        val videoEffects = buildList {
            val brightness = activeEffects["Brightness"] ?: 0f
            if (kotlin.math.abs(brightness) > 0.001f) add(Brightness(brightness.coerceIn(-1f, 1f)))

            val contrast = activeEffects["Contrast"] ?: 0f
            if (kotlin.math.abs(contrast) > 0.001f) add(Contrast(contrast.coerceIn(-1f, 1f)))

            val saturation = activeEffects["Saturation"] ?: 0f
            val hue = activeEffects["Hue"] ?: 0f
            val lightness = activeEffects["Lightness"] ?: 0f
            if (kotlin.math.abs(saturation) > 0.001f || kotlin.math.abs(hue) > 0.001f || kotlin.math.abs(lightness) > 0.001f) {
                add(HslAdjustment.Builder()
                    .adjustHue(hue.coerceIn(-180f, 180f))
                    .adjustSaturation(saturation.coerceIn(-100f, 100f))
                    .adjustLightness(lightness.coerceIn(-100f, 100f))
                    .build())
            }

            val blur = activeEffects["Gaussian Blur"] ?: 0f
            if (blur > 0.05f) add(GaussianBlur(blur.coerceIn(0.1f, 18f)))

            val red = activeEffects["Red Channel"] ?: 1f
            val green = activeEffects["Green Channel"] ?: 1f
            val blue = activeEffects["Blue Channel"] ?: 1f
            if (kotlin.math.abs(red - 1f) > 0.001f || kotlin.math.abs(green - 1f) > 0.001f || kotlin.math.abs(blue - 1f) > 0.001f) {
                add(RgbAdjustment.Builder()
                    .setRedScale(red.coerceIn(0f, 2f))
                    .setGreenScale(green.coerceIn(0f, 2f))
                    .setBlueScale(blue.coerceIn(0f, 2f))
                    .build())
            }

            val zoom = activeEffects["Zoom"] ?: 1f
            val rotation = activeEffects["Rotate"] ?: 0f
            if (kotlin.math.abs(zoom - 1f) > 0.001f || kotlin.math.abs(rotation) > 0.001f) {
                add(ScaleAndRotateTransformation.Builder()
                    .setScale(zoom.coerceIn(0.5f, 2f), zoom.coerceIn(0.5f, 2f))
                    .setRotationDegrees(rotation.coerceIn(-180f, 180f))
                    .build())
            }
            add(Presentation.createForHeight(outputShortSide))
        }
        val effects = Effects(emptyList(), videoEffects)
        val edited = EditedMediaItem.Builder(item).setEffects(effects).build()
        val handler = Handler(Looper.getMainLooper())
        lateinit var transformer: Transformer
        var finished = false
        val progressHolder = ProgressHolder()
        val progressPoll = object : Runnable {
            override fun run() {
                if (finished) return
                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(1, 99))
                }
                handler.postDelayed(this, 250L)
            }
        }
        transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
            .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    finished = true
                    handler.removeCallbacks(progressPoll)
                    Thread {
                        try {
                            val destination = publishToGallery(context, output)
                            output.delete()
                            handler.post {
                                onProgress(100)
                                onComplete(destination)
                            }
                        } catch (error: Exception) {
                            output.delete()
                            handler.post {
                                onError(error.message ?: "Video rendered, but couldn't save it to your gallery.")
                            }
                        }
                    }.start()
                }
                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                    finished = true
                    handler.removeCallbacks(progressPoll)
                    output.delete()
                    onError(exportException.message ?: "Video export failed.")
                }
            })
            .build()
        try {
            transformer.start(edited, output.absolutePath)
            onProgress(1)
            handler.post(progressPoll)
        } catch (error: Exception) {
            finished = true
            handler.removeCallbacks(progressPoll)
            output.delete()
            onError(error.message ?: "Couldn't start video export.")
        }
    }
    private fun publishToGallery(context: Context, renderedFile: File): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return renderedFile.absolutePath
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, renderedFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/UnnamedEditor")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android couldn't create a gallery entry for this export.")
        try {
            resolver.openOutputStream(destination)?.use { output ->
                renderedFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Android couldn't open the gallery file.")
            val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(destination, ready, null, null)
            return "Movies/UnnamedEditor/${renderedFile.name}"
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        }
    }
}
