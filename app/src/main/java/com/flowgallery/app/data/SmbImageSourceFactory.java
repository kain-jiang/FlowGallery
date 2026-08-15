package com.flowgallery.app.data;

import android.content.Context;

import java.io.InputStream;

import coil.decode.ImageSource;
import coil.decode.ImageSources;
import okio.BufferedSource;

/**
 * Bridges Coil's ImageSource factory to Kotlin. The Kotlin top-level
 * function `coil.decode.ImageSource(source, context)` clashes with the
 * class of the same name in Kotlin resolution, so we call the JVM facade
 * (ImageSources.create) from Java instead. Accepts a raw InputStream and
 * does the okio wrapping here to avoid Kotlin overload ambiguity.
 */
public final class SmbImageSourceFactory {
    private SmbImageSourceFactory() {
    }

    public static ImageSource create(InputStream stream, Context context) {
        BufferedSource buffered = okio.Okio.buffer(okio.Okio.source(stream));
        return ImageSources.create(buffered, context);
    }
}
