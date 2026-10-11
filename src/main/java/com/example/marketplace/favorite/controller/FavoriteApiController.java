package com.example.marketplace.favorite.controller;

import com.example.marketplace.common.response.ApiResponse;
import com.example.marketplace.favorite.dto.FavoriteState;
import com.example.marketplace.favorite.service.FavoriteService;
import com.example.marketplace.security.AppUserDetails;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/favorites")
@PreAuthorize("hasRole('CUSTOMER')")
public class FavoriteApiController {

    private final FavoriteService favoriteService;

    public FavoriteApiController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{serviceId}/toggle")
    public ApiResponse<FavoriteState> toggle(@AuthenticationPrincipal AppUserDetails user,
                                             @PathVariable String serviceId) {
        return ApiResponse.ok(new FavoriteState(favoriteService.toggle(user.getId(), serviceId)));
    }

    @GetMapping("/state")
    public ApiResponse<List<String>> state(@AuthenticationPrincipal AppUserDetails user,
                                           @RequestParam(defaultValue = "") List<String> ids) {
        return ApiResponse.ok(new ArrayList<>(favoriteService.favoriteIdsAmong(user.getId(), ids)));
    }
}
