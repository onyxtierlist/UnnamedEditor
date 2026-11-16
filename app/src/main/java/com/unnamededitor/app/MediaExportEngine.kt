package com.unnamededitor.app

import android.content.Context
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
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
            .apply { if (endMs != null && endMs > startMs then setEndPositionMs(endMs) }
            .build()
        val item = MediaItem.Builder().setUri(source).setClippingConfiguration(clipping).build()
        val edited = EditedMediaItem.Builder(item).build()
        val transformer = Transformer.Builder(context)
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
