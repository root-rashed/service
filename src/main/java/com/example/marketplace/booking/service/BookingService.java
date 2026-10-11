package com.example.marketplace.booking.service;

import com.example.marketplace.booking.dto.BookingDto;
import com.example.marketplace.booking.dto.BookingRequest;
import com.example.marketplace.booking.dto.CheckoutView;
import com.example.marketplace.booking.dto.CustomerBookingSummary;
import com.example.marketplace.booking.dto.PriceBreakdown;
import com.example.marketplace.booking.dto.WorkerBookingFilter;
import com.example.marketplace.booking.event.BookingStatusChangedEvent;
import com.example.marketplace.booking.model.Booking;
import com.example.marketplace.booking.model.Booking.StatusChange;
import com.example.marketplace.booking.repository.BookingRepository;
import com.example.marketplace.common.enums.BookingStatus;
import com.example.marketplace.common.enums.PaymentMethod;
import com.example.marketplace.common.enums.PaymentStatus;
import com.example.marketplace.common.enums.ServiceStatus;
import com.example.marketplace.common.enums.UserStatus;
import com.example.marketplace.common.exception.BookingException;
import com.example.marketplace.common.exception.PaymentException;
import com.example.marketplace.common.exception.ResourceNotFoundException;
import com.example.marketplace.schedule.service.AvailabilityService;
import com.example.marketplace.servicecatalog.dto.ServiceDetailView;
import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import com.example.marketplace.servicecatalog.model.ServiceListing;
import com.example.marketplace.servicecatalog.repository.ServiceListingRepository;
import com.example.marketplace.servicecatalog.service.PublicServiceQueryService;
import com.example.marketplace.user.model.User;
import com.example.marketplace.user.repository.UserRepository;
import com.example.marketplace.worker.dto.WorkerProfileDto;
import com.example.marketplace.worker.service.WorkerProfileService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Booking creation and the booking state machine.
 *
 * Rules enforced here, on the server:
 * - the customer, worker, price, fee, total, payment method and statuses are never taken from the browser;
 * - the time slot is re-checked against live availability and then claimed atomically (BookingLockService);
 * - every status change follows BookingStatus.canTransitionTo and is applied with a conditional update
 *   (id + owner + expected current status), so concurrent or repeated requests can not corrupt the state;
 * - customers and workers can only reach their own bookings (ownership is part of every query).
 */
@Service
public class BookingService {

    private static final int PAGE_SIZE = 10;

    private final BookingRepository bookingRepository;
    private final ServiceListingRepository serviceListingRepository;
    private final UserRepository userRepository;
    private final WorkerProfileService workerProfileService;
    private final AvailabilityService availabilityService;
    private final BookingLockService lockService;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final PublicServiceQueryService publicServiceQueryService;
    private final BigDecimal platformFeePercent;
    private final int cancelCutoffHours;
    private final int maxAdvanceDays;

    public BookingService(BookingRepository bookingRepository,
                          ServiceListingRepository serviceListingRepository,
                          UserRepository userRepository,
                          WorkerProfileService workerProfileService,
                          AvailabilityService availabilityService,
                          BookingLockService lockService,
                          MongoTemplate mongoTemplate,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock,
                          PublicServiceQueryService publicServiceQueryService,
                          @Value("${app.booking.platform-fee-percent:5}") BigDecimal platformFeePercent,
                          @Value("${app.booking.cancel-cutoff-hours:2}") int cancelCutoffHours,
                          @Value("${app.booking.max-advance-days:60}") int maxAdvanceDays) {
        this.bookingRepository = bookingRepository;
        this.serviceListingRepository = serviceListingRepository;
        this.userRepository = userRepository;
        this.workerProfileService = workerProfileService;
        this.availabilityService = availabilityService;
        this.lockService = lockService;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.publicServiceQueryService = publicServiceQueryService;
        this.platformFeePercent = platformFeePercent;
        this.cancelCutoffHours = cancelCutoffHours;
        this.maxAdvanceDays = maxAdvanceDays;
    }

