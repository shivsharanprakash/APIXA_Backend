package com.apixa.common.error;

public class ApiException extends RuntimeException {
    private final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public ApiException(int status, String message, Throwable cause) { super(message, cause); this.status = status; }
    public int getStatus() { return status; }
    public static ApiException badRequest(String m) { return new ApiException(400, m); }
    public static ApiException notFound(String m) { return new ApiException(404, m); }
    public static ApiException internal(String m, Throwable c) { return new ApiException(500, m, c); }
}
