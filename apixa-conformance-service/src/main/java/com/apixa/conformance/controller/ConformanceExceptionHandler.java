package com.apixa.conformance.controller;

import com.apixa.common.error.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Step 11 error handling: a malformed / unreadable conformance request is a client error, not a server
 * error. Kept local to the conformance service (no apixa-common change) and reported with the existing
 * APIXA {@link ErrorBody} model, never exposing a stack trace.
 */
@RestControllerAdvice(basePackageClasses = ConformanceController.class)
public class ConformanceExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        org.slf4j.LoggerFactory.getLogger(ConformanceExceptionHandler.class)
                .error("conformance body parse failure on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorBody(400, "Bad Request",
                "Malformed conformance request body. Note: SecurityPolicy booleans 'permitAll' and "
                        + "'unknown' are primitives and must both be present.", req.getRequestURI(), System.currentTimeMillis()));
    }
}