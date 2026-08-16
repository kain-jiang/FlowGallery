package com.flowgallery.app.data

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options

/**
 * Video thumbnail decoder that skips pure-black first frames.
 *
 * The default Coil VideoFrameDecoder always returns frame 0; some videos
 * start fully black, producing an invisible thumbnail. This decoder samples
 * frame 0 and steps forward (1s, 2s, 4s, 8s …) until a frame with visible
 * content is found.
 *
 * Because Coil's ImageSource doesn't expose the original URI, the caller
 * passes it via request parameters under [KEY_VIDEO_URI].
 */
class SmartVideoFrameDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val uri = options.parameters.value<String>(KEY_VIDEO_URI)?.let { Uri.parse(it) }
        val retriever = MediaMetadataRetriever()
        try {
            if (uri != null) {
                if (uri.scheme == "smb") {
                    // SMB videos: serve via a streaming MediaDataSource so
                    // MediaMetadataRetriever reads frames on demand (no full
                    // download).
                    retriever.setDataSource(
                        SmbMediaDataSource(uri.toString())
                    )
                } else {
                    retriever.setDataSource(options.context, uri)
                }
            } else {
                source.fileOrNull()?.let { retriever.setDataSource(it.toString()) }
            }

            val durationUs: Long = runCatching {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLong()?.times(1000L) ?: 0L
            }.getOrDefault(0L)

            // Sample times: 0, 1s, 2s, 4s, 8s, 16s (cap at clip length).
            val sampleTimes = mutableListOf(0L)
            var t = 1_000_000L
            while (t <= durationUs && sampleTimes.size < 6) {
                sampleTimes.add(t)
                t = t * 2
            }

            var fallback: Bitmap? = null
            for (timeUs in sampleTimes) {
                val frame = runCatching {
                    retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                }.getOrNull()
                if (frame == null) continue
                if (fallback == null) fallback = frame
                if (!isMostlyBlack(frame)) {
                    return DecodeResult(frame.asDrawable(), isSampled = false)
                }
                if (frame !== fallback) frame.recycle()
            }

            return DecodeResult(
                (fallback ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)).asDrawable(),
                isSampled = false
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Rough luminance check: >90% of sampled pixels darker than [threshold]. */
    private fun isMostlyBlack(bitmap: Bitmap, threshold: Int = 20): Boolean {
        val step = 8
        var dark = 0
        var total = 0
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val px = bitmap.getPixel(x, y)
                val lum = ((px shr 16 and 0xFF) + (px shr 8 and 0xFF) + (px and 0xFF)) / 3
                if (lum < threshold) dark++
                total++
            }
        }
        return total > 0 && dark.toFloat() / total > 0.9f
    }

    private fun Bitmap.asDrawable() = BitmapDrawable(options.context.resources, this)

    companion object {
        /** Request parameter key carrying the original video URI. */
        const val KEY_VIDEO_URI = "flowgallery_video_uri"
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: coil.ImageLoader
        ): Decoder? {
            val isVideo = result.mimeType?.startsWith("video/") == true ||
                result.source.fileOrNull()?.toString()?.substringAfterLast('.', "")
                    ?.lowercase() in VIDEO_EXTS
            return if (isVideo) SmartVideoFrameDecoder(result.source, options) else null
        }

        private val VIDEO_EXTS = setOf(
            "mp4", "mkv", "webm", "mov", "avi", "3gp", "flv", "ts",
            "m4v", "wmv", "rmvb", "mpg", "mpeg", "rm"
        )
    }
}
