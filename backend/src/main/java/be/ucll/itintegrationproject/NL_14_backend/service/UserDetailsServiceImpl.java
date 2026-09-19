package be.ucll.itintegrationproject.NL_14_backend.service;

import be.ucll.itintegrationproject.NL_14_backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class UserDetailsServiceImpl implements UserDetailsService, UserDetailsPasswordService {

  private final UserRepository userRepository;

  public UserDetailsServiceImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return new UserDetailsImpl(
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(username)));
  }

  @Override
  public UserDetails updatePassword(UserDetails userDetails, String newPassword) {
    final var user = ((UserDetailsImpl) userDetails).user();
    user.setPassword(newPassword);
    return userDetails;
  }
}
