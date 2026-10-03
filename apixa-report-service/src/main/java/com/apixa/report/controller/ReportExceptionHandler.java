package com.apixa.report.controller;

import com.apixa.common.error.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Step 16 error handling, local to the report service.
 *
 * <p>A missing body, a malformed body and an empty report are client errors (400) and an unknown report
 * id is a 404. No stack trace is ever returned to the API client.
 */
@RestControllerAdvice(basePackageClasses = ReportController.class)
public class ReportExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ErrorBody(400, "Bad Request",
                "Malformed report request body", req.getRequestURI(), System.currentTimeMillis()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> renderingFailure(IllegalStateException ex, HttpServletRequest req) {
        return ResponseEntity.status(500).body(new ErrorBody(500, "Internal Server Error",
                "Report rendering failed: " + ex.getMessage(), req.getRequestURI(), System.currentTimeMillis()));
    }
}