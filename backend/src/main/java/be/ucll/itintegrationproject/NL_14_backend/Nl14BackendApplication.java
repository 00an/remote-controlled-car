package be.ucll.itintegrationproject.NL_14_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class Nl14BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(Nl14BackendApplication.class, args);
  }
}
