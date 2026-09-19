package be.ucll.itintegrationproject.NL_14_backend.service;

import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.AuthenticationResponse;
import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.UserInput;
import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.UserUpdateInput;
import be.ucll.itintegrationproject.NL_14_backend.exception.PrivacyConsentException;
import be.ucll.itintegrationproject.NL_14_backend.exception.UserAlreadyExistsException;
import be.ucll.itintegrationproject.NL_14_backend.exception.UserNotFoundException;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;

  public UserService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuthenticationManager authenticationManager,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManager = authenticationManager;
    this.jwtService = jwtService;
  }

  @Cacheable("users")
  public List<User> getAllUsers() {
    return userRepository.findAll();
  }

  public Optional<User> getUserByUsername(String username) {
    return userRepository.findByUsername(username);
  }

  public Optional<User> getUserById(Long id) {
    return userRepository.findById(id);
  }

  public AuthenticationResponse authenticate(String username, String password) {
    final var usernamePasswordAuthentication =
        new UsernamePasswordAuthenticationToken(username, password);
    final var authentication = authenticationManager.authenticate(usernamePasswordAuthentication);
    final var user = ((UserDetailsImpl) authentication.getPrincipal()).user();
    final var token = jwtService.generateToken(user);
    return new AuthenticationResponse(
        "Authentication successful.", token, user.getUsername(), user.getFullName());
  }

  @CacheEvict(value = "users", allEntries = true)
  public User signup(UserInput userInput) {
    if (userRepository.existsByUsername(userInput.username())) {
      throw new UserAlreadyExistsException(userInput.username());
    }

    if (!userInput.privacyConsent()) {
      throw new PrivacyConsentException();
    }

    final var hashedPassword = passwordEncoder.encode(userInput.password());
    final var user =
        User.builder()
            .username(userInput.username())
            .firstName(userInput.firstName())
            .lastName(userInput.lastName())
            .email(userInput.email())
            .password(hashedPassword)
            .privacyConsent(true)
            .build();

    return userRepository.save(user);
  }

  @CacheEvict(value = "users", allEntries = true)
  public User updateUser(String username, UserUpdateInput userInput) {
    final var user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));
    if (userInput.firstName() != null) user.setFirstName(userInput.firstName());
    if (userInput.lastName() != null) user.setLastName(userInput.lastName());
    if (userInput.email() != null) user.setEmail(userInput.email());
    return userRepository.save(user);
  }

  @CacheEvict(value = "users", allEntries = true)
  public void deleteUserByUsername(String username) {
    final var user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));
    userRepository.delete(user);
  }
}
