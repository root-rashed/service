package com.example.marketplace.servicecatalog.service;

import com.example.marketplace.cache.CacheNames;
import com.example.marketplace.common.enums.WorkerVerificationStatus;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.worker.event.WorkerVerificationChangedEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Keeps ServiceListing.workerApproved in line with the worker's verification status and clears
 * the service caches. Admin approval / suspension will publish the same event.
 */
@Component
public class ServiceVisibilitySync {

    private final MongoTemplate mongoTemplate;
    private final CacheManager cacheManager;

    public ServiceVisibilitySync(MongoTemplate mongoTemplate, CacheManager cacheManager) {
        this.mongoTemplate = mongoTemplate;
        this.cacheManager = cacheManager;
    }

    @EventListener
    public void onWorkerVerificationChanged(WorkerVerificationChangedEvent event) {
        boolean approved = event.status() == WorkerVerificationStatus.APPROVED;
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("workerId").is(event.userId())),
                new Update().set("workerApproved", approved),
                ServiceListing.class);
        // The cached profile DTOs carry the verification status, so they have to go too.
        clear(CacheNames.WORKER_PROFILES);
        clear(CacheNames.SERVICE_DETAILS);
        clear(CacheNames.POPULAR_SERVICES);
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
