package com.example.marketplace.common.exception;

import com.example.marketplace.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized exception handling.
 * Browser requests get the "error/error" Thymeleaf page; API/AJAX requests get JSON.
 * Stack traces and internal messages are never exposed to the client.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Object handleAppException(AppException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Application error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.debug("Handled {} on {} {}: {}", ex.getClass().getSimpleName(),
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
        Map<String, String> errors = (ex instanceof ValidationException ve)
                ? ve.getFieldErrors()
                : Collections.emptyMap();
        return respond(request, ex.getStatus(), ex.getMessage(), errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "Validation failed.",
                toFieldErrors(ex.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public Object handleBind(BindException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "Validation failed.",
                toFieldErrors(ex.getBindingResult()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return respond(request, HttpStatus.BAD_REQUEST, "Validation failed.", errors);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public Object handleBadRequest(Exception ex, HttpServletRequest request) {
        log.debug("Bad request on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName());
        return respond(request, HttpStatus.BAD_REQUEST, "The request was invalid.", Collections.emptyMap());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND,
                "The page you requested could not be found.", Collections.emptyMap());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.METHOD_NOT_ALLOWED,
                "This action is not supported.", Collections.emptyMap());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.PAYLOAD_TOO_LARGE,
                "The uploaded file is too large.", Collections.emptyMap());
    }

    @ExceptionHandler(ErrorResponseException.class)
    public Object handleErrorResponse(ErrorResponseException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = (ex instanceof ResponseStatusException rse && rse.getReason() != null)
                ? rse.getReason()
                : status.getReasonPhrase();
        return respond(request, status, message, Collections.emptyMap());
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        // Let Spring Security's own handling (login redirect / 403 page) take over.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again later.", Collections.emptyMap());
    }

    private Object respond(HttpServletRequest request, HttpStatus status,
                           String message, Map<String, String> errors) {
        if (wantsJson(request)) {
            return ResponseEntity.status(status).body(ApiResponse.error(message, errors));
        }
        ModelAndView mav = new ModelAndView("error/error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("error", status.getReasonPhrase());
        mav.addObject("message", message);
        mav.addObject("errors", errors);
        return mav;
    }

    private boolean wantsJson(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null
                && accept.contains(MediaType.APPLICATION_JSON_VALUE)
                && !accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private Map<String, String> toFieldErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : bindingResult.getFieldErrors()) {
            // Only field name + message; rejected values are never echoed or logged.
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return errors;
    }
}
