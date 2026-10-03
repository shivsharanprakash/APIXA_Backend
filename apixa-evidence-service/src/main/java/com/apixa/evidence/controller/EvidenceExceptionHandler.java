package com.apixa.evidence.controller;

import com.apixa.common.error.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Step 12 error handling: a malformed / unreadable evidence request is a client error, not a server
 * error. Local to the evidence service (no apixa-common change) and reported with the existing APIXA
 * {@link ErrorBody} model, never exposing a stack trace.
 */
@RestControllerAdvice(basePackageClasses = EvidenceController.class)
public class EvidenceExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ErrorBody(400, "Bad Request",
                "Malformed evidence request body", req.getRequestURI(), System.currentTimeMillis()));
    }
}