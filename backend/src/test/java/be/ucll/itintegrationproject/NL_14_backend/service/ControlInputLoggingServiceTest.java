package be.ucll.itintegrationproject.NL_14_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import be.ucll.itintegrationproject.NL_14_backend.controller.DTO.ControllerInput;
import be.ucll.itintegrationproject.NL_14_backend.model.ControlInputLogDoc;
import be.ucll.itintegrationproject.NL_14_backend.repository.ControlInputLogMongoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ControlInputLoggingServiceTest {

  private static final double EPS = 1e-9;

  private ControlInputLogMongoRepository repository;
  private ControlInputLoggingService service;

  @BeforeEach
  void setUp() {
    repository = mock(ControlInputLogMongoRepository.class);
    service = new ControlInputLoggingService(repository, new ObjectMapper());
    ReflectionTestUtils.setField(service, "minIntervalMs", 0L);
    ReflectionTestUtils.setField(service, "quantizeStep", 0.0);
    ReflectionTestUtils.setField(service, "deadzone", 0.0);
    // Disable hysteresis by default — tests opt in explicitly when checking it.
    ReflectionTestUtils.setField(service, "wakeThreshold", 0.0);
    ReflectionTestUtils.setField(service, "fullThreshold", 1.0 + 1e-9);
  }

  @Test
  void logSkipsWhenNoActiveSession() {
    ControllerInput input = new ControllerInput(List.of(0.1, 0.2, 0.3), List.of(), List.of(), 42L);

    service.log(input, "wheel");

    verify(repository, never()).save(any());
  }

  @Test
  void logPersistsWhenSessionActive() {
    service.startSession();
    // axes: [steering, gasRaw, brakeRaw] where +1=rest, -1=floored for pedals
    // gas 0.2 -> (1 - 0.2) / 2 = 0.4
    // brake 0.3 -> (1 - 0.3) / 2 = 0.35
    ControllerInput input = new ControllerInput(List.of(0.1, 0.2, 0.3), List.of(), List.of(), 42L);

    service.log(input, "wheel");

    ArgumentCaptor<ControlInputLogDoc> captor = ArgumentCaptor.forClass(ControlInputLogDoc.class);
    verify(repository).save(captor.capture());
    ControlInputLogDoc saved = captor.getValue();
    assertThat(saved.getSteering()).isEqualTo(0.1);
    assertThat(saved.getThrottle()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(EPS));
    assertThat(saved.getBrake()).isCloseTo(0.35, org.assertj.core.data.Offset.offset(EPS));
    assertThat(saved.getClientTimestamp()).isEqualTo(42L);
    assertThat(saved.getSource()).isEqualTo("wheel");
    assertThat(saved.getRecordedAt()).isNotNull();
  }

  @Test
  void pedalAtRestNormalizesToZero() {
    service.startSession();
    // Wheel pedal at rest: gasRaw=1, brakeRaw=1 -> throttle=0, brake=0
    ControllerInput input = new ControllerInput(List.of(0.0, 1.0, 1.0), List.of(), List.of(), 1L);

    service.log(input, "wheel");

    ArgumentCaptor<ControlInputLogDoc> captor = ArgumentCaptor.forClass(ControlInputLogDoc.class);
    verify(repository).save(captor.capture());
    ControlInputLogDoc saved = captor.getValue();
    assertThat(saved.getThrottle()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(EPS));
    assertThat(saved.getBrake()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(EPS));
  }

  @Test
  void pedalFlooredNormalizesToOne() {
    service.startSession();
    // Pedal floored: gasRaw=-1 -> throttle=1
    ControllerInput input = new ControllerInput(List.of(0.0, -1.0, 1.0), List.of(), List.of(), 1L);

    service.log(input, "wheel");

    ArgumentCaptor<ControlInputLogDoc> captor = ArgumentCaptor.forClass(ControlInputLogDoc.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getThrottle())
        .isCloseTo(1.0, org.assertj.core.data.Offset.offset(EPS));
  }

  @Test
  void deadzoneClampsNearRestToZero() {
    service.startSession();
    // Enable hysteresis: wake at 8%, sleep at 5%.
    ReflectionTestUtils.setField(service, "deadzone", 0.05);
    ReflectionTestUtils.setField(service, "wakeThreshold", 0.08);
    // gasRaw=0.96 -> pressed = (1-0.96)/2 = 0.02, below 8% wake threshold -> 0
    ControllerInput input = new ControllerInput(List.of(0.0, 0.96, 1.0), List.of(), List.of(), 1L);

    service.log(input, "wheel");

    ArgumentCaptor<ControlInputLogDoc> captor = ArgumentCaptor.forClass(ControlInputLogDoc.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getThrottle())
        .isCloseTo(0.0, org.assertj.core.data.Offset.offset(EPS));
  }

  @Test
  void hysteresisPreventsBoundaryFlicker() {
    service.startSession();
    // wake at 8%, sleep at 4%. Value oscillating between 5% and 7% should stay at 0
    // until it crosses 8%, then stay non-zero until it drops below 4%.
    ReflectionTestUtils.setField(service, "deadzone", 0.04);
    ReflectionTestUtils.setField(service, "wakeThreshold", 0.08);

    // pressed = (1 - 0.9) / 2 = 0.05 -> still REST, below 0.08 wake -> 0
    service.log(new ControllerInput(List.of(0.0, 0.9, 1.0), List.of(), List.of(), 1L), "wheel");
    // pressed = (1 - 0.86) / 2 = 0.07 -> still REST -> 0 (would have woken at 5% without
    // hysteresis)
    service.log(new ControllerInput(List.of(0.0, 0.86, 1.0), List.of(), List.of(), 2L), "wheel");

    ArgumentCaptor<ControlInputLogDoc> captor = ArgumentCaptor.forClass(ControlInputLogDoc.class);
    verify(repository, atLeastOnce()).save(captor.capture());
    // First sample (steering=0, throttle=0, brake=0) is the only thing that should have been saved,
    // and dedup may catch the second identical one. Either way both are 0.
    for (var saved : captor.getAllValues()) {
      assertThat(saved.getThrottle()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(EPS));
    }
  }

  @Test
  void logTagsWithActiveSessionId() {
    UUID session = service.startSession();
    ControllerInput input = new ControllerInput(List.of(0.0, 1.0, 1.0), List.of(), List.of(), 0L);

    service.log(input, "wheel");

    ArgumentCaptor<ControlInputLogDoc> captor = ArgumentCaptor.forClass(ControlInputLogDoc.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getSessionId()).isEqualTo(session);
  }

  @Test
  void endSessionClearsSessionId() {
    service.startSession();
    service.endSession();
    assertThat(service.getCurrentSessionId()).isNull();
  }

  @Test
  void logRawParsesJsonAndPersists() {
    service.startSession();
    String payload = "{\"axes\":[-0.5,0.4,0.6],\"buttons\":[],\"hats\":[],\"timestamp\":99}";

    service.logRaw(payload, "keyboard");

    ArgumentCaptor<ControlInputLogDoc> captor = ArgumentCaptor.forClass(ControlInputLogDoc.class);
    verify(repository).save(captor.capture());
    ControlInputLogDoc saved = captor.getValue();
    assertThat(saved.getSteering()).isEqualTo(-0.5);
    // gas 0.4 -> 0.3; brake 0.6 -> 0.2
    assertThat(saved.getThrottle()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(EPS));
    assertThat(saved.getBrake()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(EPS));
    assertThat(saved.getSource()).isEqualTo("keyboard");
    assertThat(saved.getClientTimestamp()).isEqualTo(99L);
  }

  @Test
  void logRawSkipsUnparseablePayload() {
    service.startSession();
    service.logRaw("not json", "keyboard");
    verify(repository, never()).save(any());
  }

  @Test
  void logIgnoresNullInput() {
    service.startSession();
    service.log(null, "wheel");
    verify(repository, never()).save(any());
  }

  @Test
  void dedupSkipsUnchangedSamples() {
    service.startSession();
    ControllerInput input = new ControllerInput(List.of(0.1, 0.2, 0.3), List.of(), List.of(), 1L);

    service.log(input, "wheel");
    service.log(input, "wheel");
    service.log(input, "wheel");

    verify(repository, times(1)).save(any());
  }

  @Test
  void quantizeCollapsesNearbyValuesAsUnchanged() {
    service.startSession();
    ReflectionTestUtils.setField(service, "quantizeStep", 0.01);

    // Quantization runs on the *normalized* pedal value (0..1).
    // gasRaw 0.758 -> pressed (1-0.758)/2 = 0.121 -> quantized to 0.12
    // gasRaw 0.752 -> pressed (1-0.752)/2 = 0.124 -> quantized to 0.12
    // Both map to the same bucket -> dedup hits on the second.
    service.log(new ControllerInput(List.of(0.0, 0.758, 1.0), List.of(), List.of(), 1L), "wheel");
    service.log(new ControllerInput(List.of(0.0, 0.752, 1.0), List.of(), List.of(), 2L), "wheel");

    verify(repository, times(1)).save(any());
  }

  @Test
  void quantizeAllowsValuesAcrossStepBoundary() {
    service.startSession();
    ReflectionTestUtils.setField(service, "quantizeStep", 0.01);

    // gasRaw 0.758 -> 0.121 -> 0.12
    // gasRaw 0.748 -> 0.126 -> 0.13
    // Different buckets, both persist.
    service.log(new ControllerInput(List.of(0.0, 0.758, 1.0), List.of(), List.of(), 1L), "wheel");
    service.log(new ControllerInput(List.of(0.0, 0.748, 1.0), List.of(), List.of(), 2L), "wheel");

    verify(repository, times(2)).save(any());
  }
}
