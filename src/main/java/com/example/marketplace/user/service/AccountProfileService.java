package com.example.marketplace.user.service;

import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.common.util.PhoneUtil;
import com.example.marketplace.media.model.MediaFolder;
import com.example.marketplace.media.service.FileStorageService;
import com.example.marketplace.media.service.ImageProcessingService;
import com.example.marketplace.media.service.ImageProcessingService.ProcessedImage;
import com.example.marketplace.user.dto.AccountProfileDto;
import com.example.marketplace.user.dto.ProfileUpdateForm;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.repository.UserRepository;
import com.example.marketplace.worker.event.WorkerVerificationChangedEvent;
import com.example.marketplace.worker.model.WorkerProfile;
import com.example.marketplace.worker.repository.WorkerProfileRepository;
import java.util.function.Consumer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service account profile: name, phone and profile photo (upload, replace, remove).
 * The user id always comes from the authenticated principal. For workers, the public worker
 * profile keeps a copy of the name and photo, which is updated here and announced through
 * WorkerVerificationChangedEvent so cached pages are refreshed.
 */
@Service
public class AccountProfileService {

    private static final int PHOTO_EDGE_PX = 512;

    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final ImageProcessingService imageProcessingService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public AccountProfileService(UserRepository userRepository,
                                 WorkerProfileRepository workerProfileRepository,
                                 ImageProcessingService imageProcessingService,
                                 FileStorageService fileStorageService,
                                 ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.workerProfileRepository = workerProfileRepository;
        this.imageProcessingService = imageProcessingService;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
    }

    public AccountProfileDto getProfile(String userId) {
        return AccountProfileDto.from(findUser(userId));
    }

    public ProfileUpdateForm getForm(String userId) {
        User user = findUser(userId);
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setName(user.getName());
        form.setPhone(user.getPhone());
        return form;
    }

    @CacheEvict(cacheNames = CacheNames.WORKER_PROFILES, allEntries = true)
    public void updateProfile(String userId, ProfileUpdateForm form) {
        String phone = PhoneUtil.normalize(form.getPhone());
        User user = findUser(userId);
        String name = form.getName().trim();
        user.setName(name);
        user.setPhone(phone);
        userRepository.save(user);
        syncWorkerProfile(user, p -> p.setDisplayName(name));
    }

    /** Stores a new photo (replacing any old one). The old file is deleted only after the database is updated. */
    @CacheEvict(cacheNames = CacheNames.WORKER_PROFILES, allEntries = true)
    public void uploadPhoto(String userId, MultipartFile file) {
        ProcessedImage processed = imageProcessingService.process(file, PHOTO_EDGE_PX);
        User user = findUser(userId);
        String oldKey = user.getProfileImage();
        String newKey = fileStorageService.store(MediaFolder.PROFILE_IMAGES, processed.content(), processed.extension());
        try {
            user.setProfileImage(newKey);
            userRepository.save(user);
        } catch (RuntimeException ex) {
            fileStorageService.delete(newKey);
            throw ex;
        }
        syncWorkerProfile(user, p -> p.setProfileImage(newKey));
        fileStorageService.delete(oldKey);
    }

    @CacheEvict(cacheNames = CacheNames.WORKER_PROFILES, allEntries = true)
    public void removePhoto(String userId) {
        User user = findUser(userId);
        String oldKey = user.getProfileImage();
        if (oldKey == null) {
            return;
        }
        user.setProfileImage(null);
        userRepository.save(user);
        syncWorkerProfile(user, p -> p.setProfileImage(null));
        fileStorageService.delete(oldKey);
    }

    // ---------- Helpers ----------

    private User findUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("Account"));
    }

    private void syncWorkerProfile(User user, Consumer<WorkerProfile> change) {
        if (user.getRole() != Role.WORKER) {
            return;
        }
        workerProfileRepository.findByUserId(user.getId()).ifPresent(profile -> {
            change.accept(profile);
            workerProfileRepository.save(profile);
            // Clears the service caches that embed worker data; the status itself is unchanged.
            eventPublisher.publishEvent(new WorkerVerificationChangedEvent(profile.getUserId(), profile.getVerificationStatus()));
        });
    }
}
