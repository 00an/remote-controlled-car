package be.ucll.itintegrationproject.NL_14_backend.repository;

import be.ucll.itintegrationproject.NL_14_backend.model.Ride;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideRepository extends JpaRepository<Ride, Long> {

  List<Ride> findByUserId(Long userId);
}
