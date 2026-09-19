#pragma once

#include <Arduino.h>
#include <Adafruit_MPU6050.h>

class GyroController
{
public:
    struct Reading
    {
        unsigned long timestampMs = 0;
        float accelX = 0.0f;
        float accelY = 0.0f;
        float accelZ = 0.0f;
        float gyroX = 0.0f;
        float gyroY = 0.0f;
        float gyroZ = 0.0f;
        float roll = 0.0f;
        float pitch = 0.0f;
    };

    bool begin(TwoWire &wire, uint8_t address = 0x68);
    bool update();

    bool isReady() const;
    const Reading &latestReading() const;

private:
    Adafruit_MPU6050 mpu;
    bool ready = false;
    bool hasSample = false;
    unsigned long lastUpdateMs = 0;
    Reading latest;

    static constexpr float filterAlpha = 0.96f;
};