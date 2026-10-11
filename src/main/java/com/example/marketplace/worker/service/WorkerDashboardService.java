package com.example.marketplace.worker.service;

import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.dto.WorkerBookingFilter;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.booking.repository.BookingRepository;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.common.util.MongoNumbers;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.service.ServiceListingService;
import com.example.marketplace.worker.dto.WorkerDashboardSummary;
import com.example.marketplace.worker.dto.WorkerEarningsDto;
import com.example.marketplace.worker.dto.WorkerProfileDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Assembles the professional dashboard.
 *
 * <p>Reads the professional's own profile, listings and bookings, and nothing else. No decision is
 * taken here: every status shown was reached through {@code BookingService}, so the dashboard can
 * never disagree with the bookings page about what a professional is allowed to do.
 */
@Service
public class WorkerDashboardService {

    /** How many bookings each dashboard list shows. */
    private static final int LIST_LIMIT = 5;

    private static final Sort OLDEST_FIRST =
            Sort.by(Sort.Order.asc("bookingDate"), Sort.Order.asc("startMinute"));
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("bookingDate"), Sort.Order.desc("startMinute"));

    private final BookingRepository bookingRepository;
    private final WorkerProfileService workerProfileService;
    private final ServiceListingService serviceListingService;
    private final MongoTemplate mongoTemplate;

    public WorkerDashboardService(BookingRepository bookingRepository,
                                  WorkerProfileService workerProfileService,
                                  ServiceListingService serviceListingService,
                                  MongoTemplate mongoTemplate) {
        this.bookingRepository = bookingRepository;
        this.workerProfileService = workerProfileService;
        this.serviceListingService = serviceListingService;
        this.mongoTemplate = mongoTemplate;
    }

    /** @param userId the signed-in professional's user id */
    public WorkerDashboardSummary getDashboard(String userId) {
        WorkerProfileDto profile = workerProfileService.getOwnProfile(userId).orElse(null);
        List<ServiceListingDto> listings = serviceListingService.listForWorker(userId);

        return new WorkerDashboardSummary(
                profile,
                openBookings(userId, WorkerBookingFilter.PENDING),
                openBookings(userId, WorkerBookingFilter.UPCOMING),
                completedJobs(userId),
                earnings(userId),
                listings.size(),
                listings.stream().filter(ServiceListingDto::isActive).count());
    }

    // ---------- Bookings ----------

    /** The soonest bookings in one of the worker's work-in-progress tabs. */
    private List<BookingDto> openBookings(String workerId, WorkerBookingFilter filter) {
        return bookingRepository
                .findByWorkerIdAndBookingStatusIn(workerId, filter.getStatuses(),
                        PageRequest.of(0, LIST_LIMIT, OLDEST_FIRST))
                .map(BookingDto::forWorker)
                .getContent();
    }

    private List<BookingDto> completedJobs(String workerId) {
        return bookingRepository
                .findByWorkerIdAndBookingStatusIn(workerId, Set.of(BookingStatus.COMPLETED),
                        PageRequest.of(0, LIST_LIMIT, NEWEST_FIRST))
                .map(BookingDto::forWorker)
                .getContent();
    }

    // ---------- Earnings ----------

    /**
     * Cash totals over the professional's completed jobs.
     *
     * <p>A single aggregation covers both halves of the cash picture: the amount already handed
     * over ({@code paymentStatus = PAID}) and the amount still to collect.
     */
    private WorkerEarningsDto earnings(String workerId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("workerId").is(workerId)
                        .and("bookingStatus").is(BookingStatus.COMPLETED)),
                Aggregation.group()
                        .count().as("completed")
                        .sum("servicePrice").as("gross")
                        .sum("platformFee").as("fees")
                        .sum(whenCashCollected("servicePrice")).as("collected"));

        List<Document> rows = mongoTemplate
                .aggregate(aggregation, Booking.class, Document.class).getMappedResults();
        if (rows.isEmpty()) {
            return WorkerEarningsDto.empty();
        }

        Document row = rows.get(0);
        BigDecimal gross = money(row, "gross");
        BigDecimal fees = money(row, "fees");
        BigDecimal collected = money(row, "collected");
        return new WorkerEarningsDto(
                count(row, "completed"), gross, fees,
                gross.subtract(fees),
                collected, gross.subtract(collected));
    }

    // ---------- Aggregation helpers ----------

    /** Adds the amount only when the customer has actually handed the cash over. */
    private static AggregationExpression whenCashCollected(Object value) {
        return ConditionalOperators.when(Criteria.where("paymentStatus").is(PaymentStatus.PAID))
                .then(value).otherwise(0);
    }

    private static long count(Document row, String field) {
        return row.get(field) instanceof Number number ? number.longValue() : 0L;
    }

    private static BigDecimal money(Document row, String field) {
        return MongoNumbers.toBigDecimal(row.get(field));
    }
}