    // ==================== Checkout preview ====================

    /** Service, price breakdown and date limits for the checkout page. 404 if the service is not public. */
    public CheckoutView previewCheckout(String serviceSlug) {
        ServiceDetailView view = publicServiceQueryService.getDetail(serviceSlug);
        ServiceListingDto service = view.getService();
        PriceBreakdown price = PriceCalculator.calculate(service.getPriceType(), service.getPrice(),
                service.getDurationMinutes(), platformFeePercent);
        LocalDate today = LocalDate.now(clock);
        return new CheckoutView(
                service,
                view.getWorker().getName(),
                price.servicePrice(),
                price.platformFee(),
                price.totalAmount(),
                platformFeePercent.stripTrailingZeros().toPlainString() + "%",
                today.toString(),
                today.plusDays(maxAdvanceDays).toString());
    }

    // ==================== Create ====================

    public BookingDto create(String customerId, BookingRequest request) {
        ServiceListing service = serviceListingRepository
                .findBySlugAndStatusAndWorkerApprovedTrue(request.getServiceSlug(), ServiceStatus.ACTIVE)
                .orElseThrow(() -> ResourceNotFoundException.of("Service"));
        // Authoritative live check that the worker is still approved.
        WorkerProfileDto worker = workerProfileService.getPublicByUserId(service.getWorkerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Service"));
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account"));

        if (customer.getStatus() != UserStatus.ACTIVE) {
            throw new BookingException("Your account cannot make bookings right now.");
        }
        if (service.getWorkerId().equals(customerId)) {
            throw new BookingException("You can't book your own service.");
        }
        if (request.getPaymentMethod() != PaymentMethod.CASH) {
            throw new PaymentException("Only cash payment is available.");
        }

        int start = request.getStartMinute();
        int end = start + service.getDurationMinutes();
        if (!availabilityService.isSlotAvailable(service.getWorkerId(), service.getDurationMinutes(),
                request.getDate(), start)) {
            throw new BookingException("This time is no longer available. Please choose another slot.");
        }

        PriceBreakdown price = PriceCalculator.calculate(service.getPriceType(), service.getPrice(),
                service.getDurationMinutes(), platformFeePercent);

        String bookingId = new ObjectId().toHexString();
        Booking booking = Booking.builder()
                .id(bookingId)
                .customerId(customerId)
                .workerId(service.getWorkerId())
                .serviceId(service.getId())
                .serviceTitle(service.getTitle())
                .serviceSlug(service.getSlug())
                .workerName(worker.getName())
                .customerName(customer.getName())
                .customerPhone(customer.getPhone())
                .bookingDate(request.getDate().toString())
                .startMinute(start)
                .endMinute(end)
                .durationMinutes(service.getDurationMinutes())
                .address(request.getAddress().trim())
                .customerNote(blankToNull(request.getCustomerNote()))
                .servicePrice(price.servicePrice())
                .platformFee(price.platformFee())
                .totalAmount(price.totalAmount())
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PENDING)
                .bookingStatus(BookingStatus.PENDING)
                .statusHistory(new ArrayList<>(List.of(new StatusChange(BookingStatus.PENDING, Instant.now(clock), "CUSTOMER"))))
                .build();

        // The atomic step: claim the time cells first, then store the booking.
        lockService.acquire(bookingId, service.getWorkerId(), booking.getBookingDate(), start, end);
        try {
            mongoTemplate.insert(booking);
        } catch (RuntimeException ex) {
            lockService.release(bookingId);
            throw ex;
        }

        eventPublisher.publishEvent(new BookingStatusChangedEvent(
                bookingId, customerId, service.getWorkerId(), null, BookingStatus.PENDING));
        return BookingDto.forCustomer(booking, isCancellable(booking));
    }

