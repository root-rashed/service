package com.example.marketplace.servicecatalog.dto;

import com.example.marketplace.worker.dto.WorkerProfileDto;
import lombok.Value;

/** Everything the public service detail page needs, cached as one unit. */
@Value
public class ServiceDetailView {

    ServiceListingDto service;
    WorkerProfileDto worker;
    String categorySlug;
}
