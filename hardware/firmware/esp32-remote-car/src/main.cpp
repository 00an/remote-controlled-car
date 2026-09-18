#include <Arduino.h>
#include <ArduinoJson.h>
#include <ArduinoOTA.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <HTTPClient.h>
#include <Update.h>
#include <WebServer.h>
#include <WebSocketsClient.h>
#include <WiFiClientSecure.h>
#include <WiFi.h>
#include <Wire.h>
#include <MPU9250.h>
#include <esp32-hal-cpu.h>
#include <soc/gpio_struct.h>

#include "config.h"
#include "aes_hardware.h"

namespace {
constexpr int websocketReconnectMs = 5000;
constexpr int pwmFrequency = 50;
constexpr int pwmResolution = 16;
constexpr int steeringChannel = 0;
constexpr int driveChannel = 1;
constexpr unsigned long stepperCommandGraceMs = 30;
constexpr unsigned long stepperStopTimeoutMs = 400;
constexpr float stepperAccelerationStepsPerSec2 = 1200.0f;
constexpr unsigned long controllerSignalTimeoutMs = 800;
constexpr uint8_t stepperPhaseCount = 8;

DRAM_ATTR uint32_t stepperSetMask[8];
DRAM_ATTR uint32_t stepperClrMask[8];

hw_timer_t *stepperTimer = nullptr;
portMUX_TYPE stepperMux = portMUX_INITIALIZER_UNLOCKED;
volatile int8_t isrStepperDirection = 0;  // -1, 0, or +1 (no float in ISR)
volatile uint8_t isrStepperPhase = 0;

void IRAM_ATTR onStepperTimer() {
    int8_t dir = isrStepperDirection;
    if (dir == 0) return;

    uint8_t phase = isrStepperPhase;
    phase = (phase + (uint8_t)(dir > 0 ? 1 : 7)) & 7;
    isrStepperPhase = phase;

    GPIO.out_w1ts = stepperSetMask[phase];
    GPIO.out_w1tc = stepperClrMask[phase];
}

WebSocketsClient webSocket;
WebServer webServer(80);
Adafruit_SSD1306 display(oledWidth, oledHeight, &Wire, oledResetPin);
MPU9250 mpu9250(Wire, mpu9265I2cAddress);

// LED state tracking
enum class ConnectionState {
    WIFI_CONNECTING,
    WIFI_CONNECTED_WS_DISCONNECTED,
    WEBSOCKET_CONNECTED,
    ERROR
};

// Display modes
enum class DisplayMode {
    SPEEDOMETER,
    DEBUG_INFO,
    ERROR_DISPLAY,
    BOOT_SCREEN
};

// Display configuration
constexpr float speedometerThresholdMs = 0.5f;  // m/s threshold to show speedometer

ConnectionState currentState = ConnectionState::WIFI_CONNECTING;
DisplayMode currentDisplayMode = DisplayMode::BOOT_SCREEN;
unsigned long lastLedUpdate = 0;
unsigned long lastHttpFallbackPoll = 0;
bool websocketConnected = false;
float lastSteering = 0.0f;
float lastThrottle = 0.0f;
float lastBrake = 0.0f;
float pendingStepperDrive = 0.0f;
float activeStepperDrive = 0.0f;
float currentStepperSpeed = 0.0f;
unsigned long lastStepperCommandReceived = 0;
unsigned long lastStepperCommandApplied = 0;
unsigned long lastStepperSpeedUpdate = 0;
uint8_t currentStepperPhase = 0;
bool stepperDrivePending = false;
String lastStatusMessage;
unsigned long lastDisplayUpdate = 0;
unsigned long lastControllerInputReceived = 0;
bool controllerSignalTimedOut = false;
bool lowPowerModeActive = false;
uint32_t activeCpuFrequencyMhz = 0;

// IMU telemetry variables
float velocityMagnitude = 0.0f;  // m/s
float currentAccX = 0.0f, currentAccY = 0.0f, currentAccZ = 0.0f;  // g
unsigned long lastImuReadTime = 0;
unsigned long lastTelemetrySend = 0;
bool imuInitialized = false;
float accelBiasX = 0.0f, accelBiasY = 0.0f, accelBiasZ = 0.0f;  // g (includes gravity)
bool imuCalibrated = false;

const char* connectionStateLabel(ConnectionState state) {
    switch (state) {
        case ConnectionState::WIFI_CONNECTING:
            return "WiFi connecting";
        case ConnectionState::WIFI_CONNECTED_WS_DISCONNECTED:
            return "WiFi up, WS down";
        case ConnectionState::WEBSOCKET_CONNECTED:
            return "WebSocket connected";
        case ConnectionState::ERROR:
            return "Error";
    }
    return "Unknown";
}

void setStatusMessage(const String& message) {
    lastStatusMessage = message;
}

void enterLowPowerMode() {
    if (lowPowerModeActive || !enableIdleLowPowerMode) {
        return;
    }

    lowPowerModeActive = true;
    WiFi.setSleep(true);
    activeCpuFrequencyMhz = getCpuFrequencyMhz();
    setCpuFrequencyMhz(idleLowPowerCpuMhz);

    if (oledWidth > 0 && oledHeight > 0) {
        display.ssd1306_command(SSD1306_DISPLAYOFF);
    }

    Serial.println("[power] entering low-power idle mode");
    setStatusMessage("Low-power idle");
}

void exitLowPowerMode() {
    if (!lowPowerModeActive) {
        return;
    }

    lowPowerModeActive = false;
    if (activeCpuFrequencyMhz > 0) {
        setCpuFrequencyMhz(activeCpuFrequencyMhz);
    }
    WiFi.setSleep(false);

    if (oledWidth > 0 && oledHeight > 0) {
        display.ssd1306_command(SSD1306_DISPLAYON);
    }

    lastDisplayUpdate = 0;
    Serial.println("[power] exiting low-power idle mode");
    setStatusMessage("Active");
}

void registerControllerInput() {
    lastControllerInputReceived = millis();
    controllerSignalTimedOut = false;
    exitLowPowerMode();
}

void initializeDisplay() {
    Wire.begin(oledSdaPin, oledSclPin, 100000);

    if (!display.begin(SSD1306_SWITCHCAPVCC, oledI2cAddress)) {
        Serial.println("[oled] SSD1306 init failed");
        return;
    }

    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    display.setTextWrap(false);
    display.setCursor(0, 0);
    display.println("ESP32 booting...");
    display.display();
    Serial.println("[oled] display ready");
}

// Draw an RC car splash screen
void drawBootScreen() {
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(2);
    display.setTextWrap(false);
    
    // Title: RC CAR
    display.setCursor(30, 2);
    display.println("RC CAR");
    
    // Simple RC car ASCII art (8 pixels below title)
    display.setTextSize(1);
    display.setCursor(32, 20);
    display.println("/---\\");
    display.setCursor(32, 28);
    display.println("(o o)");
    display.setCursor(32, 36);
    display.println("\\---/");
    
    // Bottom border
    display.drawLine(0, 50, 128, 50, SSD1306_WHITE);
    
    display.setTextSize(1);
    display.setCursor(20, 54);
    display.println("RC CAR");
    
    display.display();
}

// Draw boot status with animated loading indicator
void drawBootStatus(const String& status, int progress) {
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    display.setTextWrap(false);
    
    // Title bar
    display.drawLine(0, 0, 128, 0, SSD1306_WHITE);
    display.setCursor(2, 3);
    display.println("BOOTING");
    display.drawLine(0, 12, 128, 12, SSD1306_WHITE);
    
    // Status message
    display.setCursor(2, 16);
    display.println(status);
    
    // Progress bar
    const int barWidth = 120;
    const int barHeight = 6;
    const int barX = 4;
    const int barY = 35;
    
    display.drawRect(barX, barY, barWidth, barHeight, SSD1306_WHITE);
    int filledWidth = (progress * barWidth) / 100;
    display.fillRect(barX + 1, barY + 1, filledWidth - 2, barHeight - 2, SSD1306_WHITE);
    
    // Progress percentage
    display.setTextSize(1);
    display.setCursor(50, 46);
    display.print(progress);
    display.println("%");
    
    // Loading animation (spinning)
    const char spinner[] = {'|', '/', '-', '\\'};
    int spinnerIndex = (millis() / 200) % 4;
    display.setTextSize(2);
    display.setCursor(115, 35);
    display.print(spinner[spinnerIndex]);
    
    display.display();
}

// Update boot screen with status during initialization
void updateBootStatus(const String& message, int progress) {
    drawBootStatus(message, progress);
    delay(100);  // Brief delay for visual feedback
}

void drawDisplayLine(int16_t y, const String& text) {
    // Clear the line area first to avoid visual overlap from previous longer text
    const int lineHeight = 10; // safe line height for text size 1
    display.fillRect(0, y, oledWidth, lineHeight, SSD1306_BLACK);
    display.setCursor(0, y);
    display.print(text);
}

DisplayMode getDisplayMode() {
    if (currentState == ConnectionState::ERROR) {
        return DisplayMode::ERROR_DISPLAY;
    }
    if (WiFi.status() != WL_CONNECTED) {
        return DisplayMode::DEBUG_INFO;
    }
    if (!websocketConnected) {
        return DisplayMode::DEBUG_INFO;
    }
    if (velocityMagnitude > speedometerThresholdMs) {
        return DisplayMode::SPEEDOMETER;
    }
    return DisplayMode::DEBUG_INFO;
}

void drawSpeedometer() {
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    
    // Top header bar - retro futuristic style
    display.fillRect(0, 0, oledWidth, 8, SSD1306_WHITE);
    display.setTextColor(SSD1306_BLACK);
    display.setTextSize(1);
    display.setCursor(2, 1);
    display.println("VELOCITY MONITOR");
    
    // Reset color for gauge
    display.setTextColor(SSD1306_WHITE);
    
    // Draw circular gauge (simplified polygon representation for retro feel)
    const int centerX = 42;
    const int centerY = 40;
    const int radius = 28;
    
    // Draw gauge circle
    display.drawCircle(centerX, centerY, radius, SSD1306_WHITE);
    display.drawCircle(centerX, centerY, radius - 1, SSD1306_WHITE);
    
    // Draw gauge tick marks and speed labels (0, 20, 40 km/h)
    const float maxSpeed = 50.0f;  // km/h
    float currentSpeedKmh = velocityMagnitude * 3.6f;
    
    // 0 km/h (bottom left)
    display.drawLine(centerX - 20, centerY + 18, centerX - 24, centerY + 22, SSD1306_WHITE);
    display.setTextSize(1);
    display.setCursor(centerX - 30, centerY + 18);
    display.println("0");
    
    // 25 km/h (bottom)
    display.drawLine(centerX, centerY + 28, centerX, centerY + 31, SSD1306_WHITE);
    display.setCursor(centerX - 5, centerY + 32);
    display.println("25");
    
    // 50 km/h (top right)
    display.drawLine(centerX + 20, centerY + 18, centerX + 24, centerY + 22, SSD1306_WHITE);
    display.setCursor(centerX + 20, centerY + 15);
    display.println("50");
    
    // Draw needle (retro angular style)
    float speedRatio = constrain(currentSpeedKmh / maxSpeed, 0.0f, 1.0f);
    float needleAngle = 200.0f + (speedRatio * 140.0f);  // 200° to 340° range
    float needleRad = (needleAngle - 90.0f) * 3.14159f / 180.0f;
    int needleX = centerX + (int)(cos(needleRad) * (radius - 5));
    int needleY = centerY + (int)(sin(needleRad) * (radius - 5));
    display.drawLine(centerX, centerY, needleX, needleY, SSD1306_WHITE);
    display.drawLine(centerX, centerY, needleX - 1, needleY, SSD1306_WHITE);
    
    // Center hub
    display.fillCircle(centerX, centerY, 2, SSD1306_WHITE);
    
    // Right side: Speed readout (digital)
    display.setTextSize(2);
    display.setCursor(78, 10);
    display.print(currentSpeedKmh, 1);
    display.setTextSize(1);
    display.setCursor(78, 28);
    display.println("km/h");
    
    // Acceleration data
    display.setTextSize(1);
    display.setCursor(78, 36);
    display.print("ACC:");
    display.print(sqrt(currentAccX*currentAccX + currentAccY*currentAccY + currentAccZ*currentAccZ), 1);
    display.println("g");
    
    // Status line at bottom (green = connected)
    display.drawLine(0, 58, oledWidth, 58, SSD1306_WHITE);
    display.setTextSize(1);
    display.setCursor(2, 59);
    display.print(websocketConnected ? "WS:OK" : "WS:ERR");
    display.setCursor(50, 59);
    display.print("ACTIVE");
    
    display.display();
}

void drawDebugDisplay() {
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    display.setTextWrap(false);

    drawDisplayLine(0, String("WiFi: ") + (WiFi.status() == WL_CONNECTED ? "OK" : "DOWN"));
    drawDisplayLine(10, String("WS: ") + (websocketConnected ? "OK" : "DOWN"));
    drawDisplayLine(20, String("IP: ") + (WiFi.status() == WL_CONNECTED ? WiFi.localIP().toString() : String("--")));
    drawDisplayLine(30, String("State: ") + connectionStateLabel(currentState));

    if (oledHeight >= 64) {
        drawDisplayLine(40, String("Steer:") + String(lastSteering, 0) + " Thr:" + String(lastThrottle, 0));
        drawDisplayLine(50, String("Speed: ") + String(velocityMagnitude * 3.6f, 1) + " km/h " + (lowPowerModeActive ? "LP" : ""));
    } else if (lastStatusMessage.length() > 0) {
        drawDisplayLine(40, String("Msg: ") + lastStatusMessage.substring(0, 21));
    }

    display.display();
}

void drawErrorDisplay() {
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    
    // Error header with border
    display.fillRect(0, 0, oledWidth, 12, SSD1306_WHITE);
    display.setTextColor(SSD1306_BLACK);
    display.setCursor(38, 2);
    display.println("ERROR!");
    
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 16);
    display.println("System encountered");
    display.println("a critical error.");
    display.println("");
    display.println("Status: ");
    display.println(connectionStateLabel(currentState));
    display.println("");
    display.println("Message:");
    display.println(lastStatusMessage);
    