    // ==================== Worker actions ====================

    public BookingDto accept(String workerId, String bookingId) {
        Booking booking = ownedByWorker(workerId, bookingId);
        if (startInstant(booking).isBefore(Instant.now(clock))) {
            throw new BookingException("This request has expired because its start time has passed.");
        }
        return forWorker(change(booking, "workerId", workerId, BookingStatus.WORKER_ACCEPTED, "WORKER", null));
    }

    public BookingDto reject(String workerId, String bookingId) {
        Booking booking = ownedByWorker(workerId, bookingId);
        Booking updated = change(booking, "workerId", workerId, BookingStatus.WORKER_REJECTED, "WORKER",
                new Update().set("paymentStatus", PaymentStatus.CANCELLED));
        lockService.release(bookingId);
        return forWorker(updated);
    }

    /** A worker may cancel a confirmed booking before it starts. */
    public BookingDto cancelByWorker(String workerId, String bookingId) {
        Booking booking = ownedByWorker(workerId, bookingId);
        Booking updated = change(booking, "workerId", workerId, BookingStatus.CANCELLED, "WORKER",
                new Update().set("paymentStatus", PaymentStatus.CANCELLED));
        lockService.release(bookingId);
        return forWorker(updated);
    }

    public BookingDto start(String workerId, String bookingId) {
        Booking booking = ownedByWorker(workerId, bookingId);
        if (LocalDate.parse(booking.getBookingDate()).isAfter(LocalDate.now(clock))) {
            throw new BookingException("You can start the job on the day of the booking.");
        }
        return forWorker(change(booking, "workerId", workerId, BookingStatus.IN_PROGRESS, "WORKER", null));
    }

    /** Completing does not mark the cash as paid: the worker confirms that separately. */
    public BookingDto complete(String workerId, String bookingId) {
        Booking booking = ownedByWorker(workerId, bookingId);
        return forWorker(change(booking, "workerId", workerId, BookingStatus.COMPLETED, "WORKER", null));
    }

    // ==================== Customer actions ====================

    public BookingDto cancelByCustomer(String customerId, String bookingId) {
        Booking booking = ownedByCustomer(customerId, bookingId);
        if (!isCancellable(booking)) {
            throw new BookingException("This booking can no longer be cancelled. Bookings can be cancelled until "
                    + cancelCutoffHours + " hours before they start.");
        }
        Booking updated = change(booking, "customerId", customerId, BookingStatus.CANCELLED, "CUSTOMER",
                new Update().set("paymentStatus", PaymentStatus.CANCELLED));
        lockService.release(bookingId);
        return BookingDto.forCustomer(updated, false);
    }

    // ==================== Administrator actions ====================

    /**
     * Admin override for a booking that has to be closed (for example a job that will never
     * happen). The state machine still applies, so a completed or already cancelled booking
     * can not be cancelled again; the change is recorded in the audit trail as "ADMIN".
     */
    public BookingDto cancelByAdmin(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
        Booking updated = change(booking, "id", booking.getId(), BookingStatus.CANCELLED, "ADMIN",
                new Update().set("paymentStatus", PaymentStatus.CANCELLED));
        lockService.release(bookingId);
        return BookingDto.forAdmin(updated);
    }

    // ==================== Reads (ownership-scoped) ====================

    public BookingDto getForCustomer(String customerId, String bookingId) {
        Booking booking = ownedByCustomer(customerId, bookingId);
        return BookingDto.forCustomer(booking, isCancellable(booking));
    }

    public BookingDto getForWorker(String workerId, String bookingId) {
        return forWorker(ownedByWorker(workerId, bookingId));
    }

