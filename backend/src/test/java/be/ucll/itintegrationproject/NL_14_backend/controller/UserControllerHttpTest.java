package be.ucll.itintegrationproject.NL_14_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.repository.RideRepository;
import be.ucll.itintegrationproject.NL_14_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerHttpTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private RideRepository rideRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void resetData() {
    rideRepository.deleteAll();
    userRepository.deleteAll();
  }

  private void createUser(String username) {
    createUser(username, "defaultPassword");
  }

  private void createUser(String username, String rawPassword) {
    userRepository.save(
        User.builder()
            .username(username)
            .firstName("Test")
            .lastName("User")
            .email(username + "@ucll.be")
            .password(passwordEncoder.encode(rawPassword))
            .privacyConsent(true)
            .build());
  }

  // READ

  @Test
  void getUsersReturnsAllUsersWhenAuthenticated() throws Exception {
    createUser("alice");
    createUser("bob");

    mockMvc
        .perform(get("/v1/users").with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[*].username", containsInAnyOrder("alice", "bob")));
  }

  @Test
  void getUsersWithoutAuthenticationIsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/users")).andExpect(status().isUnauthorized());
  }

  // CREATE (signup)

  @Test
  void signupCreatesNewUser() throws Exception {
    mockMvc
        .perform(
            post("/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"username":"alice","password":"password123","firstName":"Alice","lastName":"Smith","email":"alice@ucll.be","privacyConsent":true}"""))
        .andExpect(status().isCreated())
        .andExpect(
            content()
                .json(
                    """
                        {"username":"alice","firstName":"Alice","lastName":"Smith","email":"alice@ucll.be"}"""));

    assertThat(userRepository.findByUsername("alice")).isPresent();
  }

  @Test
  void signupWithDuplicateUsernameIsConflict() throws Exception {
    createUser("alice");

    mockMvc
        .perform(
            post("/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"username":"alice","password":"password123","firstName":"Alice","lastName":"Smith","email":"alice@ucll.be","privacyConsent":true}"""))
        .andExpect(status().isConflict());
  }

  @Test
  void signupWithoutPrivacyConsentIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"username":"alice","password":"password123","firstName":"Alice","lastName":"Smith","email":"alice@ucll.be","privacyConsent":false}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void signupWithBlankUsernameIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"username":"","password":"password123","firstName":"Alice","lastName":"Smith","email":"alice@ucll.be","privacyConsent":true}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void signupWithInvalidEmailIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"username":"alice","password":"password123","firstName":"Alice","lastName":"Smith","email":"not-an-email","privacyConsent":true}"""))
        .andExpect(status().isBadRequest());
  }

  // LOGIN / LOGOUT

  @Test
  void loginWithValidCredentialsSetsAuthCookie() throws Exception {
    createUser("alice", "password123");

    mockMvc
        .perform(
            post("/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"username":"alice","password":"password123"}"""))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("authToken"))
        .andExpect(cookie().httpOnly("authToken", true))
        .andExpect(cookie().secure("authToken", true))
        .andExpect(
            content()
                .json(
                    """
                        {"username":"alice"}"""));
  }

  @Test
  void loginWithInvalidCredentialsIsUnauthorized() throws Exception {
    createUser("alice", "password123");

    mockMvc
        .perform(
            post("/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"username":"alice","password":"wrong"}"""))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutClearsAuthCookie() throws Exception {
    mockMvc
        .perform(post("/v1/users/logout"))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge("authToken", 0));
  }

  // UPDATE

  @Test
  void updateCurrentUserChangesProfileFields() throws Exception {
    createUser("alice");

    mockMvc
        .perform(
            put("/v1/users/me")
                .with(jwt().jwt(j -> j.subject("alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"firstName": "Alicia", "lastName": "Updated", "email": "alicia@ucll.be"}"""))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                        {"firstName": "Alicia", "lastName": "Updated", "email": "alicia@ucll.be"}"""));

    User updated = userRepository.findByUsername("alice").orElseThrow();
    assertThat(updated.getFirstName()).isEqualTo("Alicia");
    assertThat(updated.getLastName()).isEqualTo("Updated");
    assertThat(updated.getEmail()).isEqualTo("alicia@ucll.be");
  }

  @Test
  void updateCurrentUserWithBlankFirstNameIsBadRequest() throws Exception {
    createUser("alice");

    mockMvc
        .perform(
            put("/v1/users/me")
                .with(jwt().jwt(j -> j.subject("alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"firstName": "", "lastName": "Updated", "email": "alicia@ucll.be"}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateCurrentUserWithoutAuthenticationIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            put("/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"firstName": "Alicia", "lastName": "Updated", "email": "alicia@ucll.be"}"""))
        .andExpect(status().isUnauthorized());
  }

  // DELETE

  @Test
  void deleteCurrentUserRemovesUserAndClearsCookie() throws Exception {
    createUser("alice");

    mockMvc
        .perform(delete("/v1/users/me").with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge("authToken", 0));

    assertThat(userRepository.findByUsername("alice")).isEmpty();
  }

  @Test
  void deleteCurrentUserWithoutAuthenticationIsUnauthorized() throws Exception {
    mockMvc.perform(delete("/v1/users/me")).andExpect(status().isUnauthorized());
  }
}
