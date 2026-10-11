package com.example.marketplace.admin.service;

import com.example.marketplace.admin.dto.AdminDashboardSummary;
import com.example.marketplace.admin.dto.AdminUserDto;
import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.common.util.MongoNumbers;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.common.enums.WorkerVerificationStatus;
import com.example.marketplace.review.model.Review;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.user.model.User;
import com.example.marketplace.worker.model.WorkerProfile;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Read-only platform figures for the admin dashboard.
 *
 * Everything here is a count or an aggregate written straight to the view; no dashboard
 * figure is cached, because moderation actions change them continuously.
 */
@Service
public class AdminDashboardService {

    /** Enough rows to cover the "latest activity" lists without paging. */
    private static final int ACTIVITY_LIMIT = 8;
    private static final int NEW_USERS_LIMIT = 5;

    /** Statuses that still occupy a professional's calendar. */
    private static final List<BookingStatus> OPEN_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.WORKER_ACCEPTED, BookingStatus.IN_PROGRESS);

    private final MongoTemplate mongoTemplate;

    public AdminDashboardService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AdminDashboardSummary getSummary() {
        long customers = count(User.class, Criteria.where("role").is(Role.CUSTOMER));
        long workers = count(User.class, Criteria.where("role").is(Role.WORKER));

        long approvedProfiles = count(WorkerProfile.class,
                Criteria.where("verificationStatus").is(WorkerVerificationStatus.APPROVED));
        long pendingProfiles = count(WorkerProfile.class,
                Criteria.where("verificationStatus").is(WorkerVerificationStatus.PENDING));

        long services = count(ServiceListing.class, null);
        long activeServices = count(ServiceListing.class, Criteria.where("status").is(ServiceStatus.ACTIVE));

        long bookings = count(Booking.class, null);
        Map<BookingStatus, Long> byStatus = new EnumMap<>(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            byStatus.put(status, count(Booking.class, Criteria.where("bookingStatus").is(status)));
        }

        long reviews = count(Review.class, null);
        long hiddenReviews = count(Review.class, Criteria.where("visible").is(false));

        long unpaidCompleted = count(Booking.class, new Criteria()
                .and("bookingStatus").is(BookingStatus.COMPLETED)
                .and("paymentStatus").is(PaymentStatus.PENDING));
        MoneyTotals money = moneyTotals();

        return AdminDashboardSummary.of(
                customers, workers, approvedProfiles, pendingProfiles,
                services, activeServices, bookings,
                byStatus.getOrDefault(BookingStatus.COMPLETED, 0L),
                reviews, hiddenReviews, byStatus, unpaidCompleted,
                money.collected(), money.outstanding(), money.fees());
    }

    // ---------- Activity lists shown next to the figures ----------

    public List<BookingDto> getRecentBookings() {
        return toDtos(mongoTemplate.find(
                new Query().with(Sort.by(Sort.Order.desc("createdAt"))).limit(ACTIVITY_LIMIT), Booking.class));
    }

    /** Bookings that still occupy a professional's calendar, soonest first. */
    public List<BookingDto> getOpenBookings() {
        Query query = Query.query(Criteria.where("bookingStatus").in(OPEN_STATUSES))
                .with(Sort.by(Sort.Order.asc("bookingDate"), Sort.Order.asc("startMinute")))
                .limit(ACTIVITY_LIMIT);
        return toDtos(mongoTemplate.find(query, Booking.class));
    }

    /** Completed jobs whose cash has not been confirmed yet. */
    public List<BookingDto> getUnpaidCompletedBookings() {
        Query query = Query.query(new Criteria()
                        .and("bookingStatus").is(BookingStatus.COMPLETED)
                        .and("paymentStatus").is(PaymentStatus.PENDING))
                .with(Sort.by(Sort.Order.asc("bookingDate")))
                .limit(ACTIVITY_LIMIT);
        return toDtos(mongoTemplate.find(query, Booking.class));
    }

    public List<AdminUserDto> getNewestUsers() {
        return mongoTemplate.find(
                        new Query().with(Sort.by(Sort.Order.desc("createdAt"))).limit(NEW_USERS_LIMIT), User.class)
                .stream()
                .map(u -> AdminUserDto.from(u, u.getLockedUntil() != null))
                .toList();
    }

    private List<BookingDto> toDtos(List<Booking> bookings) {
        return bookings.stream().map(BookingDto::forAdmin).toList();
    }

    // ---------- Internals ----------

    private long count(Class<?> type, Criteria criteria) {
        return mongoTemplate.count(criteria == null ? new Query() : Query.query(criteria), type);
    }

    /**
     * One grouped pass for collected / outstanding / fees rather than three collection scans.
     * "Outstanding" deliberately counts every completed job's value: that work was done and the
     * money is owed, whether or not the professional has confirmed it yet.
     */
    private MoneyTotals moneyTotals() {
        Aggregation aggregation = Aggregation.newAggregation(Aggregation.group()
                .sum(ConditionalOperators.when(Criteria.where("paymentStatus").is(PaymentStatus.PAID))
                        .then("$totalAmount").otherwise(0)).as("collected")
                .sum(ConditionalOperators.when(Criteria.where("bookingStatus").is(BookingStatus.COMPLETED))
                        .then("$totalAmount").otherwise(0)).as("outstanding")
                .sum(ConditionalOperators.when(Criteria.where("paymentStatus").is(PaymentStatus.PAID))
                        .then("$platformFee").otherwise(0)).as("fees"));
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, Booking.class, Document.class);
        Document result = results.getUniqueMappedResult();
        if (result == null) {
            return new MoneyTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new MoneyTotals(MongoNumbers.toBigDecimal(result.get("collected")),
                MongoNumbers.toBigDecimal(result.get("outstanding")),
                MongoNumbers.toBigDecimal(result.get("fees")));
    }

    private record MoneyTotals(BigDecimal collected, BigDecimal outstanding, BigDecimal fees) {
    }
}