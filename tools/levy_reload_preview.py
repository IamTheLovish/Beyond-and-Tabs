"""The Crossbow Levy's reload, offline: a Python mirror of CrossbowLevyUnitModel's crossbow mechanism (the same
formulas and keys - change them together), written as poses for tools/game_preview.py (rotations, "_move", "_scale",
"_hide"), so the shot, the spanning and the rebolting are seen frame by frame before a build (lovish, Oct 16: "the
crossbow should be mechanical and have animations including rebolting").

    python3 tools/levy_reload_preview.py /tmp/reload_poses.json
    python3 tools/game_preview.py design/models/sunforged_crossbow_levy_v2.json --team tabard,tabard_dark \
        --views rts --poses @/tmp/reload_poses.json --out /tmp/reload.png

The numbers are tools/gen_levy.py's crossbow() (its XB dict / the bone pivots) - the XB_ constants in the Java."""
import json, math, sys

XB_X, PROD_CZ = -0.3, -12.6
TIP_DX, TIP_DZ = 8.708972, 2.555488
NUT_Z, CLAW_PARK = -6.95, -5.75
YAW0, LEN0 = 19.561380, 9.242413
RELAX = 6.0
P_HOOK, P_DRAWN, P_LATCH, P_BOLT = 0.12, 0.74, 0.80, 0.80


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def string_geom(s, d):
    phi = s * RELAX * (1 - d)
    c, sn = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    vx, vz = s * TIP_DX, TIP_DZ
    tx, tz = XB_X + c * vx + sn * vz, PROD_CZ - sn * vx + c * vz
    sz = tz + (NUT_Z - tz) * d
    dx, dz = XB_X - tx, sz - tz
    a = math.degrees(math.atan2(dz, -dx)) if s > 0 else math.degrees(math.atan2(-dz, dx))
    return phi, a - phi - s * YAW0, math.hypot(dx, dz) / LEN0, sz


def mech(spanning, p, loaded, shot_t):
    """-> (d, claw_z, nut_open, crank_turns, bolt (visible, dy, dz, dx, tilt), trigger, kick)"""
    bolt = (False, 0, 0, 0, 0)
    if spanning:
        if p < P_HOOK:
            d = 0.0
            claw = CLAW_PARK + (string_geom(1, 0)[3] + 0.3 - CLAW_PARK) * smooth(p / P_HOOK)
            turns = -0.5 * p / P_HOOK
        elif p < P_DRAWN:
            d = smooth((p - P_HOOK) / (P_DRAWN - P_HOOK))
            claw = string_geom(1, d)[3] + 0.3
            turns = -0.5 + 3.5 * (p - P_HOOK) / (P_DRAWN - P_HOOK)
        else:
            d = 1.0
            turns = 3.0
            claw = NUT_Z + 0.3 if p < P_LATCH else NUT_Z + 0.3 + (CLAW_PARK - NUT_Z - 0.3) * smooth((p - P_LATCH) / (1 - P_LATCH))
        nut = 1.0 if p < P_LATCH else 1.0 - smooth((p - P_LATCH) / 0.05)
        if p >= P_BOLT:
            u = smooth((p - P_BOLT) / (1 - P_BOLT))
            bolt = (True, 2.4 * (1 - u) ** 2, 1.2 * (1 - u), -1.0 * (1 - u), 25 * (1 - u))
    elif loaded:
        d, claw, nut, turns = 1.0, CLAW_PARK, 0.0, 0.0
        bolt = (True, 0, 0, 0, 0)
    else:
        d, claw, nut, turns = 0.0, CLAW_PARK, 1.0, 0.0
    trig = kick = 0.0
    if shot_t is not None and shot_t < 12:
        kick = math.exp(-shot_t / 2.2)
        trig = max(0.0, 1 - shot_t / 5)
        if d == 0.0:
            d = -0.18 * math.exp(-shot_t / 1.6) * math.cos(shot_t * 2.4)
    return d, claw, nut, turns, bolt, trig, kick


def pose(spanning=False, p=0.0, loaded=True, shot_t=None, charge=0.0):
    d, claw, nut, turns, bolt, trig, kick = mech(spanning, p, loaded, shot_t)
    out = {"_move": {}, "_scale": {}, "_hide": []}
    for s, prod, string in ((1, "prod_l", "string_l"), (-1, "prod_r", "string_r")):
        phi, own, sc, _ = string_geom(s, d)
        out[prod] = [0, phi, 0]
        out[string] = [0, own, 0]
        out["_scale"][string] = [sc, 1, 1]
    out["_move"]["claw"] = [0, 0, claw - CLAW_PARK]
    out["nut"] = [-55 * nut, 0, 0]
    out["crank"] = [360 * turns, 0, 0]
    out["trigger"] = [-10 * trig, 0, 0]
    vis, dy, dz, dx, tilt = bolt
    if not vis:
        out["_hide"].append("bolt")
    else:
        out["_move"]["bolt"] = [dx, dy, dz]
        out["bolt"] = [tilt, 0, 0]
    out["crossbow"] = [6 * kick - 28 * charge, 0, 0]
    if charge:
        out["xbow_arms"] = [-10 * charge, 0, 0]
        out["body"] = [-10 * charge, 0, 0]
    return out


if __name__ == "__main__":
    seq = {}
    seq["loaded"] = pose()
    seq["shot t1"] = pose(loaded=False, shot_t=1)
    seq["unloaded"] = pose(loaded=False)
    for p in (0.08, 0.3, 0.55, 0.74, 0.85, 0.93):
        seq["span %.2f" % p] = pose(spanning=True, p=p, loaded=False, charge=0.0 if len(sys.argv) > 2 else 1.0)
    json.dump(seq, open(sys.argv[1], "w"), indent=0)
