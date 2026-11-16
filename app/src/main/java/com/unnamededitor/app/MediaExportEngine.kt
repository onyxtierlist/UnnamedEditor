package com.unnamededitor.app

import android.content.Context
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * Real single-source export pipeline. Timeline trim is applied to the imported source.
 * The output is an H.264 MP4 written into the app's Movies folder.
 */
object MediaExportEngine {
    fun export(
        context: Context,
        source: android.net.Uri,
        startMs: Long,
        endMs: Long?,
        resolution: String,
        quality: String,
        onProgress: (Int) -> Unit,
        onComplete: (File) -> Unit,
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
        val effects = Effects(emptyList(), listOf(Presentation.createForShortSide(outputShortSide)))
        val edited = EditedMediaItem.Builder(item).setEffects(effects).build()
        val transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
            .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    onProgress(100)
                    onComplete(output)
                }
                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                    output.delete()
                    onError(exportException.message ?: "Video export failed.")
                }
            })
            .build()
        try {
            transformer.start(edited, output.absolutePath)
            onProgress(1)
        } catch (error: Exception) {
            output.delete()
            onError(error.message ?: "Couldn't start video export.")
        }
    }
}