    display.display();
}

void updateDisplay() {
    unsigned long now = millis();
    if (now - lastDisplayUpdate < 500) {
        return;
    }
    lastDisplayUpdate = now;

    if (oledWidth <= 0 || oledHeight <= 0) {
        return;
    }

    // Determine which display to show
    currentDisplayMode = getDisplayMode();
    
    switch (currentDisplayMode) {
        case DisplayMode::SPEEDOMETER:
            drawSpeedometer();
            break;
        case DisplayMode::DEBUG_INFO:
            drawDebugDisplay();
            break;
        case DisplayMode::ERROR_DISPLAY:
            drawErrorDisplay();
            break;
        case DisplayMode::BOOT_SCREEN:
            // Boot screen is handled separately during setup
            break;
    }
}

bool isWebUpdateAuthorized() {
    if (strlen(otaPassword) == 0) {
        return true;
    }
    return webServer.authenticate("admin", otaPassword);
}

String buildUpdatePage() {
    String page;
    page.reserve(2400);
    page += F("<!DOCTYPE html><html><head><meta charset='utf-8'>");
    page += F("<meta name='viewport' content='width=device-width,initial-scale=1'>");
    page += F("<title>ESP32 OTA Update</title>");
    page += F("<style>body{font-family:Arial,sans-serif;max-width:720px;margin:40px auto;padding:0 16px;line-height:1.5}h1{margin-bottom:0.25rem}code{background:#f4f4f4;padding:2px 6px;border-radius:4px}form{margin-top:1rem;padding:1rem;border:1px solid #ddd;border-radius:8px}input[type=file]{display:block;margin:0.5rem 0 1rem}button{padding:0.7rem 1rem;border:0;border-radius:6px;background:#0b5fff;color:#fff;font-size:1rem;cursor:pointer}small{color:#555}.card{background:#fafafa;border:1px solid #eee;border-radius:8px;padding:1rem}</style>");
    page += F("</head><body>");
    page += F("<h1>ESP32 OTA Update</h1>");
    page += F("<p>Upload a new firmware <code>.bin</code> file from your browser.</p>");
    page += F("<div class='card'><strong>Device IP:</strong> ");
    page += WiFi.localIP().toString();
    page += F("<br><strong>Hostname:</strong> ");
    page += otaHostname;
    page += F("</div>");
    page += F("<form method='POST' action='/update' enctype='multipart/form-data'>");
    page += F("<input type='file' name='update' accept='.bin' required>");
    page += F("<button type='submit'>Upload Firmware</button>");
    page += F("</form>");
    page += F("<p><small>If you set an OTA password in <code>include/config.h</code>, your browser will ask for it.</small></p>");
    page += F("</body></html>");
    return page;
}

