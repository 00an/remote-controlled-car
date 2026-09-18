package be.ucll.itintegrationproject.NL_14_backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class RequestLoggingFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    String requestId = UUID.randomUUID().toString().substring(0, 8);
    long start = System.currentTimeMillis();

    MDC.put("requestId", requestId);
    MDC.put("method", req.getMethod());
    MDC.put("uri", req.getRequestURI());

    try {
      chain.doFilter(request, response);
    } finally {
      long duration = System.currentTimeMillis() - start;
      MDC.put("status", String.valueOf(res.getStatus()));
      MDC.put("durationMs", String.valueOf(duration));

      if (res.getStatus() >= 400) {
        log.warn(
            "HTTP {} {} -> {} ({}ms)",
            req.getMethod(),
            req.getRequestURI(),
            res.getStatus(),
            duration);
      } else {
        log.info(
            "HTTP {} {} -> {} ({}ms)",
            req.getMethod(),
            req.getRequestURI(),
            res.getStatus(),
            duration);
      }

      MDC.clear();
    }
  }
}
