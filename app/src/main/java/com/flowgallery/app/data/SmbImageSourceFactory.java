package com.flowgallery.app.data;

import java.io.File;
import java.io.InputStream;

import coil.decode.ImageSource;
import coil.decode.ImageSources;
import okio.BufferedSource;

/**
 * Bridges Coil's ImageSource factory to Kotlin. The Kotlin top-level
 * function `coil.decode.ImageSource(...)` clashes with the class of the same
 * name in Kotlin resolution, so we call the JVM facade (ImageSources.create)
 * from Java instead.
 */
public final class SmbImageSourceFactory {
    private SmbImageSourceFactory() {
    }

    /** Wrap a buffered stream into an ImageSource (used by fetchers). */
    public static ImageSource create(InputStream stream, android.content.Context context) {
        BufferedSource buffered = okio.Okio.buffer(okio.Okio.source(stream));
        return ImageSources.create(buffered, context);
    }

    /** Wrap a local temp file into an ImageSource (full download decoded). */
    public static ImageSource create(File file) {
        return ImageSources.create(
            okio.Path.get(file.getAbsolutePath()),
            okio.FileSystem.SYSTEM,
            null, null, null
        );
    }
}
