# Firmware

BOARD: ESP32 dev board (`esp32dev`), two separate projects — `esp32-rc-car` and
`esp32-remote-car`. See `hardware/bom.md` for the full parts list per build.

TOOLCHAIN: [PlatformIO](https://platformio.org/) with the Arduino framework.

SETUP COMMANDS:
```bash
cd hardware/firmware/esp32-rc-car        # or esp32-remote-car
cp include/config_secrets.h.example include/config_secrets.h
# edit config_secrets.h with your Wi-Fi credentials
pio run                                   # build
pio run -t upload                         # flash over USB
pio run -t upload -e esp32dev_rc_car_ota  # flash over Wi-Fi (OTA), esp32-remote-car uses esp32dev_ota
```

PINOUT: pin assignments are project-specific and documented as constants in each project's
`include/config.h` (motor/servo pins, I2C addresses, display pins, etc.) rather than
duplicated here, so they stay in sync with the code.

NOTES: `steering-input/` is a companion Python script (not firmware) that reads a physical
steering wheel/pedal set and forwards input to the backend over HTTP; see its own
`requirements.txt` and `.env.example`.
