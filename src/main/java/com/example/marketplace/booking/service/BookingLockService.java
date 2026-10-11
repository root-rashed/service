package com.example.marketplace.booking.service;

import com.example.marketplace.booking.model.BookingLock;
import com.example.marketplace.common.exception.BookingException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Atomic double-booking protection without transactions.
 *
 * A worker's day is split into 5-minute cells. A booking claims every cell it covers by inserting one
 * lock document per cell, with the id "workerId:date:cell". MongoDB's unique _id makes the second
 * claimant of any cell fail with a duplicate-key error, even if two requests arrive at the same instant.
 * If any cell is taken, the cells already claimed by this booking are released again (all or nothing).
 * Cells are rounded outwards, so the protection is never weaker than the real time range.
 */
@Service
public class BookingLockService {

    static final int CELL_MINUTES = 5;

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public BookingLockService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /** @throws BookingException if any part of the time range is already taken */
    public void acquire(String bookingId, String workerId, String isoDate, int startMinute, int endMinute) {
        Instant expireAt = LocalDate.parse(isoDate).plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        int[] cells = cellRange(startMinute, endMinute);
        try {
            for (int cell = cells[0]; cell <= cells[1]; cell++) {
                mongoTemplate.insert(new BookingLock(workerId + ":" + isoDate + ":" + cell, bookingId, expireAt));
            }
        } catch (DuplicateKeyException ex) {
            release(bookingId);
            throw new BookingException("That time was just taken by another booking. Please choose a different slot.");
        } catch (RuntimeException ex) {
            release(bookingId);
            throw ex;
        }
    }

    /** Frees every cell held by a booking (cancelled or rejected). */
    public void release(String bookingId) {
        mongoTemplate.remove(Query.query(Criteria.where("bookingId").is(bookingId)), BookingLock.class);
    }

    /**
     * First and last cell (inclusive) covered by [startMinute, endMinute). The start is rounded down
     * and the end rounded up to whole cells.
     */
    static int[] cellRange(int startMinute, int endMinute) {
        int first = startMinute / CELL_MINUTES;
        int last = (endMinute + CELL_MINUTES - 1) / CELL_MINUTES - 1;
        return new int[]{first, Math.max(first, last)};
    }
}
