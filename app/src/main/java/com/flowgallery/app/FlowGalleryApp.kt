package com.flowgallery.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.flowgallery.app.data.SmbFetcher
import com.flowgallery.app.data.SmartVideoFrameDecoder

class FlowGalleryApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    /** Single shared ImageLoader — also used by "Clear Cache" in settings. */
    private val loader by lazy { buildImageLoader() }

    override fun newImageLoader(): ImageLoader = loader

    fun clearImageCache() {
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    private fun buildImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                // SMB shares (smb:// URLs)
                add(SmbFetcher.Factory())
                // Smart decoder first (handles video/*), default as fallback
                add(SmartVideoFrameDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
