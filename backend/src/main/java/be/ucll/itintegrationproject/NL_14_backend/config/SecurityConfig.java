package be.ucll.itintegrationproject.NL_14_backend.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class})
@EnableMethodSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  @Order(1)
  @Profile("dev")
  public SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher(PathRequest.toH2Console())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
        .build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            authorizeRequests ->
                authorizeRequests
                    .requestMatchers("/v1/status")
                    .permitAll()
                    // Intentionally public: the physical steering wheel (a standalone Python
                    // script) and the ESP32 car both talk to these endpoints with no login flow
                    // of their own, so they can't carry the auth cookie. This assumes a trusted
                    // deployment (single-driver hobby project) rather than a public multi-tenant
                    // service; don't relax this further without adding real device auth first.
                    .requestMatchers("/v1/api/controller/**")
                    .permitAll()
                    .requestMatchers("/ws", "/ws/**")
                    .permitAll()
                    .requestMatchers("/error/**")
                    .permitAll()
                    .requestMatchers("/v1/users/login", "/v1/users/signup", "/v1/users/logout")
                    .permitAll()
                    .requestMatchers("/test-utils/**")
                    .permitAll()
                    .requestMatchers("/v1/api-docs/**", "/v1/api-docs")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .cors(Customizer.withDefaults())
        // CSRF protection is disabled: this API is stateless JSON-over-HTTP consumed by a
        // single-page app (never HTML forms), CORS is locked to an explicit origin allowlist
        // (see CorsProperties), and the auth cookie only carries a bearer JWT read by
        // CookieBearerTokenResolver rather than driving server-side session state. A classic
        // CSRF token exchange would add complexity without addressing a realistic attack here;
        // if this API ever accepts browser form submissions or session-based auth, revisit this.
        .csrf(csrf -> csrf.disable())
        .headers(
            headers -> {
              headers.frameOptions(frameOptions -> frameOptions.deny());
              headers.contentTypeOptions(Customizer.withDefaults());
              headers.httpStrictTransportSecurity(
                  hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(63072000));
              headers.referrerPolicy(
                  referrerPolicy ->
                      referrerPolicy.policy(
                          ReferrerPolicyHeaderWriter.ReferrerPolicy
                              .STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
              headers.addHeaderWriter(
                  new StaticHeadersWriter(
                      "Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
              headers.contentSecurityPolicy(
                  csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"));
            })
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(Customizer.withDefaults())
                    .bearerTokenResolver(new CookieBearerTokenResolver()))
        .build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    final var configuration = new CorsConfiguration();
    final var allowedOrigins = corsProperties.allowedOrigins().stream().map(URL::toString).toList();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setExposedHeaders(List.of("Set-Cookie"));
    configuration.setAllowCredentials(true);
    final var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecretKey secretKey(JwtProperties jwtProperties) throws NoSuchAlgorithmException {
    final var secretKeyProperty = jwtProperties.secretKey();
    if (secretKeyProperty == null || secretKeyProperty.isEmpty()) {
      log.warn("No secret key configured, generating a random key");
      final var secretKeyGenerator = KeyGenerator.getInstance("AES");
      return secretKeyGenerator.generateKey();
    } else {
      final var bytes = Base64.getDecoder().decode(jwtProperties.secretKey());
      return new SecretKeySpec(bytes, "AES");
    }
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey secretKey) {
    return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey secretKey) {
    final JWK jwk = new OctetSequenceKey.Builder(secretKey).algorithm(JWSAlgorithm.HS256).build();
    final var jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
    return new NimbusJwtEncoder(jwks);
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }
}
