package be.ucll.itintegrationproject.NL_14_backend.service;

import be.ucll.itintegrationproject.NL_14_backend.config.JwtProperties;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class JwtService {

  private final JwtProperties jwtProperties;
  private final JwtEncoder jwtEncoder;

  public JwtService(JwtProperties jwtProperties, JwtEncoder jwtEncoder) {
    this.jwtProperties = jwtProperties;
    this.jwtEncoder = jwtEncoder;
  }

  public String generateToken(String username) {
    final var now = Instant.now();
    final var expiresAt = now.plus(jwtProperties.token().lifetime());
    final var header = JwsHeader.with(MacAlgorithm.HS256).build();
    final var claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.token().issuer())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(username)
            .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public String generateToken(User user) {
    return generateToken(user.getUsername());
  }
}
