package com.example.marketplace.media;

/** Turns a stored file key such as "service-images/abc.jpg" into its public URL path. */
public final class MediaUrls {

    public static final String PUBLIC_PREFIX = "/uploads/public/";

    private MediaUrls() {
    }

    public static String publicUrl(String key) {
        return key == null ? null : PUBLIC_PREFIX + key;
    }
}
