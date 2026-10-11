#!/usr/bin/env python3
"""
Pure-logic check of the RTS camera maths (orthoview/OrthoviewClientEvents), mirrored line for line:

  1. zoom-to-cursor: after a zoom step that also changes the pitch (tilt follows zoom) and yaw, re-placing the camera
     with viewOffset keeps the ground point under the cursor at the same window position;
  2. middle-drag grab: moveInViewPlane moves the image by exactly the dragged pixels (ground follows the cursor);
  3. the critically damped spring is frame-rate independent (30 fps vs 300 fps) and settles in 0.15-0.25 s at the
     default smoothing (camera_smoothing 10 -> omega 25/s);
  4. tilt follows zoom: monotonic from camera_pitch_close to camera_pitch_far.

Usage: python3 tools/camera_math_check.py   (exit 1 on any failure)
"""
import math
import random
import sys

ZOOM_MIN, ZOOM_MAX = 10.0, 90.0
ZOOM_TILT_FAR = ZOOM_MAX * 1.5


def basis(yaw_mc, pitch_mc):
    yR, xR = math.radians(yaw_mc), math.radians(pitch_mc)
    f = (-math.sin(yR) * math.cos(xR), -math.sin(xR), math.cos(yR) * math.cos(xR))
    r = (-math.cos(yR), 0.0, -math.sin(yR))
    u = (-r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1])  # right x forward
    return f, r, u


def view_offset(ndc_x, ndc_y, yaw_mc, pitch_mc, z, aspect, cam_y, h):
    """OrthoviewClientEvents.viewOffset: camera -> ground point (plane y = h) drawn at (ndc_x, ndc_y)."""
    f, r, u = basis(yaw_mc, pitch_mc)
    a, b = ndc_x * z * aspect / 2, ndc_y * z / 2
    ox, oy, oz = r[0] * a + u[0] * b, u[1] * b, r[2] * a + u[2] * b
    t = 0 if abs(f[1]) < 1e-4 else (h - cam_y - oy) / f[1]
    return ox + f[0] * t, oz + f[2] * t


def project(p, cam, yaw_mc, pitch_mc, z, aspect):
    """World point -> window ndc for the orthographic view."""
    f, r, u = basis(yaw_mc, pitch_mc)
    d = (p[0] - cam[0], p[1] - cam[1], p[2] - cam[2])
    a = d[0] * r[0] + d[1] * r[1] + d[2] * r[2]
    b = d[0] * u[0] + d[1] * u[1] + d[2] * u[2]
    return a / (z * aspect / 2), b / (z / 2)


def move_in_view_plane(cam, yaw_mc, pitch_mc, right, up):
    f, r, u = basis(yaw_mc, pitch_mc)
    ox, oy, oz = r[0] * right + u[0] * up, u[1] * up, r[2] * right + u[2] * up
    if abs(f[1]) > 1e-3:
        ox -= f[0] * oy / f[1]
        oz -= f[2] * oy / f[1]
    return cam[0] + ox, cam[1], cam[2] + oz


def spring_step(x, v, target, omega, dt):
    if omega <= 0 or dt <= 0:
        return (target, 0.0) if omega <= 0 else (x, v)
    e = x - target
    k = v + omega * e
    ex = math.exp(-omega * dt)
    return target + (e + k * dt) * ex, (v - omega * k * dt) * ex


def base_pitch(z, close=40.0, far=80.0):
    t = (math.log(max(z, ZOOM_MIN)) - math.log(ZOOM_MIN)) / (math.log(ZOOM_TILT_FAR) - math.log(ZOOM_MIN))
    t = min(1.0, max(0.0, t))
    t = t * t * (3 - 2 * t)
    return close + (far - close) * t


