package be.ucll.itintegrationproject.NL_14_backend.controller;

import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.RideInput;
import be.ucll.itintegrationproject.NL_14_backend.exception.UserNotFoundException;
import be.ucll.itintegrationproject.NL_14_backend.model.Ride;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.service.RideService;
import be.ucll.itintegrationproject.NL_14_backend.service.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rides")
public class RideController {

  private final RideService rideService;
  private final UserService userService;

  public RideController(RideService rideService, UserService userService) {
    this.rideService = rideService;
    this.userService = userService;
  }

  @GetMapping
  public List<Ride> getRideHistory(Authentication authentication) {
    return rideService.getRidesForUser(currentUser(authentication).getId());
  }

  @PostMapping
  public Ride createRide(@RequestBody RideInput rideInput, Authentication authentication) {
    return rideService.saveRide(
        currentUser(authentication),
        rideInput.topspeed(),
        rideInput.averagespeed(),
        rideInput.timespent());
  }

  @PutMapping("/{id}")
  public Ride updateRide(
      @PathVariable Long id, @RequestBody RideInput rideInput, Authentication authentication) {
    return rideService.updateRide(
        id,
        currentUser(authentication).getId(),
        rideInput.topspeed(),
        rideInput.averagespeed(),
        rideInput.timespent());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Object> deleteRide(@PathVariable Long id, Authentication authentication) {
    rideService.deleteRide(id, currentUser(authentication).getId());
    return ResponseEntity.ok(Map.of("message", "Ride deleted successfully"));
  }

  private User currentUser(Authentication authentication) {
    return userService
        .getUserByUsername(authentication.getName())
        .orElseThrow(() -> new UserNotFoundException(authentication.getName()));
  }
}
