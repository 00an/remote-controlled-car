package be.ucll.itintegrationproject.NL_14_backend.controller;

import be.ucll.itintegrationproject.NL_14_backend.config.JwtProperties;
import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.AuthenticationRequest;
import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.AuthenticationResponse;
import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.UserInput;
import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.UserUpdateInput;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.apache.tomcat.util.http.SameSiteCookies;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

  private final UserService userService;
  private final JwtProperties jwtProperties;

  public UserController(UserService userService, JwtProperties jwtProperties) {
    this.userService = userService;
    this.jwtProperties = jwtProperties;
  }

  @GetMapping
  public List<User> getUsers() {
    return userService.getAllUsers();
  }

  @PostMapping("/login")
  public ResponseEntity<Object> authenticate(
      @RequestBody AuthenticationRequest authenticationRequest, HttpServletResponse response) {
    var auth =
        userService.authenticate(
            authenticationRequest.username(), authenticationRequest.password());

    writeAuthCookie(response, auth.token(), 3600);

    auth = new AuthenticationResponse(auth.message(), null, auth.username(), auth.fullname());
    return ResponseEntity.ok(auth);
  }

  @PostMapping("/logout")
  public ResponseEntity<Object> logout(HttpServletResponse response) {
    writeAuthCookie(response, "", 0);
    return ResponseEntity.ok(Map.of("message", "Logout successful"));
  }

  @PostMapping("/signup")
  public ResponseEntity<User> signup(@Valid @RequestBody UserInput userInput) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.signup(userInput));
  }

  @PutMapping("/me")
  public User updateCurrentUser(
      @Valid @RequestBody UserUpdateInput userInput, Authentication authentication) {
    return userService.updateUser(authentication.getName(), userInput);
  }

  @DeleteMapping("/me")
  public ResponseEntity<Object> deleteCurrentUser(
      Authentication authentication, HttpServletResponse response) {
    userService.deleteUserByUsername(authentication.getName());
    writeAuthCookie(response, "", 0);
    return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
  }

  private void writeAuthCookie(HttpServletResponse response, String value, long maxAge) {
    ResponseCookie cookie =
        ResponseCookie.from("authToken", value)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .domain(jwtProperties.cookieDomain())
            .maxAge(maxAge)
            .sameSite(SameSiteCookies.NONE.toString())
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
