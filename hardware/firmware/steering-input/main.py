import atexit
import os
import sys
import time

import pygame
import requests
from dotenv import load_dotenv

load_dotenv()

BACKEND_URL = os.getenv("BACKEND_URL")
POLL_HZ = int(os.getenv("POLL_HZ"))

# The .env BACKEND_URL points at /v1/api/controller (the POST endpoint).
# Derive the session base from it so we can hit /session/start and /session/stop.
SESSION_BASE = BACKEND_URL.rstrip("/").rsplit("/", 1)[0]  # drop trailing "/controller"
SESSION_START = f"{SESSION_BASE}/controller/session/start"
SESSION_STOP = f"{SESSION_BASE}/controller/session/stop"

pygame.init()
pygame.joystick.init()

if pygame.joystick.get_count() == 0:
    print("No controller found.")
    sys.exit(1)

joy = pygame.joystick.Joystick(0)
joy.init()
print(f"Controller : {joy.get_name()}")
print(f"POST to    : {BACKEND_URL}  @ {POLL_HZ}Hz")
print(f"Session    : {SESSION_BASE}/controller/session/{{start,stop}}\n")

# --- Pedal axis mapping & calibration -------------------------------------
# The rest of the system (dashboard + ESP32 firmware) expects:
#   axes[0] = steering, axes[1] = throttle, axes[2] = brake
# where a *released* pedal reads +1.0 and a fully pressed pedal reads -1.0
# (throttle% = (1 - axis) / 2 * 100, drive = throttle - brake).
#
# This G29 (without G HUB) does NOT expose separate pedal axes. Instead both
# pedals share a single COMBINED axis that rests at center (~0): gas pushes it
# one way, brake the other. We split that one axis back into throttle + brake so
# brake can drive the movement below zero (reverse).
#
# PEDAL_AXIS      : index of the combined pedal axis (default 1).
# PEDAL_GAS_SIGN  : which direction of the axis is gas. -1 means gas pushes the
#                   axis negative (the G29 default), +1 means gas pushes positive.
# All indices/signs are env-overridable so production wheels with a different
# layout (e.g. real separate axes) can be configured without code changes.
PEDAL_AXIS = int(os.getenv("PEDAL_AXIS", "1"))
PEDAL_GAS_SIGN = int(os.getenv("PEDAL_GAS_SIGN", "-1"))
# Ignore tiny jitter around the resting center so standstill stays exactly 0.
PEDAL_DEADZONE = float(os.getenv("PEDAL_DEADZONE", "0.06"))


def _read_axis(index: int) -> float:
    if 0 <= index < joy.get_numaxes():
        return joy.get_axis(index)
    return 0.0


def calibrate_axis_center(index: int, samples: int = 10) -> float:
    """Return the resting (center) value of an axis, averaged over a few frames."""
    if not (0 <= index < joy.get_numaxes()):
        return 0.0
    total = 0.0
    for _ in range(samples):
        pygame.event.pump()
        total += joy.get_axis(index)
        time.sleep(0.01)
    return total / samples


def split_combined_pedal(raw: float, center: float):
    """Split one combined pedal axis into (throttle_axis, brake_axis).

    Returns values in the system's convention: +1.0 = released, -1.0 = full.
    Movement from center toward the gas direction drives throttle; movement the
    other way drives brake. Travel on each side is rescaled to use the full range.
    """
    offset = raw - center
    if abs(offset) < PEDAL_DEADZONE:
        return 1.0, 1.0  # both released

    if (offset < 0) == (PEDAL_GAS_SIGN < 0):
        # Gas side: scale |offset| over the available travel toward the rail.
        rail = -1.0 if PEDAL_GAS_SIGN < 0 else 1.0
        span = abs(rail - center) - PEDAL_DEADZONE
        amount = min(1.0, (abs(offset) - PEDAL_DEADZONE) / span) if span > 1e-6 else 1.0
        return 1.0 - 2.0 * amount, 1.0  # throttle engaged, brake released
    else:
        # Brake side.
        rail = 1.0 if PEDAL_GAS_SIGN < 0 else -1.0
        span = abs(rail - center) - PEDAL_DEADZONE
        amount = min(1.0, (abs(offset) - PEDAL_DEADZONE) / span) if span > 1e-6 else 1.0
        return 1.0, 1.0 - 2.0 * amount  # throttle released, brake engaged


pedal_center = calibrate_axis_center(PEDAL_AXIS)
print(
    f"Pedal cal  : combined axis {PEDAL_AXIS} center={pedal_center:.3f}  "
    f"gas_sign={PEDAL_GAS_SIGN}  deadzone={PEDAL_DEADZONE}"
)
print("  -> Keep pedals released while this starts (center calibration).")

session = requests.Session()


def start_logging_session() -> bool:
    try:
        r = session.post(SESSION_START, timeout=5)
        if r.status_code == 200:
            data = r.json()
            print(f"Logging session: {data.get('status')}  id={data.get('sessionId')}")
            return True
        print(f"Could not start logging session: HTTP {r.status_code}: {r.text[:200]}")
        return False
    except requests.exceptions.RequestException as e:
        print(f"Could not start logging session: {e}")
        return False


def stop_logging_session() -> None:
    try:
        r = session.post(SESSION_STOP, timeout=5)
        if r.status_code == 200:
            print(f"Logging session stopped: {r.json().get('status')}")
        else:
            print(f"Stop returned HTTP {r.status_code}: {r.text[:200]}")
    except requests.exceptions.RequestException as e:
        print(f"Could not stop logging session: {e}")


start_logging_session()
atexit.register(stop_logging_session)

interval = 1 / POLL_HZ
last_log = 0
sent = 0
errors = 0

try:
    while True:
        pygame.event.pump()

        axes = [joy.get_axis(i) for i in range(joy.get_numaxes())]
        # Emit a consistent [steering, throttle, brake] with released = +1.0,
        # splitting the wheel's single combined pedal axis into throttle + brake
        # so braking can drive the movement below zero (reverse).
        # Steering inverted: swap left/right by flipping the sign.
        steering_axis = -axes[0] if len(axes) > 0 else 0.0
        throttle_axis, brake_axis = split_combined_pedal(
            _read_axis(PEDAL_AXIS), pedal_center
        )

        payload = {
            "axes":    [steering_axis, throttle_axis, brake_axis],
            "buttons": [joy.get_button(i) for i in range(joy.get_numbuttons())],
            "hats":    [list(joy.get_hat(i)) for i in range(joy.get_numhats())],
            "timestamp": round(time.time() * 1000),
        }

        try:
            r = session.post(BACKEND_URL, json=payload, timeout=2)
            if r.status_code == 200:
                sent += 1
            else:
                errors += 1
                print(f"HTTP {r.status_code}: {r.text[:120]}")
        except requests.exceptions.RequestException as e:
            errors += 1
            print(f"Error: {e}")

        now = time.time()
        if now - last_log >= 0.5:
            print(
                f"  -> sent {sent} err {errors} | raw a{PEDAL_AXIS}="
                f"{_read_axis(PEDAL_AXIS):+.3f} throttle={throttle_axis:+.3f} "
                f"brake={brake_axis:+.3f}"
            )
            last_log = now

        time.sleep(interval)
except KeyboardInterrupt:
    print("\nStopping...")
