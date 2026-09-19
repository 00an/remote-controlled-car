#include <Arduino.h>
#include <ArduinoJson.h>
#include <ArduinoOTA.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Update.h>
#include <RCSwitch.h>
#include <WebServer.h>
#include <WebSocketsClient.h>
#include <WiFiClientSecure.h>
#include <WiFi.h>
#include <esp_now.h>
#include <esp32-hal-cpu.h>
#include <ESP32Servo.h>
#include <Wire.h>
#include <time.h>

#include "config.h"
#include "gyro_controller.h"

namespace
{
    constexpr int websocketReconnectMs = 5000;
    constexpr unsigned long displayUpdateIntervalMs = 500;

    WebSocketsClient webSocket;
    WebServer webServer(80);
    RCSwitch rfTransmitter;
    Servo steeringServo;
    GyroController gyroController;
    bool gyroTelemetryActive = false;
    bool gyroEspNowActive = false;
    unsigned long lastGyroTelemetryUpdate = 0;
    unsigned long lastTelemetryIndicatorUpdate = 0;
    uint32_t gyroTelemetrySequence = 0;

    struct GyroTelemetryPacket
    {
        uint32_t sequence;
        unsigned long timestampMs;
        float accelX;
        float accelY;
        float accelZ;
        float gyroX;
        float gyroY;
        float gyroZ;
        float roll;
        float pitch;
    };

    enum class ConnectionState
    {
        WIFI_CONNECTING,
        WIFI_CONNECTED_WS_DISCONNECTED,
        WEBSOCKET_CONNECTED,
        ERROR
    };

    enum class DisplayMode
    {
        DEBUG_INFO,
        ERROR_DISPLAY,
        BOOT_SCREEN
    };

    ConnectionState currentState = ConnectionState::WIFI_CONNECTING;
    DisplayMode currentDisplayMode = DisplayMode::BOOT_SCREEN;
    unsigned long lastLedUpdate = 0;
    bool websocketConnected = false;
    float lastSteering = 0.0f;
    float lastThrottle = 0.0f;
    float lastBrake = 0.0f;
    String lastStatusMessage;
    unsigned long lastControllerLogMs = 0;
    unsigned long lastRfTelemetryUpdate = 0;
    float pendingRfDrive = 0.0f;
    bool pendingRfTelemetry = false;
    unsigned long lastDisplayUpdate = 0;
    unsigned long lastControllerInputReceived = 0;
    bool controllerSignalTimedOut = false;
    bool lowPowerModeActive = false;
    uint32_t activeCpuFrequencyMhz = 0;

    int telemetryStatusCode = 0;

    void updateTelemetryStatusCode(float drive)
    {
        if (controllerSignalTimedOut)
        {
            telemetryStatusCode = 3;
        }
        else if (fabsf(drive) > motorDeadband)
        {
            telemetryStatusCode = 2;
        }
        else if (websocketConnected)
        {
            telemetryStatusCode = 1;
        }
        else
        {
            telemetryStatusCode = 0;
        }
    }

    void sendRfTelemetry(float drive)
    {
        if (!enableRfTelemetry)
        {
            return;
        }

        const unsigned long now = millis();
        if (now - lastRfTelemetryUpdate < 120)
        {
            return;
        }

        lastRfTelemetryUpdate = now;
        updateTelemetryStatusCode(drive);

        const float estimatedSpeedKmh = constrain(fabsf(drive) * 0.4f, 0.0f, 99.99f);
        const unsigned long payload = static_cast<unsigned long>(telemetryStatusCode) * 10000UL + static_cast<unsigned long>(lroundf(estimatedSpeedKmh * 100.0f));
        rfTransmitter.send(payload, 24);
    }

    void queueRfTelemetry(float drive)
    {
        if (!enableRfTelemetry)
        {
            return;
        }

        pendingRfDrive = drive;
        pendingRfTelemetry = true;
    }

