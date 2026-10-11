package com.example.marketplace.user.repository;

import com.example.marketplace.common.enums.Role;
import com.example.marketplace.user.model.User;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Used by the bootstrap seeder so an existing administrator is never touched. */
    boolean existsByRole(Role role);
}