void handleWebUpdateRoot() {
    if (!isWebUpdateAuthorized()) {
        webServer.requestAuthentication();
        return;
    }

    webServer.send(200, "text/html", buildUpdatePage());
}

void handleWebUpdateUpload() {
    if (!isWebUpdateAuthorized()) {
        return;
    }

    HTTPUpload &upload = webServer.upload();

    if (upload.status == UPLOAD_FILE_START) {
        Serial.printf("[web-ota] upload start: %s\n", upload.filename.c_str());
        if (!Update.begin(UPDATE_SIZE_UNKNOWN)) {
            Update.printError(Serial);
        }
    } else if (upload.status == UPLOAD_FILE_WRITE) {
        if (Update.write(upload.buf, upload.currentSize) != upload.currentSize) {
            Update.printError(Serial);
        }
    } else if (upload.status == UPLOAD_FILE_END) {
        if (Update.end(true)) {
            Serial.printf("[web-ota] upload complete: %u bytes\n", upload.totalSize);
        } else {
            Update.printError(Serial);
        }
    }
}

void setupWebUpdateServer() {
    webServer.on("/", HTTP_GET, handleWebUpdateRoot);
    webServer.on("/update", HTTP_GET, handleWebUpdateRoot);
    webServer.on(
        "/update",
        HTTP_POST,
        []() {
            const bool ok = !Update.hasError();
            webServer.send(ok ? 200 : 500, "text/plain", ok ? "Update successful. Rebooting..." : "Update failed.");
            delay(500);
            if (ok) {
                ESP.restart();
            }
        },
        handleWebUpdateUpload);

    webServer.begin();
    Serial.println("[web-ota] update page ready at /update");
}

