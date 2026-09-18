package be.ucll.itintegrationproject.NL_14_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  // Prepends /v1 to every @RestController. Doesn't apply to @Controller (such as the Swagger UI redirect)
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix("/v1", HandlerTypePredicate.forAnnotation(RestController.class));
  }
}
