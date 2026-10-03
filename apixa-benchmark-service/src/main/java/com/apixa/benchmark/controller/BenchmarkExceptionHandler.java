package com.apixa.benchmark.controller;

import com.apixa.common.error.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Step 15 error handling, local to the benchmark service (no apixa-common change).
 *
 * <p>A malformed run request is a client error. An internal execution failure is reported as a controlled
 * 500 that names the failure class only — a stack trace is never returned to the API client.
 */
@RestControllerAdvice(basePackageClasses = BenchmarkController.class)
public class BenchmarkExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ErrorBody(400, "Bad Request",
                "Malformed benchmark request body", req.getRequestURI(), System.currentTimeMillis()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> benchmarkFailure(IllegalStateException ex, HttpServletRequest req) {
        return ResponseEntity.status(500).body(new ErrorBody(500, "Internal Server Error",
                "Benchmark execution failed: " + ex.getMessage(),
                req.getRequestURI(), System.currentTimeMillis()));
    }
}