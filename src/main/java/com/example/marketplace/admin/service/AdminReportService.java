package com.example.marketplace.admin.service;

import com.example.marketplace.admin.dto.AdminReport;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.common.util.MongoNumbers;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.worker.model.WorkerProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Operational reporting: monthly booking and cash totals plus two simple leaderboards.
 *
 * Bookings store their date as an ISO string ("2026-10-12"), so the booked month is derived
 * with {@code $substrBytes} rather than a date field. All aggregation output is read as raw
 * Documents and normalised by {@link MongoNumbers}, which keeps the report working whatever
 * numeric type Mongo chose for the stored amounts.
 */
@Service
public class AdminReportService {

    private static final int MONTHS = 6;
    private static final int LEADERBOARD_SIZE = 8;

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public AdminReportService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public AdminReport getReport() {
        return new AdminReport(monthRows(), topServices(), topWorkers());
    }

    // ---------- Monthly totals ----------

    /**
     * Cash and completed-value totals per booked month. Only bookings whose service date falls
     * in the window are counted, so "cash collected" really is the cash collected in that month
     * rather than the month the request was placed.
     */
    private List<AdminReport.MonthRow> monthRows() {
        List<YearMonth> window = monthWindow();
        // ISO dates compare correctly as strings, so the whole window is one range match.
        String windowStart = window.get(0).toString() + "-01";

        // Grouped per booked DAY, then folded into months in Java. Booking dates are stored as
        // ISO strings rather than a date field, and at most ~180 daily groups is a trivial
        // amount of data - cheaper and far less fragile than deriving the month server-side.
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("bookingDate").gte(windowStart)),
                Aggregation.group("bookingDate")
                        .count().as("bookings")
                        .sum(whenCompleted(1)).as("completed")
                        .sum(whenCompleted("$totalAmount")).as("jobValue")
                        .sum(whenPaid("$totalAmount")).as("cash")
                        .sum(whenPaid("$platformFee")).as("fees"));

        Map<YearMonth, Totals> byMonth = new LinkedHashMap<>();
        for (Document row : mongoTemplate.aggregate(aggregation, Booking.class, Document.class).getMappedResults()) {
            String date = row.getString("_id");
            if (date == null || date.length() < 7) {
                continue;
            }
            YearMonth month = YearMonth.parse(date.substring(0, 7));
            byMonth.computeIfAbsent(month, key -> new Totals()).add(row);
        }

        List<AdminReport.MonthRow> rows = new ArrayList<>(window.size());
        for (YearMonth month : window) {
            Totals totals = byMonth.getOrDefault(month, new Totals());
            rows.add(new AdminReport.MonthRow(
                    month.toString(),
                    MONTH_LABEL.format(month.atDay(1)),
                    totals.bookings,
                    totals.completed,
                    totals.jobValue,
                    totals.cash,
                    totals.fees));
        }
        return rows;
    }

    /** Accumulator for the daily groups that make up one month. */
    private static final class Totals {
        private long bookings;
        private long completed;
        private BigDecimal jobValue = BigDecimal.ZERO;
        private BigDecimal cash = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;

        void add(Document row) {
            bookings += count(row, "bookings");
            completed += count(row, "completed");
            jobValue = jobValue.add(money(row, "jobValue"));
            cash = cash.add(money(row, "cash"));
            fees = fees.add(money(row, "fees"));
        }
    }

    /** The last {@value #MONTHS} calendar months, oldest first. */
    private List<YearMonth> monthWindow() {
        YearMonth current = YearMonth.now(clock);
        List<YearMonth> months = new ArrayList<>(MONTHS);
        for (int back = MONTHS - 1; back >= 0; back--) {
            months.add(current.minusMonths(back));
        }
        return months;
    }

    // ---------- Leaderboards ----------

    /** Most-booked services by booking count, with the value of the completed ones. */
    private List<AdminReport.TopServiceRow> topServices() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("serviceTitle")
                        .count().as("bookings")
                        .sum(whenCompleted("$totalAmount")).as("value"),
                Aggregation.sort(Sort.by(Sort.Order.desc("bookings"))),
                Aggregation.limit(LEADERBOARD_SIZE));
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, Booking.class, Document.class);
        List<AdminReport.TopServiceRow> rows = new ArrayList<>(results.getMappedResults().size());
        for (Document row : results.getMappedResults()) {
            rows.add(new AdminReport.TopServiceRow(
                    row.getString("_id"), count(row, "bookings"), money(row, "value")));
        }
        return rows;
    }

    /** Professionals with the most completed jobs. */
    private List<AdminReport.TopWorkerRow> topWorkers() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("bookingStatus").is(BookingStatus.COMPLETED)),
                Aggregation.group("workerId")
                        .count().as("completed")
                        .sum("totalAmount").as("value"),
                Aggregation.sort(Sort.by(Sort.Order.desc("completed"))),
                Aggregation.limit(LEADERBOARD_SIZE));
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, Booking.class, Document.class);
        if (results.getMappedResults().isEmpty()) {
            return List.of();
        }

        // One extra read to turn worker ids into the names and public links we display.
        List<String> workerIds = results.getMappedResults().stream()
                .map(row -> row.getString("_id"))
                .toList();
        Map<String, WorkerProfile> profiles = mongoTemplate.find(
                        Query.query(Criteria.where("userId").in(workerIds)), WorkerProfile.class).stream()
                .collect(Collectors.toMap(WorkerProfile::getUserId, Function.identity(), (a, b) -> a));

        List<AdminReport.TopWorkerRow> rows = new ArrayList<>();
        for (Document row : results.getMappedResults()) {
            WorkerProfile profile = profiles.get(row.getString("_id"));
            rows.add(new AdminReport.TopWorkerRow(
                    profile == null ? "Removed professional" : profile.getDisplayName(),
                    profile == null ? null : profile.getSlug(),
                    count(row, "completed"),
                    money(row, "value")));
        }
        return rows;
    }

    // ---------- Result helpers ----------

    /** Counts the booking only when the job was actually completed. */
    private static AggregationExpression whenCompleted(Object value) {
        return ConditionalOperators.when(Criteria.where("bookingStatus").is(BookingStatus.COMPLETED))
                .then(value).otherwise(0);
    }

    /** Adds the amount only when the cash payment has been confirmed. */
    private static AggregationExpression whenPaid(Object value) {
        return ConditionalOperators.when(Criteria.where("paymentStatus").is(PaymentStatus.PAID))
                .then(value).otherwise(0);
    }

    private static long count(Document row, String field) {
        if (row == null || !(row.get(field) instanceof Number number)) {
            return 0L;
        }
        return number.longValue();
    }

    private static BigDecimal money(Document row, String field) {
        return row == null ? BigDecimal.ZERO : MongoNumbers.toBigDecimal(row.get(field));
    }
}