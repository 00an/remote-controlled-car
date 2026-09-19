#include <Arduino.h>
#include <Wire.h>
#include <RCSwitch.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

constexpr int ledPin = 2;
constexpr int screenWidth = 128;
constexpr int screenHeight = 64;
constexpr int oledResetPin = -1;
constexpr uint8_t oledAddress = 0x3C;
constexpr int rfReceivePin = 27;
constexpr unsigned long displayRefreshMs = 150;
constexpr unsigned long payloadTimeoutMs = 2500;
constexpr float speedometerThresholdKmh = 5.0f;
constexpr float speedometerMaxKmh = 40.0f;

Adafruit_SSD1306 display(screenWidth, screenHeight, &Wire, oledResetPin);
RCSwitch rf = RCSwitch();

unsigned long lastDisplayUpdate = 0;
unsigned long lastPayloadReceived = 0;
unsigned long lastRawPayload = 0;
float currentSpeedKmh = 0.0f;
int currentStatusCode = 0;
String currentStatusText = "Waiting for RF";

const char *statusLabel(int code)
{
    switch (code)
    {
    case 0:
        return "IDLE";
    case 1:
        return "READY";
    case 2:
        return "DRIVING";
    case 3:
        return "ERROR";
    default:
        return "UNKNOWN";
    }
}

void setTelemetryFromPayload(unsigned long payload)
{
    // Protocol: statusCode * 10000 + speedCentiKmh
    // Example: 20085 = status 2 (DRIVING), speed 0.85 km/h.
    currentStatusCode = static_cast<int>(payload / 10000UL);
    currentSpeedKmh = static_cast<float>(payload % 10000UL) / 100.0f;
    currentStatusText = statusLabel(currentStatusCode);
    lastRawPayload = payload;
    lastPayloadReceived = millis();
}

bool isDriving()
{
    return currentSpeedKmh >= speedometerThresholdKmh || currentStatusCode == 2;
}

void drawWaitingScreen()
{
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    display.setTextWrap(false);

    display.fillRect(0, 0, screenWidth, 10, SSD1306_WHITE);
    display.setTextColor(SSD1306_BLACK);
    display.setCursor(2, 1);
    display.println("RC CAR DISPLAY");

    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 16);
    display.print("RF: ");
    display.println((millis() - lastPayloadReceived) <= payloadTimeoutMs ? "LIVE" : "WAITING");

    display.setCursor(0, 28);
    display.print("Status: ");
    display.println(currentStatusText);

    display.setCursor(0, 40);
    display.print("Speed: ");
    display.print(currentSpeedKmh, 1);
    display.println(" km/h");

    display.setCursor(0, 52);
    display.print("Mode: ");
    display.println("STATUS");

    display.display();
}

void drawSpeedometer()
{
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);

    display.fillRect(0, 0, screenWidth, 8, SSD1306_WHITE);
    display.setTextColor(SSD1306_BLACK);
    display.setTextSize(1);
    display.setCursor(2, 1);
    display.println("SPEEDOMETER");

    display.setTextColor(SSD1306_WHITE);

    const int centerX = 40;
    const int centerY = 40;
    const int radius = 24;

    display.drawCircle(centerX, centerY, radius, SSD1306_WHITE);
    display.drawCircle(centerX, centerY, radius - 1, SSD1306_WHITE);

    display.setTextSize(1);
    display.setCursor(10, 56);
    display.print("0");
    display.setCursor(34, 58);
    display.print("20");
    display.setCursor(56, 46);
    display.print("40");

    const float speedRatio = constrain(currentSpeedKmh / speedometerMaxKmh, 0.0f, 1.0f);
    const float needleAngle = 200.0f + (speedRatio * 140.0f);
    const float needleRad = (needleAngle - 90.0f) * 3.14159f / 180.0f;
    const int needleX = centerX + static_cast<int>(cos(needleRad) * (radius - 4));
    const int needleY = centerY + static_cast<int>(sin(needleRad) * (radius - 4));

    display.drawLine(centerX, centerY, needleX, needleY, SSD1306_WHITE);
    display.drawLine(centerX, centerY, needleX - 1, needleY, SSD1306_WHITE);
    display.fillCircle(centerX, centerY, 2, SSD1306_WHITE);

    display.setTextSize(2);
    display.setCursor(74, 10);
    display.print(currentSpeedKmh, 1);
    display.setTextSize(1);
    display.setCursor(74, 28);
    display.println("km/h");

    display.setCursor(74, 40);
    display.print("RF:");
    display.println((millis() - lastPayloadReceived) <= payloadTimeoutMs ? "LIVE" : "WAITING");

    display.setCursor(74, 52);
    display.print("STS:");
    display.println(currentStatusText);

    display.display();
}

void drawStatusScreen()
{
    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    display.setTextWrap(false);

    display.setCursor(0, 0);
    display.print("Status: ");
    display.println(currentStatusText);

    display.setCursor(0, 12);
    display.print("RF: ");
    display.println((millis() - lastPayloadReceived) <= payloadTimeoutMs ? "LIVE" : "WAITING");

    display.setCursor(0, 24);
    display.print("Speed: ");
    display.print(currentSpeedKmh, 1);
    display.println(" km/h");

    display.setCursor(0, 36);
    display.print("Mode: ");
    display.println(isDriving() ? "SPEEDOMETER" : "STATUS");

    display.setCursor(0, 48);
    display.print("RX:");
    display.print(lastRawPayload);

    display.display();
}

void updateDisplay()
{
    const unsigned long now = millis();
    if (now - lastDisplayUpdate < displayRefreshMs)
    {
        return;
    }

    lastDisplayUpdate = now;

    if (millis() - lastPayloadReceived > payloadTimeoutMs)
    {
        currentStatusText = "Waiting for RF";
    }

    if (isDriving())
    {
        drawSpeedometer();
    }
    else
    {
        drawStatusScreen();
    }
}

void setup()
{
    pinMode(ledPin, OUTPUT);
    digitalWrite(ledPin, LOW);

    Wire.begin(21, 22);

    if (!display.begin(SSD1306_SWITCHCAPVCC, oledAddress))
    {
        while (true)
        {
            delay(1000);
        }
    }

    display.clearDisplay();
    display.setTextColor(SSD1306_WHITE);
    display.setTextSize(1);
    display.setCursor(0, 0);
    display.println("Booting display...");
    display.display();

    rf.enableReceive(rfReceivePin);

    currentStatusText = "RF ready";
    lastPayloadReceived = 0;
}

void loop()
{
    if (rf.available())
    {
        const unsigned long payload = rf.getReceivedValue();
        rf.resetAvailable();

        if (payload > 0)
        {
            setTelemetryFromPayload(payload);
            digitalWrite(ledPin, HIGH);
            delay(10);
            digitalWrite(ledPin, LOW);
        }
    }

    updateDisplay();
}