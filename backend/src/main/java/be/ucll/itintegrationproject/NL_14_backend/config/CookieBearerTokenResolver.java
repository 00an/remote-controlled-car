package be.ucll.itintegrationproject.NL_14_backend.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

// Reads the JWT token from the HttpOnly cookie instead of only from the Authorization header
public class CookieBearerTokenResolver implements BearerTokenResolver {

  private final BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    String tokenFromHeader = defaultResolver.resolve(request);
    if (tokenFromHeader != null) return tokenFromHeader;

    if (request.getCookies() == null) return null;

    return Arrays.stream(request.getCookies())
        .filter(cookie -> "authToken".equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
