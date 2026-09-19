package be.ucll.itintegrationproject.NL_14_backend.service;

import be.ucll.itintegrationproject.NL_14_backend.exception.RideNotFoundException;
import be.ucll.itintegrationproject.NL_14_backend.model.Ride;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.repository.RideRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RideService {

  private final RideRepository rideRepository;

  public RideService(RideRepository rideRepository) {
    this.rideRepository = rideRepository;
  }

  public List<Ride> getRidesForUser(Long userId) {
    return rideRepository.findByUserId(userId);
  }

  public Ride saveRide(User user, int topspeed, int averagespeed, int timespent) {
    Ride ride = new Ride();
    ride.setUser(user);
    ride.setTopspeed(topspeed);
    ride.setAveragespeed(averagespeed);
    ride.setTimespent(timespent);
    return rideRepository.save(ride);
  }

  public Ride updateRide(Long rideId, Long userId, int topspeed, int averagespeed, int timespent) {
    Ride ride = getOwnedRide(rideId, userId);
    ride.setTopspeed(topspeed);
    ride.setAveragespeed(averagespeed);
    ride.setTimespent(timespent);
    return rideRepository.save(ride);
  }

  public void deleteRide(Long rideId, Long userId) {
    rideRepository.delete(getOwnedRide(rideId, userId));
  }

  // 404 instead of 403 so you can't guess which ride IDs exist
  private Ride getOwnedRide(Long rideId, Long userId) {
    return rideRepository
        .findById(rideId)
        .filter(ride -> ride.getUser() != null && ride.getUser().getId().equals(userId))
        .orElseThrow(() -> new RideNotFoundException(rideId));
  }
}