void setupOta() {
    ArduinoOTA.setHostname(otaHostname);

    if (strlen(otaPassword) > 0) {
        ArduinoOTA.setPassword(otaPassword);
    }

    ArduinoOTA
        .onStart([]() {
            Serial.println("[ota] start");
        })
        .onEnd([]() {
            Serial.println("[ota] end");
        })
        .onProgress([](unsigned int progress, unsigned int total) {
            Serial.printf("[ota] progress: %u%%\r", (progress * 100U) / total);
        })
        .onError([](ota_error_t error) {
            Serial.printf("[ota] error[%u]\n", error);
        });

    ArduinoOTA.begin();
    Serial.printf("[ota] ready, hostname=%s\n", otaHostname);
}

void updateLed() {
    if (lowPowerModeActive) {
        digitalWrite(builtInLedPin, LOW);
        return;
    }

    unsigned long now = millis();
    if (now - lastLedUpdate < 50) return;
    lastLedUpdate = now;
    static int breathePhase = 0;
    static bool blink = false;
    switch (currentState) {
        case ConnectionState::WIFI_CONNECTING:
            blink = (now / 100) % 2;
            digitalWrite(builtInLedPin, blink ? HIGH : LOW);
            break;
        case ConnectionState::WIFI_CONNECTED_WS_DISCONNECTED:
            blink = (now / 300) % 2;
            digitalWrite(builtInLedPin, blink ? HIGH : LOW);
            break;
        case ConnectionState::WEBSOCKET_CONNECTED: {
            breathePhase = (now / 50) % 40;
            bool ledOn = breathePhase < 20;
            digitalWrite(builtInLedPin, ledOn ? HIGH : LOW);
            break;
        }
        case ConnectionState::ERROR:
            blink = (now / 50) % 2;
            digitalWrite(builtInLedPin, blink ? HIGH : LOW);
            break;
    }
}

