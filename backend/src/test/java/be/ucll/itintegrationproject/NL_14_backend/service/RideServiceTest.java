package be.ucll.itintegrationproject.NL_14_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.ucll.itintegrationproject.NL_14_backend.exception.RideNotFoundException;
import be.ucll.itintegrationproject.NL_14_backend.model.Ride;
import be.ucll.itintegrationproject.NL_14_backend.model.User;
import be.ucll.itintegrationproject.NL_14_backend.repository.RideRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RideServiceTest {

  private RideRepository rideRepository;
  private RideService rideService;

  @BeforeEach
  void setUp() {
    rideRepository = mock(RideRepository.class);
    rideService = new RideService(rideRepository);
  }

  @Test
  void returnsEmptyListWhenUserHasNoRides() {
    when(rideRepository.findByUserId(1L)).thenReturn(List.of());

    var result = rideService.getRidesForUser(1L);

    assertThat(result).isEmpty();
  }

  @Test
  void returnsRidesForUser() {
    User user = new User();
    user.setId(1L);

    Ride ride = new Ride();
    ride.setUser(user);

    when(rideRepository.findByUserId(1L)).thenReturn(List.of(ride));

    var result = rideService.getRidesForUser(1L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getUser().getId()).isEqualTo(1L);
  }

  @Test
  void doesNotReturnOtherUsersRides() {
    User user1 = new User();
    user1.setId(1L);

    User user2 = new User();
    user2.setId(2L);

    Ride user2Ride = new Ride();
    user2Ride.setUser(user2);

    when(rideRepository.findByUserId(1L)).thenReturn(List.of());
    when(rideRepository.findByUserId(2L)).thenReturn(List.of(user2Ride));

    var result = rideService.getRidesForUser(1L);

    assertThat(result).isEmpty();
  }

  @Test
  void saveRideLinksRideToUser() {
    User user = new User();
    user.setId(1L);

    rideService.saveRide(user, 120, 80, 3600);

    var captor = org.mockito.ArgumentCaptor.forClass(Ride.class);
    verify(rideRepository).save(captor.capture());
    Ride saved = captor.getValue();
    assertThat(saved.getUser()).isEqualTo(user);
    assertThat(saved.getTopspeed()).isEqualTo(120);
    assertThat(saved.getAveragespeed()).isEqualTo(80);
    assertThat(saved.getTimespent()).isEqualTo(3600);
  }

  @Test
  void updateRideChangesValuesOnOwnedRide() {
    User user = new User();
    user.setId(1L);
    Ride ride = new Ride();
    ride.setUser(user);

    when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));
    when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Ride result = rideService.updateRide(5L, 1L, 200, 150, 900);

    assertThat(result.getTopspeed()).isEqualTo(200);
    assertThat(result.getAveragespeed()).isEqualTo(150);
    assertThat(result.getTimespent()).isEqualTo(900);
  }

  @Test
  void updateRideThrowsWhenRideBelongsToAnotherUser() {
    User owner = new User();
    owner.setId(2L);
    Ride ride = new Ride();
    ride.setUser(owner);

    when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));

    assertThatThrownBy(() -> rideService.updateRide(5L, 1L, 200, 150, 900))
        .isInstanceOf(RideNotFoundException.class);
    verify(rideRepository, never()).save(any(Ride.class));
  }

  @Test
  void updateRideThrowsWhenRideDoesNotExist() {
    when(rideRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> rideService.updateRide(99L, 1L, 200, 150, 900))
        .isInstanceOf(RideNotFoundException.class);
  }

  @Test
  void deleteRideRemovesOwnedRide() {
    User user = new User();
    user.setId(1L);
    Ride ride = new Ride();
    ride.setUser(user);

    when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));

    rideService.deleteRide(5L, 1L);

    verify(rideRepository).delete(ride);
  }

  @Test
  void deleteRideThrowsWhenRideBelongsToAnotherUser() {
    User owner = new User();
    owner.setId(2L);
    Ride ride = new Ride();
    ride.setUser(owner);

    when(rideRepository.findById(5L)).thenReturn(Optional.of(ride));

    assertThatThrownBy(() -> rideService.deleteRide(5L, 1L))
        .isInstanceOf(RideNotFoundException.class);
    verify(rideRepository, never()).delete(any(Ride.class));
  }
}
