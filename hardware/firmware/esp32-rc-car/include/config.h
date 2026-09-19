#pragma once

#include "config_secrets.h"

// WebSocket endpoint
const char* host = "esp32-backend-itip-nl-14.apps.okd.ucll.cloud";
const uint16_t port = 443;
const char* websocketPath = "/ws";
const bool useSecureWebSocket = true;
const char* websocketFingerprint = "";

// 433 MHz RF transmitter data pin for status/speed telemetry.
constexpr bool enableRfTelemetry = false;
constexpr int rfTransmitPin = 4;

// OTA
const char* otaHostname = "esp32-rc-car";
const char* otaPassword = "";

// L298N H-bridge motor driver
constexpr int motorEnaPin = 25;   // ENA (PWM speed control)
constexpr int motorIn1Pin = 26;   // IN1 (direction)
constexpr int motorIn2Pin = 27;   // IN2 (direction)
constexpr int motorPwmFrequency = 1000;  // Hz
constexpr int motorPwmResolution = 8;    // 8-bit (0-255)
// LEDC channels are paired by timer (0-1->T0, 2-3->T1, 4-5->T2, 6-7->T3).
// ESP32Servo grabs the next free channel at attach() and typically lands on
// channel 3 (Timer 1). Keep the motor PWM on Timer 2 so the servo's 50 Hz
// timer config can never clash with the motor's 1000 Hz timer config.
constexpr int motorPwmChannel = 4;
constexpr float motorDeadband = 5.0f;    // ignore drive values within +/- this

// Steering servo
constexpr int steeringServoPin = 18;
// steering PWM channel/frequency/resolution removed; using Servo library instead
constexpr int steeringPulseMinUs = 1000;
constexpr int steeringPulseMaxUs = 2000;
constexpr int steeringPulseCenterUs = 1500;
// Original code mapped +steering to the MIN pulse. Keep that direction by
// default; flip to false if the car steers the wrong way after this change.
constexpr bool steeringFlipDirection = true;

// Built-in LED
constexpr int builtInLedPin = 2;

// Gyro telemetry indicator LED
constexpr int telemetryIndicatorLedPin = 23;
constexpr unsigned long telemetryIndicatorPulseMs = 100;

// Gyro / IMU
constexpr bool enableGyroTelemetry = true;
constexpr uint8_t gyroI2cAddress = 0x68;
constexpr unsigned long gyroTelemetryIntervalMs = 100;
constexpr bool enableGyroEspNowTelemetry = true;
constexpr uint8_t gyroTelemetryReceiverMac[6] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

// SBC-OLED01 / SSD1306 I2C display
constexpr int oledWidth = 128;
constexpr int oledHeight = 64;
constexpr int oledResetPin = -1;
constexpr uint8_t oledI2cAddress = 0x3C;
constexpr int oledSdaPin = 21;
constexpr int oledSclPin = 22;

// Controller signal failsafe
constexpr unsigned long controllerSignalTimeoutMs = 800;

// Idle low-power mode
constexpr bool enableIdleLowPowerMode = false;
constexpr unsigned long idleLowPowerTimeoutMs = 5000;
constexpr int idleLowPowerCpuMhz = 80;