void writePulseMicros(int channel, int pulseMicros) {
    pulseMicros = constrain(pulseMicros, 500, 2500);
    const uint32_t maxDuty = (1UL << pwmResolution) - 1;
    const uint32_t duty = static_cast<uint32_t>((pulseMicros / 20000.0f) * maxDuty);
    ledcWrite(channel, duty);
}

void disableStepperOutputs() {
    digitalWrite(stepperPin1, LOW);
    digitalWrite(stepperPin2, LOW);
    digitalWrite(stepperPin3, LOW);
    digitalWrite(stepperPin4, LOW);
}

void applyControllerState(float steering, float throttle, float brake) {
    steering = constrain(steering, -100.0f, 100.0f);
    throttle = constrain(throttle, 0.0f, 100.0f);
    brake = constrain(brake, 0.0f, 100.0f);
    lastSteering = steering;
    lastThrottle = throttle;
    lastBrake = brake;

    // Convert steering (-100 to 100) to angle (inverted: 180 to 0), then to microseconds (1000 to 2000)
    // Swapped mapping endpoints to invert steering direction
    const int steeringAngle = static_cast<int>(map(static_cast<long>(steering), -100, 100, 180, 0));
    const int steeringPulse = static_cast<int>(1000.0f + (steeringAngle * 5.55f));
    writePulseMicros(steeringChannel, steeringPulse);

    if (driveOutputPin >= 0) {
        const float drive = throttle - brake;
        if (useStepperDrive) {
            pendingStepperDrive = drive;
            lastStepperCommandReceived = millis();
            stepperDrivePending = true;
        } else {
            const int drivePulse = static_cast<int>(1500.0f + (drive * 5.0f));
            writePulseMicros(driveChannel, drivePulse);
        }
    }

    Serial.printf("[controller] steering=%.1f (angle=%d, pulse=%d us) throttle=%.1f brake=%.1f\n", steering, steeringAngle, steeringPulse, throttle, brake);
}

void serviceControllerTimeouts() {
    const unsigned long now = millis();
    if (lastControllerInputReceived == 0) {
        lastControllerInputReceived = now;
        return;
    }

    const unsigned long idleForMs = now - lastControllerInputReceived;
    if (!controllerSignalTimedOut && idleForMs >= controllerSignalTimeoutMs) {
        // Failsafe: neutralize outputs if control stream stops.
        applyControllerState(0.0f, 0.0f, 0.0f);
        controllerSignalTimedOut = true;
        setStatusMessage("Input timeout -> neutral");
        Serial.println("[controller] input timeout -> neutral outputs");
    }

    if (enableIdleLowPowerMode && !lowPowerModeActive && idleForMs >= idleLowPowerTimeoutMs) {
        enterLowPowerMode();
    }
}

void handleControllerPayload(const uint8_t *payload, size_t length) {
    JsonDocument doc;
    DeserializationError error = deserializeJson(doc, payload, length);
    if (error) {
        Serial.printf("[ws] JSON parse error: %s\n", error.c_str());
        setStatusMessage(String("JSON error: ") + error.c_str());
        return;
    }

    JsonArray axes = doc["axes"].as<JsonArray>();
    const float steeringAxis = axes.size() > 0 ? axes[0].as<float>() : 0.0f;
    const float gasAxis = axes.size() > 1 ? axes[1].as<float>() : 1.0f;
    const float brakeAxis = axes.size() > 2 ? axes[2].as<float>() : 1.0f;

    const float steering = steeringAxis * 100.0f;
    const float throttle = constrain(((1.0f - gasAxis) / 2.0f) * 100.0f, 0.0f, 100.0f);
    const float brake = constrain(((1.0f - brakeAxis) / 2.0f) * 100.0f, 0.0f, 100.0f);

    registerControllerInput();
    applyControllerState(steering, throttle, brake);
}

