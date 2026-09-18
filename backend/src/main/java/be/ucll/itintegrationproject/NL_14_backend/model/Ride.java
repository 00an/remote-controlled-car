package be.ucll.itintegrationproject.NL_14_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "user")
@Table(name = "ride")
public class Ride {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @JsonIgnore
  private Instant createdAt;

  @ManyToOne private User user;

  private int topspeed;

  private int averagespeed;

  private int timespent;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public int getTopspeed() {
    return topspeed;
  }

  public void setTopspeed(int topspeed) {
    this.topspeed = topspeed;
  }

  public int getAveragespeed() {
    return averagespeed;
  }

  public void setAveragespeed(int averagespeed) {
    this.averagespeed = averagespeed;
  }

  public int getTimespent() {
    return timespent;
  }

  public void setTimespent(int timespent) {
    this.timespent = timespent;
  }
}
