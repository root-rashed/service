package com.example.marketplace.notification.repository;

import com.example.marketplace.notification.model.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<Notification> findTop5ByUserIdAndReadFalseOrderByCreatedAtDesc(String userId);

    long countByUserIdAndReadFalse(String userId);

    /** Ownership-scoped: a notification id from a URL only ever touches the owner's own row. */
    Optional<Notification> findByIdAndUserId(String id, String userId);

    long deleteByUserIdAndReadFalse(String userId);
}
