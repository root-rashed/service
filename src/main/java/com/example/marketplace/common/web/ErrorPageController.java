package com.example.marketplace.common.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Renders dedicated pages for the status codes that deserve a real explanation.
 *
 * Spring Boot forwards any unhandled error to /error; this controller picks the friendly
 * template for the codes we care about and falls back to error/error for anything else.
 * Nothing here is reachable directly by a browser: /error itself is not a public route
 * (SecurityConfig denies it), and dispatching always goes through the servlet error path.
 */
@Controller
public class ErrorPageController implements ErrorController {

    @RequestMapping("/error")
    public String error(HttpServletRequest request, Model model) {
        int status = statusOf(request);
        HttpStatus resolved = HttpStatus.resolve(status);

        if (resolved == HttpStatus.NOT_FOUND) {
            return render("error/404", request, model);
        }
        if (resolved == HttpStatus.FORBIDDEN || resolved == HttpStatus.UNAUTHORIZED) {
            return render("error/403", request, model);
        }
        if (resolved != null && resolved.is5xxServerError()) {
            return render("error/500", request, model);
        }
        // 400 and anything unexpected: the generic page, which shows the supplied message.
        model.addAttribute("status", status);
        model.addAttribute("error", resolved == null ? "Error" : resolved.getReasonPhrase());
        model.addAttribute("message", request.getAttribute(RequestDispatcher.ERROR_MESSAGE));
        model.addAttribute("errors", Map.of());
        return "error/error";
    }

    /** Never leaks internals: the request path is the only detail echoed back. */
    private String render(String view, HttpServletRequest request, Model model) {
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        model.addAttribute("status", statusOf(request));
        model.addAttribute("error", HttpStatus.resolve(statusOf(request)) == null
                ? "Error" : HttpStatus.resolve(statusOf(request)).getReasonPhrase());
        // Server-side exception messages are not shown on 5xx pages.
        model.addAttribute("message", statusOf(request) >= 500 ? null : message);
        model.addAttribute("path", request.getRequestURI());
        return view;
    }

    private int statusOf(HttpServletRequest request) {
        Object attr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (attr instanceof Integer code) {
            return code;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}
