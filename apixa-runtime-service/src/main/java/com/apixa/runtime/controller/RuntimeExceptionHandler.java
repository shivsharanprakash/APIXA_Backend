package com.apixa.runtime.controller;

import com.apixa.common.error.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Step 13 error handling: a malformed / unreadable runtime request is a client error. Local to the
 * runtime service (no apixa-common change), reported with the existing APIXA {@link ErrorBody} model
 * and without exposing a stack trace.
 */
@RestControllerAdvice(basePackageClasses = RuntimeController.class)
public class RuntimeExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ErrorBody(400, "Bad Request",
                "Malformed runtime verification request body", req.getRequestURI(),
                System.currentTimeMillis()));
    }
}