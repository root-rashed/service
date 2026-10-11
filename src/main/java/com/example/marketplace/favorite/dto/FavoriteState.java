package com.example.marketplace.favorite.dto;

import lombok.Value;

/** Result of a toggle: the state the service is in now. */
@Value
public class FavoriteState {

    boolean favorite;
}
