// Configure your Wi-Fi and websocket endpoint here.
// Use port 3000 and useSecureWebSocket = false for local development.
// Wi-Fi credentials live in config_secrets.h (git-ignored). Copy
// config_secrets.h.example to config_secrets.h and fill it in.

#include "config_secrets.h"

const char* host = "esp32-backend-itip-nl-14.apps.okd.ucll.cloud";
const uint16_t port = 443;
const char* websocketPath = "/ws";
const bool useSecureWebSocket = true;
const char* websocketFingerprint = "";  // empty = no pinning, rely on insecure TLS (edge-terminated OKD route)
const bool enableHttpFallback = true;
const char* httpFallbackHost = "esp32-backend-itip-nl-14.apps.okd.ucll.cloud";
const uint16_t httpFallbackPort = 443;
const bool useSecureHttpFallback = true;
const char* httpLatestPath = "/api/controller/latest";
constexpr unsigned long httpFallbackIntervalMs = 1000;

// OTA is only available while the ESP32 is connected to Wi-Fi.
// Leave otaPassword empty to allow unauthenticated updates on your local network.
const char* otaHostname = "esp32-remote-car";
const char* otaPassword = "";

constexpr int steeringServoPin = 13;
constexpr int driveOutputPin = 12;
constexpr int builtInLedPin = 2;  // ESP32 built-in LED (GPIO2)

// SBC-OLED01 / SSD1306 I2C display settings.
// Adjust these if your panel uses a different size, address, or I2C pins.
constexpr int oledWidth = 128;
constexpr int oledHeight = 64;
constexpr int oledResetPin = -1;
constexpr uint8_t oledI2cAddress = 0x3C;
constexpr int oledSdaPin = 21;
constexpr int oledSclPin = 22;

// MPU-9265 IMU sensor settings
constexpr uint8_t mpu9265I2cAddress = 0x68;  // 0x68 or 0x69 (AD0 pin dependent)
constexpr unsigned long telemetryIntervalMs = 500;  // Send telemetry every 500ms

// Idle low-power mode (auto-enter when no controller input is received).
// Disabled to avoid unexpected behavior during development and testing.
constexpr bool enableIdleLowPowerMode = false;
constexpr unsigned long idleLowPowerTimeoutMs = 5000;
constexpr int idleLowPowerCpuMhz = 80;  // Typical ESP32 defaults to 240 MHz.

// Optional stepper (28BYJ-48 + ULN2003) used as drive actuator
// Wire these to the 4 IN pins of the ULN2003 board in the order below.
constexpr bool useStepperDrive = true; // set false to keep existing PWM drive
constexpr int stepperPin1 = 14; // IN1
constexpr int stepperPin2 = 27; // IN2
constexpr int stepperPin3 = 26; // IN3
constexpr int stepperPin4 = 25; // IN4
constexpr float stepperMaxSpeedStepsPerSec = 1750.0f; // 28BYJ-48 via ULN2003, half-step

// Boot test sequence: left, right, forwards, backwards
constexpr bool enableBootTest = false; // set false to skip