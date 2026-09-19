package be.ucll.itintegrationproject.NL_14_backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secretKey, String cookieDomain, @DefaultValue Token token) {
  public record Token(
      @DefaultValue("courses_app") String issuer, @DefaultValue("8h") Duration lifetime) {}
}
