package com.example.marketplace.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    @Test
    void averageIsRoundedToTwoDecimals() {
        assertThat(ReviewService.average(14, 3)).isEqualTo(4.67);
        assertThat(ReviewService.average(5, 1)).isEqualTo(5.0);
        assertThat(ReviewService.average(9, 2)).isEqualTo(4.5);
    }

    @Test
    void averageOfNoReviewsIsZero() {
        assertThat(ReviewService.average(0, 0)).isEqualTo(0.0);
    }

    @Test
    void displayNameShowsFirstNameAndLastInitial() {
        assertThat(ReviewService.displayName("Farhana Khan")).isEqualTo("Farhana K.");
        assertThat(ReviewService.displayName("  md  rahim   uddin ")).isEqualTo("md U.");
    }

    @Test
    void displayNameHandlesSingleAndMissingNames() {
        assertThat(ReviewService.displayName("Imran")).isEqualTo("Imran");
        assertThat(ReviewService.displayName("   ")).isEqualTo("Customer");
        assertThat(ReviewService.displayName(null)).isEqualTo("Customer");
    }
}
