package be.ucll.itintegrationproject.NL_14_backend.repository;

import be.ucll.itintegrationproject.NL_14_backend.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  boolean existsByUsername(String username);

  Optional<User> findByUsername(String username);
}
