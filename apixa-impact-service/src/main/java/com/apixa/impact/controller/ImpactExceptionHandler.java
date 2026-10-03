package com.apixa.impact.controller;

import com.apixa.common.error.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Step 14 error handling: a malformed / unreadable impact request is a client error, reported with the
 * existing APIXA {@link ErrorBody} model and without exposing a stack trace. Local to the impact
 * service — no apixa-common change.
 */
@RestControllerAdvice(basePackageClasses = ImpactController.class)
public class ImpactExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ErrorBody(400, "Bad Request",
                "Malformed impact request body", req.getRequestURI(), System.currentTimeMillis()));
    }
}