    public Page<BookingDto> listForCustomer(String customerId, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Order.desc("createdAt")));
        return bookingRepository.findByCustomerId(customerId, pageable)
                .map(b -> BookingDto.forCustomer(b, isCancellable(b)));
    }

    /** Next booking and the latest three, for the customer dashboard (based on the 10 newest bookings). */
    public CustomerBookingSummary getDashboardSummary(String customerId) {
        List<BookingDto> latest = listForCustomer(customerId, 0).getContent();
        String today = LocalDate.now(clock).toString();
        BookingDto upcoming = latest.stream()
                .filter(b -> b.getBookingStatus().blocksTimeSlot() && b.getBookingDate().compareTo(today) >= 0)
                .min(Comparator.comparing(BookingDto::getBookingDate).thenComparingInt(BookingDto::getStartMinute))
                .orElse(null);
        return new CustomerBookingSummary(upcoming, latest.stream().limit(3).toList());
    }

    /** Number of bookings per worker tab, keyed by WorkerBookingFilter name. */
    public Map<String, Long> countsForWorker(String workerId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (WorkerBookingFilter filter : WorkerBookingFilter.values()) {
            counts.put(filter.name(), bookingRepository.countByWorkerIdAndBookingStatusIn(workerId, filter.getStatuses()));
        }
        return counts;
    }

    public Page<BookingDto> listForWorker(String workerId, WorkerBookingFilter filter, int page) {
        Sort sort = filter == WorkerBookingFilter.HISTORY
                ? Sort.by(Sort.Order.desc("bookingDate"), Sort.Order.desc("startMinute"))
                : Sort.by(Sort.Order.asc("bookingDate"), Sort.Order.asc("startMinute"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort);
        return bookingRepository.findByWorkerIdAndBookingStatusIn(workerId, filter.getStatuses(), pageable)
                .map(this::forWorker);
    }

    // ==================== Internals ====================

    private Booking ownedByCustomer(String customerId, String bookingId) {
        return bookingRepository.findByIdAndCustomerId(bookingId, customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
    }

    private Booking ownedByWorker(String workerId, String bookingId) {
        return bookingRepository.findByIdAndWorkerId(bookingId, workerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking"));
    }

    /**
     * Applies a status change. The state machine is checked first for a clear error message, then the
     * update itself is conditional on the booking still having the status we just read, so two
     * concurrent requests can not both succeed.
     */
    private Booking change(Booking booking, String ownerField, String ownerId,
                           BookingStatus target, String actor, Update extra) {
        BookingStatus from = booking.getBookingStatus();
        if (!from.canTransitionTo(target)) {
            throw new BookingException("This booking can't move from \"" + from + "\" to \"" + target + "\".");
        }
        Query query = Query.query(Criteria.where("id").is(booking.getId())
                .and(ownerField).is(ownerId)
                .and("bookingStatus").is(from));
        Update update = extra == null ? new Update() : extra;
        update.set("bookingStatus", target)
                .set("updatedAt", Instant.now(clock))
                .push("statusHistory", new StatusChange(target, Instant.now(clock), actor));

        Booking updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (updated == null) {
            throw new BookingException("This booking was just changed by someone else. Please reload the page.");
        }
        eventPublisher.publishEvent(new BookingStatusChangedEvent(
                updated.getId(), updated.getCustomerId(), updated.getWorkerId(), from, target));
        return updated;
    }

    /** Free cancellation until the cut-off before the start time, while the booking is still active. */
    private boolean isCancellable(Booking booking) {
        if (!booking.getBookingStatus().isCancellableByCustomer()) {
            return false;
        }
        Instant latest = startInstant(booking).minus(Duration.ofHours(cancelCutoffHours));
        return Instant.now(clock).isBefore(latest);
    }

    private Instant startInstant(Booking booking) {
        return LocalDateTime.of(LocalDate.parse(booking.getBookingDate()), java.time.LocalTime.MIDNIGHT)
                .plusMinutes(booking.getStartMinute())
                .atZone(clock.getZone())
                .toInstant();
    }

    private BookingDto forWorker(Booking booking) {
        return BookingDto.forWorker(booking);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
