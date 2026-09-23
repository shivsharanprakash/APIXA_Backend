package com.apixa.common.web;

import com.apixa.common.trace.TraceContext;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;

public class TraceIdFilter implements Filter {
    public static final String HEADER = "X-Trace-Id";
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) { traceId = UUID.randomUUID().toString(); }
        TraceContext.set(traceId);
        response.setHeader(HEADER, traceId);
        try { chain.doFilter(req, res); } finally { TraceContext.clear(); }
    }
}