void webSocketEvent(WStype_t type, uint8_t *payload, size_t length) {
    switch (type) {
        case WStype_DISCONNECTED:
            if (length > 0) {
                Serial.printf("[ws] disconnected: %.*s\n", static_cast<int>(length), payload);
                setStatusMessage(String("WS down: ") + String(reinterpret_cast<char*>(payload), length));
            } else {
                Serial.println("[ws] disconnected");
                setStatusMessage("WS disconnected");
            }
            websocketConnected = false;
            if (WiFi.status() == WL_CONNECTED) {
                currentState = ConnectionState::WIFI_CONNECTED_WS_DISCONNECTED;
            } else {
                currentState = ConnectionState::WIFI_CONNECTING;
            }
            break;
        case WStype_CONNECTED:
            Serial.printf("[ws] connected to %s\n", payload);
            setStatusMessage("WS connected");
            websocketConnected = true;
            currentState = ConnectionState::WEBSOCKET_CONNECTED;
            break;
        case WStype_TEXT:
            handleControllerPayload(payload, length);
            break;
        case WStype_ERROR:
            if (length > 0) {
                Serial.printf("[ws] error: %.*s\n", static_cast<int>(length), payload);
                setStatusMessage(String("WS error: ") + String(reinterpret_cast<char*>(payload), length));
            } else {
                Serial.println("[ws] error");
                setStatusMessage("WS error");
            }
            websocketConnected = false;
            currentState = ConnectionState::ERROR;
            break;
        default:
            break;
    }
}

void pollHttpFallback() {
    if (!enableHttpFallback || WiFi.status() != WL_CONNECTED || websocketConnected) {
        return;
    }

    const unsigned long now = millis();
    if (now - lastHttpFallbackPoll < httpFallbackIntervalMs) {
        return;
    }
    lastHttpFallbackPoll = now;

    HTTPClient http;
    http.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
    http.setTimeout(1500);

    // WiFiClientSecure must outlive http.GET() — keep it on function scope.
    WiFiClientSecure secureClient;
    secureClient.setInsecure();

    int statusCode = -1;
    if (useSecureHttpFallback) {
        http.begin(secureClient, httpFallbackHost, httpFallbackPort, httpLatestPath, true);
    } else {
        String url = String("http://") + httpFallbackHost + ":" + String(httpFallbackPort) + httpLatestPath;
        http.begin(url);
    }

    statusCode = http.GET();
    if (statusCode == HTTP_CODE_OK) {
        String payload = http.getString();
        handleControllerPayload(reinterpret_cast<const uint8_t*>(payload.c_str()), payload.length());
    } else {
        Serial.printf("[http-fallback] GET failed: %d\n", statusCode);
    }
    http.end();
}

void connectWifi() {
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);

    currentState = ConnectionState::WIFI_CONNECTING;
    Serial.print("[wifi] connecting");
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
        delay(500);
        Serial.print('.');
        updateLed();
        attempts++;
    }
    Serial.println();
    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("[wifi] connected, ip=%s\n", WiFi.localIP().toString().c_str());
        setStatusMessage(String("IP ") + WiFi.localIP().toString());
        currentState = ConnectionState::WIFI_CONNECTED_WS_DISCONNECTED;
    } else {
        Serial.println("[wifi] FAILED to connect!");
        setStatusMessage("WiFi failed");
        currentState = ConnectionState::ERROR;
    }
}

void initializeMpu9265() {
    if (mpu9250.begin()) {
        Serial.println("[mpu9250] initialization successful");
        mpu9250.setAccelRange(MPU9250::ACCEL_RANGE_8G);
        mpu9250.setGyroRange(MPU9250::GYRO_RANGE_500DPS);
        mpu9250.setSrd(9);  // Sample rate divider (0-255)
        imuInitialized = true;
        lastImuReadTime = millis();
        Serial.println("[mpu9250] config: accel ±8G, gyro ±500°/s");
    } else {
        Serial.println("[mpu9250] initialization FAILED");
        imuInitialized = false;
        setStatusMessage("MPU-9250 init failed");
    }
}

void readImuData() {
    if (!imuInitialized) return;
    
    unsigned long now = millis();
    
    // Read accelerometer data
    if (mpu9250.readSensor()) {
        float rawAccX = mpu9250.getAccelX_mss() / 9.81f;  // Convert m/s² to g
        float rawAccY = mpu9250.getAccelY_mss() / 9.81f;
        float rawAccZ = mpu9250.getAccelZ_mss() / 9.81f;

        currentAccX = rawAccX;
        currentAccY = rawAccY;
        currentAccZ = rawAccZ;

        // Remove stationary bias (includes gravity) so stillness ~= 0g
        float linAccX = rawAccX - accelBiasX;
        float linAccY = rawAccY - accelBiasY;
        float linAccZ = rawAccZ - accelBiasZ;
        float linAccMag = sqrt(linAccX * linAccX + linAccY * linAccY + linAccZ * linAccZ);

        // Deadband to suppress noise when standing still
        if (linAccMag < 0.02f) {
            linAccMag = 0.0f;
        }

        // Simple velocity integration: v = v + a * dt (in m/s)
        if (lastImuReadTime > 0) {
            float dt = (now - lastImuReadTime) / 1000.0f;  // Convert ms to seconds
            float accelMs2 = linAccMag * 9.81f;  // Convert g to m/s²
            velocityMagnitude += accelMs2 * dt;

            // Apply damping to prevent drift (friction simulation)
            velocityMagnitude *= 0.98f;
            if (velocityMagnitude < 0.05f) velocityMagnitude = 0.0f;
        }
        lastImuReadTime = now;
    }
}

