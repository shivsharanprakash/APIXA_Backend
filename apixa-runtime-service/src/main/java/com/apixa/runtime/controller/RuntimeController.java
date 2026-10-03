package com.apixa.runtime.controller;

import com.apixa.runtime.model.RuntimeResult;
import com.apixa.runtime.model.RuntimeVerifyRequest;
import com.apixa.runtime.service.RuntimeVerificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step 13 runtime verification API (port 8087).
 *
 * <p>{@code POST /api/runtime/verify} describes and executes exactly ONE HTTP request against a running
 * target API and returns what was observed. Batch test suites are deliberately out of scope for this
 * step, so no other routes exist.
 *
 * <p>A target that answers 401 / 403 / 404 is a successful <b>verification</b>: the service answers
 * {@code 200} with {@code status=COMPLETED} and the observed status code. Only a malformed request or
 * an unreachable target produces a different shape.
 */
@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {

    private final RuntimeVerificationService service;

    public RuntimeController(RuntimeVerificationService service) { this.service = service; }

    @PostMapping("/verify")
    public RuntimeResult verify(@RequestBody RuntimeVerifyRequest request) {
        return service.verify(request);
    }
}