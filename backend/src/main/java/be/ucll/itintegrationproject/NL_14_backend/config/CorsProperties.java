package be.ucll.itintegrationproject.NL_14_backend.config;

import java.net.URL;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(@DefaultValue("http://localhost:8080") List<URL> allowedOrigins) {}
