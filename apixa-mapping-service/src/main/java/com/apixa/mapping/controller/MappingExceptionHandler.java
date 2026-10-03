package com.apixa.mapping.controller;

import com.apixa.common.error.ApiException;
import com.apixa.common.error.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Step 10 error handling: a malformed / unreadable mapping request body is a client error, not a
 * server error. Kept local to the mapping service (no shared apixa-common change) and reported with
 * the existing APIXA {@link ErrorBody} model, without exposing any stack trace.
 */
@RestControllerAdvice(basePackageClasses = MappingController.class)
public class MappingExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ErrorBody(400, "Bad Request",
                "Malformed mapping request body", req.getRequestURI(), System.currentTimeMillis()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> mismatch(Exception ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ErrorBody(400, "Bad Request",
                "Invalid mapping request parameter", req.getRequestURI(), System.currentTimeMillis()));
    }
}