package be.ucll.itintegrationproject.NL_14_backend;

import be.ucll.itintegrationproject.NL_14_backend.model.Ride;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.repository.RideRepository;
import be.ucll.itintegrationproject.NL_14_backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Seed data for local dev: creates a test user (tobias / password123) with a few rides
@Profile("dev")
@Component
public class DbInitializer {

  private final UserRepository userRepository;
  private final RideRepository rideRepository;
  private final PasswordEncoder passwordEncoder;

  public DbInitializer(
      UserRepository userRepository,
      RideRepository rideRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.rideRepository = rideRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @PostConstruct
  public void init() {
    rideRepository.deleteAll();
    userRepository.deleteAll();

    User tobias =
        userRepository.save(
            User.builder()
                .username("tobias")
                .firstName("Tobias")
                .lastName("Quartier")
                .email("tobias@example.com")
                .password(passwordEncoder.encode("password123"))
                .privacyConsent(true)
                .build());

    rideRepository.save(
        Ride.builder().user(tobias).topspeed(120).averagespeed(80).timespent(3600).build());

    rideRepository.save(
        Ride.builder().user(tobias).topspeed(95).averagespeed(60).timespent(1800).build());

    rideRepository.save(
        Ride.builder().user(tobias).topspeed(140).averagespeed(100).timespent(5400).build());
  }
}
