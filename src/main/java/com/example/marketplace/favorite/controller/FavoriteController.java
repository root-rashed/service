package com.example.marketplace.favorite.controller;

import com.example.marketplace.favorite.service.FavoriteService;
import com.example.marketplace.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The customer's saved-services page. /favorites/** requires the CUSTOMER role (SecurityConfig). */
@Controller
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/favorites")
    public String list(@AuthenticationPrincipal AppUserDetails user,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("favorites", favoriteService.list(user.getId(), page));
        return "favorites/list";
    }
}
