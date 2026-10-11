package com.example.marketplace.admin.controller;

import com.example.marketplace.admin.dto.AdminUserDto;
import com.example.marketplace.admin.service.AdminUserService;
import com.example.marketplace.admin.web.AdminFilters;
import com.example.marketplace.common.enums.Role;
import com.example.marketplace.common.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Account administration: the account directory plus suspend / restore actions.
 *
 * The controller only reads the query string and hands it to the service; every rule about what
 * may be suspended lives in {@link AdminUserService}.
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String role,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<AdminUserDto> users = adminUserService.search(q, role, status, page);
        model.addAttribute("users", users);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("role", role == null ? "" : role);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("roles", Role.values());
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("filterQuery", AdminFilters.query("q", q, "role", role, "status", status));
        model.addAttribute("activeSection", "users");
        return "admin/users";
    }

    /** Keeps the current filters when the list is re-rendered after an action. */
    @GetMapping("/{id}")
    public String detail(@PathVariable String id,
                         @RequestParam(required = false) String back,
                         Model model) {
        model.addAttribute("account", adminUserService.get(id));
        model.addAttribute("back", back == null ? "/admin/users" : back);
        model.addAttribute("activeSection", "users");
        return "admin/user-detail";
    }

    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable String id,
                          @RequestParam(required = false) String back,
                          RedirectAttributes flash) {
        adminUserService.suspend(id);
        flash.addFlashAttribute("successMessage", "Account suspended. The user can no longer sign in.");
        return redirectTo(back);
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable String id,
                           @RequestParam(required = false) String back,
                           RedirectAttributes flash) {
        adminUserService.activate(id);
        flash.addFlashAttribute("successMessage", "Account restored and any login lock cleared.");
        return redirectTo(back);
    }

    /**
     * The {@code back} parameter is only ever a path this area generated itself; anything that
     * is not an {@code /admin/...} path is ignored so the parameter can never be used to build
     * an open redirect.
     */
    private String redirectTo(String back) {
        if (back != null && back.startsWith("/admin/") && !back.startsWith("//")) {
            return back;
        }
        return "/admin/users";
    }
}
