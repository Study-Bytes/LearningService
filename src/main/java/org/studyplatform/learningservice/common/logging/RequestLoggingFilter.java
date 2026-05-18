package org.studyplatform.learningservice.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startedAt = System.nanoTime();
        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = failure == null ? response.getStatus() : resolveErrorStatus(response);
            logRequest(request, status, durationMs, failure);
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
    }

    private int resolveErrorStatus(HttpServletResponse response) {
        return response.getStatus() >= 400 ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    private void logRequest(HttpServletRequest request, int status, long durationMs, Exception failure) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String remote = request.getRemoteAddr();

        if (failure != null) {
            log.error(
                    "HTTP {} {} -> {} durationMs={} remote={} failure={}",
                    method,
                    path,
                    status,
                    durationMs,
                    remote,
                    failure.toString()
            );
        } else if (status >= 400) {
            log.warn("HTTP {} {} -> {} durationMs={} remote={}", method, path, status, durationMs, remote);
        } else {
            log.info("HTTP {} {} -> {} durationMs={} remote={}", method, path, status, durationMs, remote);
        }
    }
}
