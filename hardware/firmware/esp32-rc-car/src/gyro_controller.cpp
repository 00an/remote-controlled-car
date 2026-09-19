#include "gyro_controller.h"

#include <Adafruit_Sensor.h>
#include <math.h>

bool GyroController::begin(TwoWire &wire, uint8_t address)
{
    ready = false;
    hasSample = false;
    lastUpdateMs = millis();
    latest = Reading{};

    if (!mpu.begin(address, &wire))
        return false;

    mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
    mpu.setGyroRange(MPU6050_RANGE_500_DEG);
    mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);

    ready = true;
    return true;
}

bool GyroController::update()
{
    if (!ready)
        return false;

    sensors_event_t accel;
    sensors_event_t gyro;
    sensors_event_t temp;
    mpu.getEvent(&accel, &gyro, &temp);

    const unsigned long now = millis();
    const float dt = hasSample ? static_cast<float>(now - lastUpdateMs) / 1000.0f : 0.0f;
    lastUpdateMs = now;

    const float radToDeg = 57.29577951308232f;
    const float accelX = accel.acceleration.x;
    const float accelY = accel.acceleration.y;
    const float accelZ = accel.acceleration.z;

    const float accelRoll = atan2f(accelY, accelZ) * radToDeg;
    const float accelPitch = atan2f(-accelX, sqrtf(accelY * accelY + accelZ * accelZ)) * radToDeg;

    latest.timestampMs = now;
    latest.accelX = accelX;
    latest.accelY = accelY;
    latest.accelZ = accelZ;
    latest.gyroX = gyro.gyro.x;
    latest.gyroY = gyro.gyro.y;
    latest.gyroZ = gyro.gyro.z;

    if (!hasSample)
    {
        latest.roll = accelRoll;
        latest.pitch = accelPitch;
        hasSample = true;
        return true;
    }

    const float gyroRollRate = gyro.gyro.x * radToDeg;
    const float gyroPitchRate = gyro.gyro.y * radToDeg;

    latest.roll = filterAlpha * (latest.roll + gyroRollRate * dt) + (1.0f - filterAlpha) * accelRoll;
    latest.pitch = filterAlpha * (latest.pitch + gyroPitchRate * dt) + (1.0f - filterAlpha) * accelPitch;

    return true;
}

bool GyroController::isReady() const
{
    return ready;
}

const GyroController::Reading &GyroController::latestReading() const
{
    return latest;
}