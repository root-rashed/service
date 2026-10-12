package com.example.marketplace.media.service;

import com.example.marketplace.media.model.MediaFolder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stores files under {app.storage.path}/public/. File names are random UUIDs (the original
 * filename is never used), the folder comes from a fixed enum, and every resolved path is checked
 * to stay inside the storage root, so path traversal is not possible.
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "png", "webp");
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^(service-images|profile-images)/[0-9a-f]{32}\\.(jpg|png|webp)$");

    private final Path publicRoot;

    public LocalFileStorageService(@Value("${app.storage.path:./storage}") String storagePath) {
        this.publicRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(publicRoot);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage directory " + publicRoot, ex);
        }
    }

    @Override
    public String store(MediaFolder folder, byte[] content, String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file extension");
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path directory = publicRoot.resolve(folder.getDirectory()).normalize();
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(publicRoot)) {
            throw new IllegalStateException("Resolved path escapes the storage root");
        }
        try {
            Files.createDirectories(directory);
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store file", ex);
        }
        return folder.getDirectory() + "/" + fileName;
    }

    @Override
    public void delete(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            return;
        }
        Path target = publicRoot.resolve(key).normalize();
        if (!target.startsWith(publicRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}", key);
        }
    }
}
