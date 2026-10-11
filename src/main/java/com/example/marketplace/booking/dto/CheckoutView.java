package com.example.marketplace.booking.dto;

import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import java.math.BigDecimal;
import lombok.Value;

/**
 * Everything the checkout page shows before the customer confirms. The amounts are a PREVIEW:
 * when the booking is created the server calculates them again and stores that result.
 */
@Value
public class CheckoutView {

    ServiceListingDto service;
    String workerName;
    BigDecimal servicePrice;
    BigDecimal platformFee;
    BigDecimal totalAmount;
    /** e.g. "5%". */
    String feeLabel;
    /** Earliest and latest selectable booking date (ISO). */
    String minDate;
    String maxDate;
}
