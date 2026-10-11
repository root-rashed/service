package com.example.marketplace.schedule.dto;

import lombok.Value;

/** A blocked day as shown on the schedule page. */
@Value
public class BlockedDateDto {

    /** ISO date, used as the identifier when removing. */
    String date;
    /** Friendly label, e.g. "Mon, 12 Oct 2026". */
    String label;
    String reason;
}
