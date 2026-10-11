package com.example.marketplace.servicecatalog.dto;

import lombok.Value;

/** A service image as shown in pages: id for management actions, url for display. */
@Value
public class ServiceImageDto {

    String id;
    String url;
}
