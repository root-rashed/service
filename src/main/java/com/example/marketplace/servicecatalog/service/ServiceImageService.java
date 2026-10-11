package com.example.marketplace.servicecatalog.service;

import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.exception.ValidationException;
import com.example.marketplace.media.model.MediaFolder;
import com.example.marketplace.media.service.FileStorageService;
import com.example.marketplace.media.service.ImageProcessingService;
import com.example.marketplace.media.service.ImageProcessingService.ProcessedImage;
import com.example.marketplace.servicecatalog.model.ServiceImage;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.servicecatalog.repository.ServiceListingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photo management for a worker's own services: upload several, choose the primary photo, reorder, delete.
 * Every lookup is scoped by the worker id from the authenticated principal, so image ids from a URL
 * can only ever act on that worker's own service.
 */
@Service
public class ServiceImageService {

    public static final int MAX_IMAGES = 8;
    private static final int MAX_EDGE_PX = 1600;

    private final ServiceListingRepository repository;
    private final ImageProcessingService imageProcessingService;
    private final FileStorageService fileStorageService;

    public ServiceImageService(ServiceListingRepository repository,
                               ImageProcessingService imageProcessingService,
                               FileStorageService fileStorageService) {
        this.repository = repository;
        this.imageProcessingService = imageProcessingService;
        this.fileStorageService = fileStorageService;
    }

    /** All-or-nothing: if any file is rejected, files stored earlier in this call are removed again. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public void upload(String workerId, String serviceId, List<MultipartFile> files) {
        List<MultipartFile> selected = files == null ? List.of() : files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();
        if (selected.isEmpty()) {
            throw new ValidationException("Please choose at least one image.");
        }

        ServiceListing listing = findOwned(workerId, serviceId);
        List<ServiceImage> images = mutableImages(listing);
        if (images.size() + selected.size() > MAX_IMAGES) {
            throw new ValidationException("A service can have up to " + MAX_IMAGES + " photos.");
        }

        List<String> storedKeys = new ArrayList<>();
        try {
            for (MultipartFile file : selected) {
                ProcessedImage processed = imageProcessingService.process(file, MAX_EDGE_PX);
                String key = fileStorageService.store(MediaFolder.SERVICE_IMAGES, processed.content(), processed.extension());
                storedKeys.add(key);
                images.add(new ServiceImage(UUID.randomUUID().toString(), key, Instant.now()));
            }
            listing.setImages(images);
            repository.save(listing);
        } catch (RuntimeException ex) {
            storedKeys.forEach(fileStorageService::delete);
            throw ex;
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public void makePrimary(String workerId, String serviceId, String imageId) {
        ServiceListing listing = findOwned(workerId, serviceId);
        List<ServiceImage> images = mutableImages(listing);
        int index = indexOf(images, imageId);
        if (index > 0) {
            images.add(0, images.remove(index));
            listing.setImages(images);
            repository.save(listing);
        }
    }

    /** Moves a photo one position earlier or later. */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public void move(String workerId, String serviceId, String imageId, boolean earlier) {
        ServiceListing listing = findOwned(workerId, serviceId);
        List<ServiceImage> images = mutableImages(listing);
        int index = indexOf(images, imageId);
        int target = earlier ? index - 1 : index + 1;
        if (target >= 0 && target < images.size()) {
            Collections.swap(images, index, target);
            listing.setImages(images);
            repository.save(listing);
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SERVICE_DETAILS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.POPULAR_SERVICES, allEntries = true)
    })
    public void delete(String workerId, String serviceId, String imageId) {
        ServiceListing listing = findOwned(workerId, serviceId);
        List<ServiceImage> images = mutableImages(listing);
        ServiceImage removed = images.remove(indexOf(images, imageId));
        listing.setImages(images);
        repository.save(listing);
        fileStorageService.delete(removed.getKey()); // only after the database no longer references it
    }

    // ---------- Helpers ----------

    private ServiceListing findOwned(String workerId, String serviceId) {
        return repository.findByIdAndWorkerId(serviceId, workerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Service"));
    }

    private List<ServiceImage> mutableImages(ServiceListing listing) {
        return listing.getImages() == null ? new ArrayList<>() : new ArrayList<>(listing.getImages());
    }

    private int indexOf(List<ServiceImage> images, String imageId) {
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getId().equals(imageId)) {
                return i;
            }
        }
        throw ResourceNotFoundException.of("Photo");
    }
}