    void serviceRfTelemetry()
    {
        if (!enableRfTelemetry)
        {
            return;
        }

        if (!pendingRfTelemetry)
        {
            return;
        }

        const unsigned long now = millis();
        if (now - lastRfTelemetryUpdate < 120)
        {
            return;
        }

        sendRfTelemetry(pendingRfDrive);
        pendingRfTelemetry = false;
    }

    const char *connectionStateLabel(ConnectionState state)
    {
        switch (state)
        {
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

    void setStatusMessage(const String &message)
    {
        lastStatusMessage = message;
    }

    void setClock()
    {
        configTime(0, 0, "pool.ntp.org", "time.nist.gov");

        Serial.print(F("[time] waiting for NTP sync"));
        time_t nowSecs = time(nullptr);
        while (nowSecs < 8 * 3600 * 2)
        {
            delay(500);
            Serial.print('.');
            nowSecs = time(nullptr);
        }

        Serial.println();
        struct tm timeinfo;
        gmtime_r(&nowSecs, &timeinfo);
        Serial.print(F("[time] current time: "));
        Serial.print(asctime(&timeinfo));
    }

    // ── Low-power idle mode ──────────────────────────────────────────────

    void enterLowPowerMode()
    {
        if (lowPowerModeActive || !enableIdleLowPowerMode)
            return;

        lowPowerModeActive = true;
        WiFi.setSleep(true);
        activeCpuFrequencyMhz = getCpuFrequencyMhz();
        setCpuFrequencyMhz(idleLowPowerCpuMhz);
        Serial.println("[power] entering low-power idle mode");
        setStatusMessage("Low-power idle");
    }

    void exitLowPowerMode()
    {
        if (!lowPowerModeActive)
            return;

        lowPowerModeActive = false;
        if (activeCpuFrequencyMhz > 0)
        {
            setCpuFrequencyMhz(activeCpuFrequencyMhz);
        }
        WiFi.setSleep(false);

        lastDisplayUpdate = 0;
        Serial.println("[power] exiting low-power idle mode");
        setStatusMessage("Active");
    }

    void registerControllerInput()
    {
        lastControllerInputReceived = millis();
        controllerSignalTimedOut = false;
        exitLowPowerMode();
    }

    void setupGyroTelemetry()
    {
        if (!enableGyroTelemetry)
            return;

        gyroTelemetryActive = gyroController.begin(Wire, gyroI2cAddress);
        if (gyroTelemetryActive)
        {
            Serial.println("[gyro] MPU6050 ready");
        }
        else
        {
            Serial.println("[gyro] MPU6050 not found; continuing without gyro telemetry");
        }
    }

    void setupGyroEspNow()
    {
        if (!enableGyroEspNowTelemetry)
            return;

        if (esp_now_init() != ESP_OK)
        {
            Serial.println("[gyro] ESP-NOW init failed");
            gyroEspNowActive = false;
            return;
        }

        esp_now_peer_info_t peerInfo{};
        memcpy(peerInfo.peer_addr, gyroTelemetryReceiverMac, sizeof(gyroTelemetryReceiverMac));
        peerInfo.channel = 0;
        peerInfo.encrypt = false;
        peerInfo.ifidx = WIFI_IF_STA;

        if (esp_now_add_peer(&peerInfo) != ESP_OK)
        {
            Serial.println("[gyro] ESP-NOW add peer failed");
            gyroEspNowActive = false;
            return;
        }

        gyroEspNowActive = true;
        Serial.println("[gyro] ESP-NOW telemetry ready");
    }

    void pulseTelemetryIndicator()
    {
        lastTelemetryIndicatorUpdate = millis();
        digitalWrite(telemetryIndicatorLedPin, HIGH);
    }

    void serviceTelemetryIndicator()
    {
        if (lastTelemetryIndicatorUpdate == 0)
        {
            digitalWrite(telemetryIndicatorLedPin, LOW);
            return;
        }

        const unsigned long elapsedMs = millis() - lastTelemetryIndicatorUpdate;
        if (elapsedMs >= telemetryIndicatorPulseMs)
        {
            digitalWrite(telemetryIndicatorLedPin, LOW);
            lastTelemetryIndicatorUpdate = 0;
        }
    }

    void sendGyroTelemetry(const GyroController::Reading &reading)
    {
        if (!gyroEspNowActive)
            return;

        GyroTelemetryPacket packet{};
        packet.sequence = ++gyroTelemetrySequence;
        packet.timestampMs = reading.timestampMs;
        packet.accelX = reading.accelX;
        packet.accelY = reading.accelY;
        packet.accelZ = reading.accelZ;
        packet.gyroX = reading.gyroX;
        packet.gyroY = reading.gyroY;
        packet.gyroZ = reading.gyroZ;
        packet.roll = reading.roll;
        packet.pitch = reading.pitch;

        if (esp_now_send(gyroTelemetryReceiverMac, reinterpret_cast<const uint8_t *>(&packet), sizeof(packet)) == ESP_OK)
        {
            pulseTelemetryIndicator();
        }
    }

    void serviceGyroTelemetry()
    {
        if (!gyroTelemetryActive)
            return;

        const unsigned long now = millis();
        if (now - lastGyroTelemetryUpdate < gyroTelemetryIntervalMs)
            return;

        lastGyroTelemetryUpdate = now;
        if (!gyroController.update())
            return;

        sendGyroTelemetry(gyroController.latestReading());
    }

    // ── OTA (ArduinoOTA + web update server) ─────────────────────────────

    bool isWebUpdateAuthorized()
    {
        if (strlen(otaPassword) == 0)
            return true;
        return webServer.authenticate("admin", otaPassword);
    }

    String buildUpdatePage()
    {
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

    void handleWebUpdateRoot()
    {
        if (!isWebUpdateAuthorized())
        {
            webServer.requestAuthentication();
            return;
        }
        webServer.send(200, "text/html", buildUpdatePage());
    }

    void handleWebUpdateUpload()
    {
        if (!isWebUpdateAuthorized())
            return;

        HTTPUpload &upload = webServer.upload();

        if (upload.status == UPLOAD_FILE_START)
        {
            Serial.printf("[web-ota] upload start: %s\n", upload.filename.c_str());
            if (!Update.begin(UPDATE_SIZE_UNKNOWN))
            {
                Update.printError(Serial);
            }
        }
        else if (upload.status == UPLOAD_FILE_WRITE)
        {
            if (Update.write(upload.buf, upload.currentSize) != upload.currentSize)
            {
                Update.printError(Serial);
            }
        }
        else if (upload.status == UPLOAD_FILE_END)
        {
            if (Update.end(true))
            {
                Serial.printf("[web-ota] upload complete: %u bytes\n", upload.totalSize);
            }
            else
            {
                Update.printError(Serial);
            }
        }
    }

    void setupWebUpdateServer()
    {
        webServer.on("/", HTTP_GET, handleWebUpdateRoot);
        webServer.on("/update", HTTP_GET, handleWebUpdateRoot);
        webServer.on(
            "/update",
            HTTP_POST,
            []()
            {
                const bool ok = !Update.hasError();
                webServer.send(ok ? 200 : 500, "text/plain", ok ? "Update successful. Rebooting..." : "Update failed.");
                delay(500);
                if (ok)
                    ESP.restart();
            },
            handleWebUpdateUpload);

        webServer.begin();
        Serial.println("[web-ota] update page ready at /update");
    }

    void setupOta()
    {
        ArduinoOTA.setHostname(otaHostname);

        if (strlen(otaPassword) > 0)
        {
            ArduinoOTA.setPassword(otaPassword);
        }

        ArduinoOTA
            .onStart([]()
                     { Serial.println("[ota] start"); })
            .onEnd([]()
                   { Serial.println("[ota] end"); })
            .onProgress([](unsigned int progress, unsigned int total)
                        { Serial.printf("[ota] progress: %u%%\r", (progress * 100U) / total); })
            .onError([](ota_error_t error)
                     { Serial.printf("[ota] error[%u]\n", error); });

        ArduinoOTA.begin();
        Serial.printf("[ota] ready, hostname=%s\n", otaHostname);
    }

    // ── LED status indicator ─────────────────────────────────────────────

    void updateLed()
    {
        if (lowPowerModeActive)
        {
            digitalWrite(builtInLedPin, LOW);
            return;
        }

        unsigned long now = millis();
        if (now - lastLedUpdate < 50)
            return;
        lastLedUpdate = now;

        static bool blink = false;
        switch (currentState)
        {
        case ConnectionState::WIFI_CONNECTING:
            blink = (now / 100) % 2;
            digitalWrite(builtInLedPin, blink ? HIGH : LOW);
            break;
        case ConnectionState::WIFI_CONNECTED_WS_DISCONNECTED:
            blink = (now / 300) % 2;
            digitalWrite(builtInLedPin, blink ? HIGH : LOW);
            break;
        case ConnectionState::WEBSOCKET_CONNECTED:
        {
            int breathePhase = (now / 50) % 40;
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

    // ── Servo pulse helper ────────────────────────────────────────────────

    void writePulseMicros(int pulseMicros)
    {
        pulseMicros = constrain(pulseMicros, 500, 2500);
        steeringServo.writeMicroseconds(pulseMicros);
    }

    // ── H-bridge motor control ───────────────────────────────────────────

    void setMotorDrive(float drive)
    {
        if (drive > motorDeadband)
        {
            digitalWrite(motorIn1Pin, HIGH);
            digitalWrite(motorIn2Pin, LOW);
            int pwm = static_cast<int>((drive / 100.0f) * 255);
            pwm = constrain(pwm, 0, 255);
            ledcWrite(motorPwmChannel, pwm);
            Serial.printf("[motor] FWD IN1=HIGH IN2=LOW PWM=%d\n", pwm);
        }
        else if (drive < -motorDeadband)
        {
            digitalWrite(motorIn1Pin, LOW);
            digitalWrite(motorIn2Pin, HIGH);
            int pwm = static_cast<int>((-drive / 100.0f) * 255);
            pwm = constrain(pwm, 0, 255);
            ledcWrite(motorPwmChannel, pwm);
            Serial.printf("[motor] REV IN1=LOW IN2=HIGH PWM=%d\n", pwm);
        }
        else
        {
            digitalWrite(motorIn1Pin, LOW);
            digitalWrite(motorIn2Pin, LOW);
            ledcWrite(motorPwmChannel, 0);
        }
    }

    void stopMotor()
    {
        digitalWrite(motorIn1Pin, LOW);
        digitalWrite(motorIn2Pin, LOW);
        ledcWrite(motorPwmChannel, 0);
    }

    // ── Controller state application ─────────────────────────────────────

    void applyControllerState(float steering, float throttle, float brake)
    {
        steering = constrain(steering, -100.0f, 100.0f);
        throttle = constrain(throttle, 0.0f, 100.0f);
        brake = constrain(brake, 0.0f, 100.0f);
        lastSteering = steering;
        lastThrottle = throttle;
        lastBrake = brake;

        const float steeringNorm = (steeringFlipDirection ? -steering : steering) / 100.0f;
        const float steeringSpan = (steeringPulseMaxUs - steeringPulseMinUs) / 2.0f;
        const int steeringPulse = static_cast<int>(lroundf(steeringPulseCenterUs + steeringNorm * steeringSpan));
        writePulseMicros(steeringPulse);

        const int steeringAngle = static_cast<int>(lroundf((steeringNorm + 1.0f) * 90.0f));

        const float drive = throttle - brake;
        setMotorDrive(drive);
            if (enableRfTelemetry)
            {
                queueRfTelemetry(drive);
            }

        const unsigned long now = millis();
        if (now - lastControllerLogMs >= 100)
        {
            lastControllerLogMs = now;
            Serial.printf("[controller] steering=%.1f (angle=%d, pulse=%d us) throttle=%.1f brake=%.1f drive=%.1f\n",
                          steering, steeringAngle, steeringPulse, throttle, brake, drive);
        }
    }

    // ── Failsafe timeout ─────────────────────────────────────────────────

    void serviceControllerTimeouts()
    {
        const unsigned long now = millis();
        if (lastControllerInputReceived == 0)
        {
            lastControllerInputReceived = now;
            return;
        }

        const unsigned long idleForMs = now - lastControllerInputReceived;
        if (!controllerSignalTimedOut && idleForMs >= controllerSignalTimeoutMs)
        {
            applyControllerState(0.0f, 0.0f, 0.0f);
            controllerSignalTimedOut = true;
            setStatusMessage("Input timeout -> neutral");
            Serial.println("[controller] input timeout -> neutral outputs");
        }

        if (enableIdleLowPowerMode && !lowPowerModeActive && idleForMs >= idleLowPowerTimeoutMs)
        {
            enterLowPowerMode();
        }
    }

    // ── WebSocket JSON handling ──────────────────────────────────────────

    void handleControllerPayload(const uint8_t *payload, size_t length)
    {
        JsonDocument doc;
        DeserializationError error = deserializeJson(doc, payload, length);
        if (error)
        {
            Serial.printf("[ws] JSON parse error: %s\n", error.c_str());
            setStatusMessage(String("JSON error: ") + error.c_str());
            return;
        }

        JsonArray axes = doc["axes"].as<JsonArray>();
        const float steeringAxis = axes.size() > 0 ? axes[0].as<float>() : 0.0f;
        const float gasAxis = axes.size() > 1 ? axes[1].as<float>() : 1.0f;
        const float reverseAxis = axes.size() > 2 ? axes[2].as<float>() : 1.0f;

        const float steering = steeringAxis * 100.0f;
        const float throttle = constrain(((1.0f - gasAxis) / 2.0f) * 100.0f, 0.0f, 100.0f);
        const float reverse = constrain(((1.0f - reverseAxis) / 2.0f) * 100.0f, 0.0f, 100.0f);

        registerControllerInput();
        applyControllerState(steering, throttle, reverse);
    }

    void webSocketEvent(WStype_t type, uint8_t *payload, size_t length)
    {
        switch (type)
        {
        case WStype_DISCONNECTED:
            if (length > 0)
            {
                Serial.printf("[ws] disconnected: %.*s\n", static_cast<int>(length), payload);
                setStatusMessage(String("WS down: ") + String(reinterpret_cast<char *>(payload), length));
            }
            else
            {
                Serial.println("[ws] disconnected");
                setStatusMessage("WS disconnected");
            }
            websocketConnected = false;
            stopMotor();
            writePulseMicros(steeringPulseCenterUs);
            if (WiFi.status() == WL_CONNECTED)
            {
                currentState = ConnectionState::WIFI_CONNECTED_WS_DISCONNECTED;
            }
            else
            {
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
            if (length > 0)
            {
                Serial.printf("[ws] error: %.*s\n", static_cast<int>(length), payload);
                setStatusMessage(String("WS error: ") + String(reinterpret_cast<char *>(payload), length));
            }
            else
            {
                Serial.println("[ws] error");
                setStatusMessage("WS error");
            }
            websocketConnected = false;
            stopMotor();
            writePulseMicros(steeringPulseCenterUs);
            currentState = ConnectionState::ERROR;
            break;
        default:
            break;
        }
    }

    // ── WiFi ─────────────────────────────────────────────────────────────

    void connectWifi()
    {
        WiFi.mode(WIFI_STA);
        WiFi.begin(ssid, password);

        currentState = ConnectionState::WIFI_CONNECTING;
        Serial.print("[wifi] connecting");
        int attempts = 0;
        while (WiFi.status() != WL_CONNECTED && attempts < 20)
        {
            delay(500);
            Serial.print('.');
            updateLed();
            attempts++;
        }
        Serial.println();
        if (WiFi.status() == WL_CONNECTED)
        {
            Serial.printf("[wifi] connected, ip=%s\n", WiFi.localIP().toString().c_str());
            setStatusMessage(String("IP ") + WiFi.localIP().toString());
            currentState = ConnectionState::WIFI_CONNECTED_WS_DISCONNECTED;
        }
        else
        {
            Serial.println("[wifi] FAILED to connect!");
            setStatusMessage("WiFi failed");
            currentState = ConnectionState::ERROR;
        }
    }

} // namespace

void setup()
{
    Serial.begin(115200);
    Serial.setDebugOutput(true);
    delay(500);

    pinMode(builtInLedPin, OUTPUT);
    digitalWrite(builtInLedPin, LOW);

    pinMode(telemetryIndicatorLedPin, OUTPUT);
    digitalWrite(telemetryIndicatorLedPin, LOW);

    if (enableRfTelemetry)
    {
        rfTransmitter.enableTransmit(rfTransmitPin);
        rfTransmitter.setRepeatTransmit(1);
    }

    // H-bridge direction pins
    pinMode(motorIn1Pin, OUTPUT);
    pinMode(motorIn2Pin, OUTPUT);
    digitalWrite(motorIn1Pin, LOW);
    digitalWrite(motorIn2Pin, LOW);

    // H-bridge PWM (speed) via LEDC
    ledcSetup(motorPwmChannel, motorPwmFrequency, motorPwmResolution);
    ledcAttachPin(motorEnaPin, motorPwmChannel);
    ledcWrite(motorPwmChannel, 0);

    delay(1500);

    Wire.begin(oledSdaPin, oledSclPin);
    setupGyroTelemetry();

    // Reserve a dedicated LEDC timer for the servo so its 50 Hz config can
    // never land on the timer the motor PWM uses (motorPwmChannel -> Timer 2).
    ESP32PWM::allocateTimer(0);
    steeringServo.setPeriodHertz(50);
    steeringServo.attach(steeringServoPin, steeringPulseMinUs, steeringPulseMaxUs);
    steeringServo.writeMicroseconds(steeringPulseCenterUs);
    delay(200);

    connectWifi();

    if (WiFi.status() == WL_CONNECTED)
    {
        setupGyroEspNow();
    }

    if (WiFi.status() == WL_CONNECTED)
    {
        setupOta();
        setupWebUpdateServer();
    }

    if (useSecureWebSocket)
    {
        setClock();
    }

    Serial.printf("[setup] connecting websocket to %s://%s:%d%s\n",
                  useSecureWebSocket ? "wss" : "ws", host, port, websocketPath);

    webSocket.setExtraHeaders("");
    webSocket.onEvent(webSocketEvent);

    if (useSecureWebSocket)
    {
        webSocket.beginSslWithBundle(host, port, websocketPath, NULL, "arduino");
    }
    else
    {
        webSocket.begin(host, port, websocketPath);
    }
    webSocket.setReconnectInterval(websocketReconnectMs);

    delay(500);

    Serial.println("[setup] complete, websocket connecting...");
    delay(1500);

    lastControllerInputReceived = millis();
}

void loop()
{
    webSocket.loop();

    serviceControllerTimeouts();
    if (enableRfTelemetry)
    {
        serviceRfTelemetry();
    }
    serviceTelemetryIndicator();
    serviceGyroTelemetry();

    if (WiFi.status() == WL_CONNECTED)
    {
        ArduinoOTA.handle();
        webServer.handleClient();
    }

    updateLed();
}