void calibrateImuBias() {
    if (!imuInitialized) return;

    const int sampleCount = 200;
    float sumX = 0.0f;
    float sumY = 0.0f;
    float sumZ = 0.0f;

    for (int i = 0; i < sampleCount; ++i) {
        if (mpu9250.readSensor()) {
            sumX += mpu9250.getAccelX_mss() / 9.81f;
            sumY += mpu9250.getAccelY_mss() / 9.81f;
            sumZ += mpu9250.getAccelZ_mss() / 9.81f;
        }
        delay(5);
    }

    accelBiasX = sumX / sampleCount;
    accelBiasY = sumY / sampleCount;
    accelBiasZ = sumZ / sampleCount;
    imuCalibrated = true;
}

void sendTelemetry() {
    if (!websocketConnected || !imuInitialized) return;
    
    unsigned long now = millis();
    if (now - lastTelemetrySend < telemetryIntervalMs) {
        return;
    }
    lastTelemetrySend = now;
    
    // Create telemetry payload
    JsonDocument doc;
    doc["type"] = "telemetry";
    
    // Speed data
    JsonObject speed = doc["speed"].to<JsonObject>();
    speed["velocity_ms"] = velocityMagnitude;
    speed["velocity_kmh"] = velocityMagnitude * 3.6f;
    
    // Acceleration data
    JsonObject accel = doc["acceleration"].to<JsonObject>();
    accel["x_g"] = currentAccX;
    accel["y_g"] = currentAccY;
    accel["z_g"] = currentAccZ;
    
    // Control state
    JsonObject control = doc["control"].to<JsonObject>();
    control["steering"] = lastSteering;
    control["throttle"] = lastThrottle;
    control["brake"] = lastBrake;
    
    // Serialize and send
    String jsonString;
    serializeJson(doc, jsonString);
    webSocket.sendTXT(jsonString);
    
    Serial.printf("[telemetry] sent: speed=%.2f m/s (%.1f km/h), accel=(%.2f, %.2f, %.2f)g\n",
                  velocityMagnitude, velocityMagnitude * 3.6f, currentAccX, currentAccY, currentAccZ);
}

void updateStepperTimer(float speed) {
    float absSpeed = fabsf(speed);
    if (absSpeed < 0.5f) {
        portENTER_CRITICAL(&stepperMux);
        isrStepperDirection = 0;
        timerAlarmDisable(stepperTimer);
        portEXIT_CRITICAL(&stepperMux);
        disableStepperOutputs();
        return;
    }
    uint64_t intervalUs = static_cast<uint64_t>(1000000.0f / absSpeed);
    if (intervalUs < 100) intervalUs = 100;
    int8_t dir = speed > 0.0f ? 1 : -1;
    portENTER_CRITICAL(&stepperMux);
    timerAlarmDisable(stepperTimer);
    timerAlarmWrite(stepperTimer, intervalUs, true);
    isrStepperDirection = dir;
    timerAlarmEnable(stepperTimer);
    portEXIT_CRITICAL(&stepperMux);
}

void serviceStepperDrive() {
    if (!useStepperDrive) {
        return;
    }

    const unsigned long now = millis();
    float targetSpeed = (activeStepperDrive / 100.0f) * stepperMaxSpeedStepsPerSec;

    if (stepperDrivePending && (now - lastStepperCommandApplied >= stepperCommandGraceMs)) {
        activeStepperDrive = pendingStepperDrive;
        targetSpeed = (activeStepperDrive / 100.0f) * stepperMaxSpeedStepsPerSec;
        lastStepperCommandApplied = now;
        stepperDrivePending = false;
    }

    if (activeStepperDrive != 0.0f && (now - lastStepperCommandReceived >= stepperStopTimeoutMs)) {
        activeStepperDrive = 0.0f;
        targetSpeed = 0.0f;
    }

    if (lastStepperSpeedUpdate == 0) {
        lastStepperSpeedUpdate = now;
    }

    const float elapsedSeconds = (now - lastStepperSpeedUpdate) / 1000.0f;
    lastStepperSpeedUpdate = now;
    const float maxSpeedDelta = stepperAccelerationStepsPerSec2 * elapsedSeconds;

    if (currentStepperSpeed < targetSpeed) {
        currentStepperSpeed = min(currentStepperSpeed + maxSpeedDelta, targetSpeed);
    } else if (currentStepperSpeed > targetSpeed) {
        currentStepperSpeed = max(currentStepperSpeed - maxSpeedDelta, targetSpeed);
    }

    if (fabsf(currentStepperSpeed) < 0.01f) {
        currentStepperSpeed = 0.0f;
    }

    updateStepperTimer(currentStepperSpeed);
}
} // namespace

