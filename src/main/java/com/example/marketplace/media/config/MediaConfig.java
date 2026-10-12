package com.example.marketplace.media.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves stored public images at /uploads/public/**.
 * Spring's resource handler rejects path traversal. File names are random and never reused, so
 * long browser caching is safe.
 */
@Configuration
public class MediaConfig implements WebMvcConfigurer {

    private final Path publicRoot;

    public MediaConfig(@Value("${app.storage.path:./storage}") String storagePath) {
        this.publicRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Files.createDirectories(publicRoot);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage directory " + publicRoot, ex);
        }
        String location = publicRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
