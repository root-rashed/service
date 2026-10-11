package com.example.marketplace.favorite.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A service saved by a customer. The unique (userId, serviceId) index makes duplicates impossible,
 * even when two requests arrive at the same moment.
 */
@Document(collection = "favorites")
@CompoundIndex(name = "user_service_unique", def = "{'userId': 1, 'serviceId': 1}", unique = true)
@CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {

    @Id
    private String id;

    /** Always the logged-in user, set on the server. */
    private String userId;

    private String serviceId;

    @CreatedDate
    private Instant createdAt;
}
