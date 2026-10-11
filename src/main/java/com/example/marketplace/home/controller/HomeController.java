package com.example.marketplace.home.controller;

import com.example.marketplace.home.service.HomePageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;



@Controller
public class HomeController {


    private final HomePageService homePageService;
    public HomeController(HomePageService homePageService){
        this.homePageService = homePageService;
    }



    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("home", homePageService.getHomePage());
        model.addAttribute("demoContent", homePageService.isDemoContent());
        model.addAttribute("activeNav", "home");
        return "home/index";
    }
}
