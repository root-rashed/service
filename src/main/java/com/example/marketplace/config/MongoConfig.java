package com.example.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation;

/**
 * - Enables @CreatedDate / @LastModifiedDate on MongoDB documents.
 * - Stores BigDecimal (prices, fees) as native Decimal128 so range queries and sorting by
 *   price are numeric. Without this, Spring Data would store BigDecimal as a String.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(adapter -> adapter.bigDecimal(BigDecimalRepresentation.DECIMAL128));
    }
}
