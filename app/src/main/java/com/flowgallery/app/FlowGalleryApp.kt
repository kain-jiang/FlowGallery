package com.flowgallery.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.flowgallery.app.data.SmartVideoFrameDecoder

class FlowGalleryApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Custom ImageLoader that registers SmartVideoFrameDecoder so video items
     * render first-frame thumbnails — skipping pure-black first frames by
     * sampling forward in the clip until visible content is found.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                // Smart decoder first (handles video/*), default as fallback
                add(SmartVideoFrameDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
