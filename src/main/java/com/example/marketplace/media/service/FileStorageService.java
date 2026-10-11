package com.example.marketplace.media.service;

import com.example.marketplace.media.model.MediaFolder;

/**
 * Storage abstraction. The local-disk implementation is used in development;
 * an object-storage implementation can replace it without touching callers.
 * Callers only ever hold the returned key, never a filesystem path.
 */
public interface FileStorageService {

    /**
     * Stores already-validated image bytes under a generated, unguessable name.
     *
     * @param extension one of jpg, png, webp
     * @return the storage key, e.g. "service-images/3f2a...c1.jpg"
     */
    String store(MediaFolder folder, byte[] content, String extension);

    /** Deletes a stored file. Unknown or malformed keys are ignored. */
    void delete(String key);
}
