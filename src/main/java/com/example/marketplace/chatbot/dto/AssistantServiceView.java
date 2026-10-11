package com.example.marketplace.chatbot.dto;

import com.example.marketplace.servicecatalog.dto.ServiceListingDto;
import java.math.BigDecimal;

/**
 * What the assistant is allowed to tell the customer, given back to it as structured data.
 *
 * <p>Every field here is public catalog information that already appears on the site. There is
 * deliberately no availability field and no contact field: those live behind the booking page, and
 * an assistant that can quote them would eventually quote something wrong. When availability is
 * asked for, {@link #availabilityNote()} is what the model is told to say.
 */
public record AssistantServiceView(
        String title,
        String slug,
        String category,
        String professional,
        String area,
        String price,
        String duration,
        double rating,
        int reviewCount) {

    /** Builds a view from a catalog listing, formatted the way the site shows it. */
    public static AssistantServiceView of(ServiceListingDto service, String professional) {
        return new AssistantServiceView(
                service.getTitle(),
                service.getSlug(),
                service.getCategoryName(),
                professional,
                service.getLocation(),
                money(service.getPrice()),
                duration(service),
                service.getRating(),
                service.getReviewCount());
    }

    /** Builds a view without a professional name, for when the lookup did not find one. */
    public static AssistantServiceView of(ServiceListingDto service) {
        return of(service, "not published");
    }

    private static String money(BigDecimal price) {
        return price == null ? "on request" : "BDT " + price.stripTrailingZeros().toPlainString();
    }

    private static String duration(ServiceListingDto service) {
        if (service.getDurationMinutes() <= 0) {
            return "not specified";
        }
        int hours = service.getDurationMinutes() / 60;
        int minutes = service.getDurationMinutes() % 60;
        if (hours == 0) {
            return minutes + " min";
        }
        return minutes == 0 ? hours + " hour" + (hours == 1 ? "" : "s") : hours + "h " + minutes + "m";
    }
}