def main():
    fails = 0
    rnd = random.Random(1234)

    # 1. zoom-to-cursor with pitch + yaw change
    worst = 0.0
    for _ in range(2000):
        aspect = rnd.uniform(1.2, 2.4)
        cam = [rnd.uniform(-500, 500), rnd.uniform(90, 200), rnd.uniform(-500, 500)]
        h = rnd.uniform(40, 120)
        z0 = rnd.uniform(ZOOM_MIN, 200)
        z1 = max(ZOOM_MIN, z0 * rnd.uniform(0.7, 1.4))
        yaw0 = rnd.uniform(-360, 360)
        yaw1 = yaw0 + rnd.uniform(-10, 10)
        p0, p1 = base_pitch(z0), base_pitch(z1)
        ndc = (rnd.uniform(-1, 1), rnd.uniform(-1, 1))
        ox, oz = view_offset(ndc[0], ndc[1], yaw0, p0, z0, aspect, cam[1], h)
        P = (cam[0] + ox, h, cam[2] + oz)
        nx, nz = view_offset(ndc[0], ndc[1], yaw1, p1, z1, aspect, cam[1], h)
        cam1 = (P[0] - nx, cam[1], P[2] - nz)
        sx, sy = project(P, cam1, yaw1, p1, z1, aspect)
        worst = max(worst, abs(sx - ndc[0]), abs(sy - ndc[1]))
    ok = worst < 1e-9
    fails += not ok
    print(f"[{'ok' if ok else 'FAIL'}] zoom-to-cursor keeps the ground point under the cursor (worst ndc error {worst:.2e})")

    # 2. grab-drag: image moves by exactly the dragged pixels
    worst = 0.0
    for _ in range(500):
        aspect, winH = 16 / 9, 1080
        z = rnd.uniform(ZOOM_MIN, 200)
        yaw, pitch = rnd.uniform(-180, 180), rnd.uniform(12, 90)
        cam = (rnd.uniform(-100, 100), 150.0, rnd.uniform(-100, 100))
        P = (cam[0] + rnd.uniform(-30, 30), 64.0, cam[2] + rnd.uniform(-30, 30))
        dx, dy = rnd.uniform(-50, 50), rnd.uniform(-50, 50)
        bpp = z / winH
        cam1 = move_in_view_plane(cam, yaw, pitch, -dx * bpp, dy * bpp)
        s0 = project(P, cam, yaw, pitch, z, aspect)
        s1 = project(P, cam1, yaw, pitch, z, aspect)
        # ndc -> window pixels: x right = ndc * winW/2, y down = -ndc * winH/2
        px = (s1[0] - s0[0]) * winH * aspect / 2
        py = -(s1[1] - s0[1]) * winH / 2
        worst = max(worst, abs(px - dx), abs(py - dy))
    ok = worst < 1e-6
    fails += not ok
    print(f"[{'ok' if ok else 'FAIL'}] middle-drag: ground follows the cursor pixel for pixel (worst {worst:.2e} px)")

    # 3. spring: frame-rate independence and settle time
    omega = 250 / 10
    def run(fps, secs):
        x, v = 0.0, 0.0
        for _ in range(int(round(fps * secs))):
            x, v = spring_step(x, v, 1.0, omega, 1 / fps)
        return x
    a, b = run(30, 0.5), run(300, 0.5)
    ok = abs(a - b) < 1e-9
    fails += not ok
    print(f"[{'ok' if ok else 'FAIL'}] spring is frame-rate independent (30 fps {a:.9f} vs 300 fps {b:.9f})")
    x, v, t = 0.0, 0.0, 0.0
    while abs(1 - x) > 0.02:
        x, v = spring_step(x, v, 1.0, omega, 1 / 1000)
        t += 1 / 1000
    ok = 0.15 <= t <= 0.25
    fails += not ok
    print(f"[{'ok' if ok else 'FAIL'}] default smoothing settles to 2% in {t:.3f} s (wanted 0.15-0.25 s)")
    over = max(spring_step(0.0, 0.0, 1.0, omega, k / 1000)[0] for k in range(1, 2000))
    ok = over <= 1.0 + 1e-12
    fails += not ok
    print(f"[{'ok' if ok else 'FAIL'}] no overshoot (critically damped): peak {over:.6f}")

    # 4. tilt follows zoom
    zs = [ZOOM_MIN + i for i in range(0, 200)]
    ps = [base_pitch(z) for z in zs]
    ok = all(b >= a - 1e-12 for a, b in zip(ps, ps[1:])) and abs(ps[0] - 40) < 1e-9 and abs(base_pitch(200) - 80) < 1e-9
    fails += not ok
    print(f"[{'ok' if ok else 'FAIL'}] tilt follows zoom: {ps[0]:.1f} deg close -> {base_pitch(ZOOM_TILT_FAR):.1f} deg far, monotonic")

    print("all camera checks passed" if fails == 0 else f"{fails} camera check(s) FAILED")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
