package com.example.marketplace.media.model;

/** The only folders files can be stored in. Never built from user input. */
public enum MediaFolder {
    SERVICE_IMAGES("service-images"),
    PROFILE_IMAGES("profile-images");

    private final String directory;

    MediaFolder(String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return directory;
    }
}
