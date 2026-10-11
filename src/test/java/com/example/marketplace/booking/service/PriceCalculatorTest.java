package com.example.marketplace.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.booking.dto.PriceBreakdown;
import com.example.marketplace.common.enums.PriceType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

    @Test
    void fixedPriceAddsPercentageFee() {
        PriceBreakdown p = PriceCalculator.calculate(PriceType.FIXED, new BigDecimal("800"), 60, new BigDecimal("5"));

        assertThat(p.servicePrice()).isEqualByComparingTo("800.00");
        assertThat(p.platformFee()).isEqualByComparingTo("40.00");
        assertThat(p.totalAmount()).isEqualByComparingTo("840.00");
    }

    @Test
    void startingFromIsChargedAsListed() {
        PriceBreakdown p = PriceCalculator.calculate(PriceType.STARTING_FROM, new BigDecimal("1200"), 120, new BigDecimal("5"));

        assertThat(p.servicePrice()).isEqualByComparingTo("1200.00");
        assertThat(p.totalAmount()).isEqualByComparingTo("1260.00");
    }

    @Test
    void hourlyPriceScalesWithDuration() {
        PriceBreakdown p = PriceCalculator.calculate(PriceType.HOURLY, new BigDecimal("600"), 90, new BigDecimal("5"));

        assertThat(p.servicePrice()).isEqualByComparingTo("900.00");
        assertThat(p.platformFee()).isEqualByComparingTo("45.00");
        assertThat(p.totalAmount()).isEqualByComparingTo("945.00");
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        PriceBreakdown p = PriceCalculator.calculate(PriceType.FIXED, new BigDecimal("99.99"), 60, new BigDecimal("5"));

        assertThat(p.platformFee()).isEqualByComparingTo("5.00"); // 4.9995 rounds up
        assertThat(p.totalAmount()).isEqualByComparingTo("104.99");
    }

    @Test
    void zeroFeeGivesTotalEqualToPrice() {
        PriceBreakdown p = PriceCalculator.calculate(PriceType.FIXED, new BigDecimal("500"), 60, BigDecimal.ZERO);

        assertThat(p.platformFee()).isEqualByComparingTo("0.00");
        assertThat(p.totalAmount()).isEqualByComparingTo("500.00");
    }
}
