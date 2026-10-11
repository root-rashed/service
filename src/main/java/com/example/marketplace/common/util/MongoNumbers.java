package com.example.marketplace.common.util;

import java.math.BigDecimal;
import org.bson.types.Decimal128;

/**
 * MongoDB aggregation results come back as whatever numeric type the server chose for the stored
 * field (Integer, Long, Double or Decimal128), so every report normalises them through here before
 * doing arithmetic. Lives in one place so all aggregations convert values the same way and no
 * report can fail on a page render because of an unexpected BSON type.
 */
public final class MongoNumbers {

    private MongoNumbers() {
    }

    /** @return the value as a {@link BigDecimal}, or {@link BigDecimal#ZERO} for anything unusable */
    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        // Anything else is a BSON type we do not expect; treat it as zero rather than fail a page render.
        return BigDecimal.ZERO;
    }
}