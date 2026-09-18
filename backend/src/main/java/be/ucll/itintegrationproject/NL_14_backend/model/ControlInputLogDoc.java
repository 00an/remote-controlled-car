package be.ucll.itintegrationproject.NL_14_backend.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "control_inputs")
public class ControlInputLogDoc {

  @Id private String id;

  @Indexed
  @Field("recordedAt")
  private Instant recordedAt;

  // Boxed: clients may omit timestamp; null is distinguishable from epoch 0.
  private Long clientTimestamp;

  private double steering;
  private double throttle;
  private double brake;

  private List<Integer> buttons;
  private List<List<Integer>> hats;

  @Indexed private String source;

  @Indexed private UUID sessionId;

  public ControlInputLogDoc() {}

  public String getId() {
    return id;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant recordedAt) {
    this.recordedAt = recordedAt;
  }

  public Long getClientTimestamp() {
    return clientTimestamp;
  }

  public void setClientTimestamp(Long clientTimestamp) {
    this.clientTimestamp = clientTimestamp;
  }

  public double getSteering() {
    return steering;
  }

  public void setSteering(double steering) {
    this.steering = steering;
  }

  public double getThrottle() {
    return throttle;
  }

  public void setThrottle(double throttle) {
    this.throttle = throttle;
  }

  public double getBrake() {
    return brake;
  }

  public void setBrake(double brake) {
    this.brake = brake;
  }

  public List<Integer> getButtons() {
    return buttons;
  }

  public void setButtons(List<Integer> buttons) {
    this.buttons = buttons;
  }

  public List<List<Integer>> getHats() {
    return hats;
  }

  public void setHats(List<List<Integer>> hats) {
    this.hats = hats;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public void setSessionId(UUID sessionId) {
    this.sessionId = sessionId;
  }
}
