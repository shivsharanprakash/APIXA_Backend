package com.apixa.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(int status, String error, String message, String path, long timestamp) {}
