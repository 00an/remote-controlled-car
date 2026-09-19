package be.ucll.itintegrationproject.NL_14_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.ucll.itintegrationproject.NL_14_backend.model.Ride;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.repository.RideRepository;
import be.ucll.itintegrationproject.NL_14_backend.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RideControllerHttpTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private RideRepository rideRepository;

  @BeforeEach
  void resetData() {
    rideRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User createUser(String username) {
    return userRepository.save(
        User.builder()
            .username(username)
            .firstName("Test")
            .lastName("User")
            .email(username + "@ucll.be")
            .password("hashed")
            .privacyConsent(true)
            .build());
  }

  private void createRide(User user, int topspeed, int averagespeed, int timespent) {
    rideRepository.save(
        Ride.builder()
            .user(user)
            .topspeed(topspeed)
            .averagespeed(averagespeed)
            .timespent(timespent)
            .build());
  }

  @Test
  void getRideHistoryReturnsOnlyAuthenticatedUsersRides() throws Exception {
    User alice = createUser("alice");
    User bob = createUser("bob");
    createRide(alice, 120, 80, 3600);
    createRide(alice, 100, 70, 1800);
    createRide(bob, 200, 150, 600);

    mockMvc
        .perform(get("/v1/rides").with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                        [
                          {"topspeed": 120, "averagespeed": 80, "timespent": 3600},
                          {"topspeed": 100, "averagespeed": 70, "timespent": 1800}
                        ]"""));
  }

  @Test
  void getRideHistoryReturnsEmptyWhenUserHasNoRides() throws Exception {
    createUser("alice");

    mockMvc
        .perform(get("/v1/rides").with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void getRideHistoryWithoutAuthenticationIsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/rides")).andExpect(status().isUnauthorized());
  }

  @Test
  void createRidePersistsRideForAuthenticatedUser() throws Exception {
    User alice = createUser("alice");

    mockMvc
        .perform(
            post("/v1/rides")
                .with(jwt().jwt(j -> j.subject("alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"topspeed": 150, "averagespeed": 90, "timespent": 2400}"""))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                        {"topspeed": 150, "averagespeed": 90, "timespent": 2400}"""));

    List<Ride> ridesForAlice = rideRepository.findByUserId(alice.getId());
    assertThat(ridesForAlice).hasSize(1);
    assertThat(ridesForAlice.get(0).getTopspeed()).isEqualTo(150);
    assertThat(ridesForAlice.get(0).getAveragespeed()).isEqualTo(90);
    assertThat(ridesForAlice.get(0).getTimespent()).isEqualTo(2400);
  }

  @Test
  void createRideWithoutAuthenticationIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/v1/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"topspeed": 150, "averagespeed": 90, "timespent": 2400}"""))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateRideChangesValuesForAuthenticatedUsersRide() throws Exception {
    User alice = createUser("alice");
    Ride ride =
        rideRepository.save(
            Ride.builder().user(alice).topspeed(100).averagespeed(70).timespent(1800).build());

    mockMvc
        .perform(
            put("/v1/rides/" + ride.getId())
                .with(jwt().jwt(j -> j.subject("alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"topspeed": 220, "averagespeed": 160, "timespent": 999}"""))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                        {"topspeed": 220, "averagespeed": 160, "timespent": 999}"""));

    Ride updated = rideRepository.findById(ride.getId()).orElseThrow();
    assertThat(updated.getTopspeed()).isEqualTo(220);
    assertThat(updated.getAveragespeed()).isEqualTo(160);
    assertThat(updated.getTimespent()).isEqualTo(999);
  }

  @Test
  void updateRideOfAnotherUserReturnsNotFound() throws Exception {
    User alice = createUser("alice");
    createUser("bob");
    Ride aliceRide =
        rideRepository.save(
            Ride.builder().user(alice).topspeed(100).averagespeed(70).timespent(1800).build());

    mockMvc
        .perform(
            put("/v1/rides/" + aliceRide.getId())
                .with(jwt().jwt(j -> j.subject("bob")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"topspeed": 220, "averagespeed": 160, "timespent": 999}"""))
        .andExpect(status().isNotFound());

    Ride unchanged = rideRepository.findById(aliceRide.getId()).orElseThrow();
    assertThat(unchanged.getTopspeed()).isEqualTo(100);
  }

  @Test
  void updateRideWithoutAuthenticationIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            put("/v1/rides/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"topspeed": 220, "averagespeed": 160, "timespent": 999}"""))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteRideRemovesAuthenticatedUsersRide() throws Exception {
    User alice = createUser("alice");
    Ride ride =
        rideRepository.save(
            Ride.builder().user(alice).topspeed(100).averagespeed(70).timespent(1800).build());

    mockMvc
        .perform(delete("/v1/rides/" + ride.getId()).with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk());

    assertThat(rideRepository.findById(ride.getId())).isEmpty();
  }

  @Test
  void deleteRideOfAnotherUserReturnsNotFound() throws Exception {
    User alice = createUser("alice");
    createUser("bob");
    Ride aliceRide =
        rideRepository.save(
            Ride.builder().user(alice).topspeed(100).averagespeed(70).timespent(1800).build());

    mockMvc
        .perform(delete("/v1/rides/" + aliceRide.getId()).with(jwt().jwt(j -> j.subject("bob"))))
        .andExpect(status().isNotFound());

    assertThat(rideRepository.findById(aliceRide.getId())).isPresent();
  }

  @Test
  void deleteRideWithoutAuthenticationIsUnauthorized() throws Exception {
    mockMvc.perform(delete("/v1/rides/1")).andExpect(status().isUnauthorized());
  }
}
