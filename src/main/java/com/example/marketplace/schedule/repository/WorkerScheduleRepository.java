package com.example.marketplace.schedule.repository;

import com.example.marketplace.schedule.model.WorkerSchedule;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WorkerScheduleRepository extends MongoRepository<WorkerSchedule, String> {

    Optional<WorkerSchedule> findByUserId(String userId);
}
