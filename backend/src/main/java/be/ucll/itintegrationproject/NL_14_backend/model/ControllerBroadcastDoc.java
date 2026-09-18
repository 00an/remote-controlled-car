package be.ucll.itintegrationproject.NL_14_backend.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Raw payload exactly as broadcast to the ESP32 over WebSocket. No quantize, no hysteresis, no
 * dedup — captures what the ESP actually received so the data&ai dashboard can compare it against
 * the filtered {@code control_inputs} collection.
 */
@Document(collection = "controller_broadcasts")
public class ControllerBroadcastDoc {

  @Id private String id;

  @Indexed
  @Field("recordedAt")
  private Instant recordedAt;

  private Long clientTimestamp;

  private List<Double> axes;
  private List<Integer> buttons;
  private List<List<Integer>> hats;

  @Indexed private String source;

  @Indexed private UUID sessionId;

  public ControllerBroadcastDoc() {}

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

  public List<Double> getAxes() {
    return axes;
  }

  public void setAxes(List<Double> axes) {
    this.axes = axes;
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