void setup() {
    Serial.begin(115200);
    delay(500);

    // Run a quick AES-128 ECB self-test to validate hardware wrapper
    {
        uint8_t plain[16];
        memcpy(plain, "0123456789ABCDEF", 16);
        uint8_t cipher[16];
        uint8_t recovered[16];
        aes128_encrypt_block(aes_key, plain, cipher);
        aes128_decrypt_block(aes_key, cipher, recovered);

        bool ok = (memcmp(plain, recovered, 16) == 0);
        Serial.print("[aes] self-test: ");
        Serial.println(ok ? "OK" : "FAILED");
        if (!ok) {
            Serial.println("[aes] plaintext:");
            for (int i = 0; i < 16; ++i) Serial.printf("%02X", plain[i]);
            Serial.println();
            Serial.println("[aes] recovered:");
            for (int i = 0; i < 16; ++i) Serial.printf("%02X", recovered[i]);
            Serial.println();
            setStatusMessage("AES self-test failed");
        }
    }

    // Setup LED
    pinMode(builtInLedPin, OUTPUT);
    digitalWrite(builtInLedPin, LOW);

    initializeDisplay();
    
    // Show custom RC car splash screen
    drawBootScreen();
    delay(1500);

    // Setup steering servo PWM
    updateBootStatus("Servo setup...", 20);
    ledcSetup(steeringChannel, pwmFrequency, pwmResolution);
    ledcAttachPin(steeringServoPin, steeringChannel);
    writePulseMicros(steeringChannel, 1500);  // Center position (90 degrees)
    delay(200);

    // Setup drive PWM
    updateBootStatus("Drive setup...", 40);
    if (driveOutputPin >= 0 && !useStepperDrive) {
        ledcSetup(driveChannel, pwmFrequency, pwmResolution);
        ledcAttachPin(driveOutputPin, driveChannel);
        writePulseMicros(driveChannel, 1500);
        delay(200);
    }

    // Initialize stepper if enabled
    if (useStepperDrive) {
        pinMode(stepperPin1, OUTPUT);
        pinMode(stepperPin2, OUTPUT);
        pinMode(stepperPin3, OUTPUT);
        pinMode(stepperPin4, OUTPUT);
        disableStepperOutputs();
        isrStepperPhase = 0;

        // Half-step phase table: precompute GPIO bitmasks so the ISR
        // reads only from DRAM (no flash access = no LoadProhibited crash).
        const uint8_t phaseTable[8][4] = {
            {1, 0, 0, 0},
            {1, 1, 0, 0},
            {0, 1, 0, 0},
            {0, 1, 1, 0},
            {0, 0, 1, 0},
            {0, 0, 1, 1},
            {0, 0, 0, 1},
            {1, 0, 0, 1},
        };
        const int pins[4] = {stepperPin1, stepperPin2, stepperPin3, stepperPin4};
        for (int i = 0; i < 8; ++i) {
            uint32_t setMask = 0, clrMask = 0;
            for (int j = 0; j < 4; ++j) {
                if (phaseTable[i][j]) {
                    setMask |= (1U << pins[j]);
                } else {
                    clrMask |= (1U << pins[j]);
                }
            }
            stepperSetMask[i] = setMask;
            stepperClrMask[i] = clrMask;
        }

        stepperTimer = timerBegin(2, 80, true);
        timerAttachInterrupt(stepperTimer, &onStepperTimer, true);
        timerAlarmWrite(stepperTimer, 2000, true);
        timerAlarmDisable(stepperTimer);
        Serial.println("[stepper] initialized (8-phase half-step, hw timer ISR)");
    }

    // Initialize MPU-9265 IMU
    updateBootStatus("IMU setup...", 50);
    initializeMpu9265();
    calibrateImuBias();
    delay(200);
    connectWifi();
    if (WiFi.status() == WL_CONNECTED) {
        updateBootStatus("OTA setup...", 75);
        setupOta();
        setupWebUpdateServer();
    }

    Serial.printf("[setup] connecting websocket to %s://%s:%d%s\n", 
                  useSecureWebSocket ? "wss" : "ws", host, port, websocketPath);

    updateBootStatus("WebSocket init...", 90);
    // ArduinoWebSockets sends "Origin: file://" by default. Spring/OKD rejects that.
    webSocket.setExtraHeaders("");
    
    if (useSecureWebSocket) {
        webSocket.beginSSL(host, port, websocketPath, websocketFingerprint, "");
    } else {
        webSocket.begin(host, port, websocketPath, "");
    }
    webSocket.onEvent(webSocketEvent);
    webSocket.setReconnectInterval(websocketReconnectMs);
    
    updateBootStatus("Boot complete!", 100);
    delay(500);
    
    Serial.println("[setup] complete, websocket connecting...");
    
    // Give WebSocket time to attempt connection
    delay(1500);

    lastControllerInputReceived = millis();
}

void loop() {
    serviceControllerTimeouts();

    if (WiFi.status() == WL_CONNECTED) {
        ArduinoOTA.handle();
        webServer.handleClient();
    }
    webSocket.loop();
    pollHttpFallback();
    if (!lowPowerModeActive) {
        readImuData();
    }
    serviceStepperDrive();
    if (!lowPowerModeActive) {
        sendTelemetry();
    }
    updateLed();
    if (!lowPowerModeActive) {
        updateDisplay();
    }
}
