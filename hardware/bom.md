# Bill of Materials

| Part | Qty | Used in | Vendor | Link | Unit Price | Notes |
|------|-----|---------|--------|------|------------|-------|
| ESP32 dev board | 2 | esp32-rc-car, esp32-remote-car | | | | Main microcontroller for each build |
| Nikko NE14 1:14 RC car (Lamborghini Murcielago) chassis | 1 | esp32-rc-car | | | | Donor chassis; motor + steering replaced with ESP32 control |
| L298N H-bridge motor driver | 1 | esp32-rc-car | | | | Drives the DC brush motor |
| Steering servo | 2 | esp32-rc-car, esp32-remote-car | | | | |
| MPU-6050 IMU | 1 | esp32-rc-car | | | | I2C, gyro/accelerometer for telemetry |
| MPU-9250 IMU | 1 | esp32-remote-car | | | | I2C, gyro/accelerometer for telemetry |
| SSD1306 OLED display (128x64, I2C) | 2 | esp32-rc-car, esp32-remote-car | | | | On-board status display |
| 28BYJ-48 stepper motor + ULN2003 driver board | 1 | esp32-remote-car | | | | Optional drive actuator (see `useStepperDrive` in config.h) |
| 433 MHz RF transmitter | 1 | esp32-rc-car | | | | Optional telemetry link, currently disabled by default (`enableRfTelemetry = false`) |

Vendor, link, and pricing columns are left blank — fill in from your own purchase history if
you want a cost total for this build.

TOTAL ESTIMATED COST:
