package be.ucll.itintegrationproject.NL_14_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.UserInput;
import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.UserUpdateInput;
import be.ucll.itintegrationproject.NL_14_backend.exception.PrivacyConsentException;
import be.ucll.itintegrationproject.NL_14_backend.exception.UserAlreadyExistsException;
import be.ucll.itintegrationproject.NL_14_backend.exception.UserNotFoundException;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private AuthenticationManager authenticationManager;
  private JwtService jwtService;
  private UserService userService;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    authenticationManager = mock(AuthenticationManager.class);
    jwtService = mock(JwtService.class);
    userService =
        new UserService(userRepository, passwordEncoder, authenticationManager, jwtService);
  }

  @Test
  void signupSavesUserWithEncodedPassword() {
    UserInput input =
        new UserInput("alice", "password123", "Alice", "Smith", "alice@ucll.be", true);
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.signup(input);

    assertThat(result.getUsername()).isEqualTo("alice");
    assertThat(result.getPassword()).isEqualTo("hashed");
    verify(userRepository).save(any(User.class));
  }

  @Test
  void signupThrowsWhenUsernameAlreadyExists() {
    UserInput input =
        new UserInput("alice", "password123", "Alice", "Smith", "alice@ucll.be", true);
    when(userRepository.existsByUsername("alice")).thenReturn(true);

    assertThatThrownBy(() -> userService.signup(input))
        .isInstanceOf(UserAlreadyExistsException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void signupThrowsWhenPrivacyConsentNotGiven() {
    UserInput input =
        new UserInput("alice", "password123", "Alice", "Smith", "alice@ucll.be", false);
    when(userRepository.existsByUsername("alice")).thenReturn(false);

    assertThatThrownBy(() -> userService.signup(input)).isInstanceOf(PrivacyConsentException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void authenticateReturnsResponseWithToken() {
    User user = new User();
    user.setUsername("alice");
    user.setFirstName("Alice");
    user.setLastName("Smith");

    Authentication auth = mock(Authentication.class);
    when(auth.getPrincipal()).thenReturn(new UserDetailsImpl(user));
    when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .thenReturn(auth);
    when(jwtService.generateToken(user)).thenReturn("fake-token");

    var response = userService.authenticate("alice", "password123");

    assertThat(response.token()).isEqualTo("fake-token");
    assertThat(response.username()).isEqualTo("alice");
  }

  @Test
  void authenticateThrowsWhenPrincipalIsNotUserDetailsImpl() {
    Authentication auth = mock(Authentication.class);
    when(auth.getPrincipal()).thenReturn("not-user-details");
    when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .thenReturn(auth);

    assertThatThrownBy(() -> userService.authenticate("alice", "password123"))
        .isInstanceOf(ClassCastException.class);
  }

  @Test
  void authenticateThrowsWhenAuthenticationFails() {
    when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .thenThrow(new AuthenticationException("Bad credentials") {});

    assertThatThrownBy(() -> userService.authenticate("alice", "wrong-password"))
        .isInstanceOf(AuthenticationException.class);

    verify(jwtService, never()).generateToken(any(User.class));
  }

  @Test
  void updateUserChangesProfileFields() {
    User user = new User();
    user.setUsername("alice");
    user.setFirstName("Old");
    user.setLastName("Name");
    user.setEmail("old@ucll.be");

    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result =
        userService.updateUser("alice", new UserUpdateInput("New", "Updated", "new@ucll.be"));

    assertThat(result.getFirstName()).isEqualTo("New");
    assertThat(result.getLastName()).isEqualTo("Updated");
    assertThat(result.getEmail()).isEqualTo("new@ucll.be");
    verify(userRepository).save(user);
  }

  @Test
  void updateUserWithPartialInputKeepsExistingValuesForNullFields() {
    User user = new User();
    user.setUsername("alice");
    user.setFirstName("Old");
    user.setLastName("Name");
    user.setEmail("old@ucll.be");

    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    User result = userService.updateUser("alice", new UserUpdateInput("New", null, null));

    assertThat(result.getFirstName()).isEqualTo("New");
    assertThat(result.getLastName()).isEqualTo("Name");
    assertThat(result.getEmail()).isEqualTo("old@ucll.be");
    verify(userRepository).save(user);
  }

  @Test
  void updateUserThrowsWhenUserNotFound() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> userService.updateUser("alice", new UserUpdateInput("A", "B", "a@b.be")))
        .isInstanceOf(UserNotFoundException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void deleteUserRemovesFromRepository() {
    User user = new User();
    user.setUsername("alice");
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

    userService.deleteUserByUsername("alice");

    verify(userRepository).delete(user);
  }

  @Test
  void deleteUserThrowsWhenUserNotFound() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.deleteUserByUsername("alice"))
        .isInstanceOf(UserNotFoundException.class);
    verify(userRepository, never()).delete(any());
  }
}
