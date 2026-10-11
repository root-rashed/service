package com.example.marketplace.favorite.repository;

import com.example.marketplace.favorite.model.Favorite;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FavoriteRepository extends MongoRepository<Favorite, String> {

    boolean existsByUserIdAndServiceId(String userId, String serviceId);

    long deleteByUserIdAndServiceId(String userId, String serviceId);

    Page<Favorite> findByUserId(String userId, Pageable pageable);

    /** Which of these services has this user saved? Used to fill the hearts on a page of cards. */
    List<Favorite> findByUserIdAndServiceIdIn(String userId, Collection<String> serviceIds);
}
