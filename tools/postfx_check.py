#!/usr/bin/env python3
"""Runs the world look's real fragment shader (bat_postfx.fsh) on a synthetic RTS scene with Mesa's software OpenGL
(llvmpipe, headless EGL through moderngl) and checks what the edge smoothing may and may not touch - before a build
and an eyes tour. The grade, haze and vignette are set neutral, so any changed pixel is the edge smoothing's.

The scene (ortho camera like the RTS view): a slanted, textured ground plane; a box standing on it (silhouette
edges); a one-pixel line and one-pixel letters floating in front of the ground (thin features - a weapon shaft, an
in-world name tag). Checks:
  - the ground's interior and the screen border do not change at all (planes keep every texel);
  - most of the box's border pixels change (its stair steps are smoothed);
  - the line and its neighbours do not change; the letters and their neighbours barely do (thin features stay as
    drawn - FXAA smeared the "Commander" tag of an eyes still, Oct 15).
It also prints the pass's cost per 1600x900 frame on this machine's llvmpipe (the eyes CI renders on the CPU).

    pip install moderngl   (needs Mesa's libEGL; Ubuntu has it)
    python3 tools/postfx_check.py [--shader path/to/bat_postfx.fsh]
Exit status 1 when a check fails.
"""
import argparse
import os
import sys
import time

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHADERS = os.path.join(ROOT, "bar", "src", "main", "resources", "assets", "reignofnether", "shaders", "core")


def ortho(l, r, b, t, n, f):
    return np.array([[2 / (r - l), 0, 0, -(r + l) / (r - l)], [0, 2 / (t - b), 0, -(t + b) / (t - b)],
                     [0, 0, -2 / (f - n), -(f + n) / (f - n)], [0, 0, 0, 1]], dtype=np.float64)


def scene(W, H):
    l, r, b, t = -20.0, 20.0, -15.0, 15.0
    P = ortho(l, r, b, t, -1875.0, 1000.0)          # the RTS camera's depth range (OrthoviewClientEvents)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
    vy = b + (yy + 0.5) / H * (t - b)                # row 0 = bottom (GL)
    z = -500 + 0.3 * vy                              # view z: larger = nearer
    rng = np.random.default_rng(3)
    col = np.zeros((H, W, 3))
    col[:] = (0.30, 0.42, 0.20)
    col += rng.normal(0, 0.04, (H, W, 1))            # a textured ground
    box = (xx >= 100) & (xx < 160) & (yy >= 100) & (yy < 150)
    z = np.where(box, z + 5, z)
    col[box] = (0.5, 0.5, 0.52)
    line = (xx == 250) & (yy >= 60) & (yy < 240)
    z = np.where(line, z + 3, z)
    col[line] = 0.95
    text = np.zeros((H, W), bool)                    # an E and an H in one-pixel strokes
    text[200, 300:306] = text[203, 300:306] = text[206, 300:306] = True
    text[200:207, 300] = True
    text[200:207, 310] = text[200:207, 315] = True
    text[203, 310:316] = True
    z = np.where(text, z + 2, z)
    col[text] = 1.0
    depth = ((P[2, 2] * z + P[2, 3]) + 1) / 2
    return P, np.clip(col, 0, 1), depth, box, line, text


def near(mask, k=1):
    m = mask.copy()
    for dy in range(-k, k + 1):
        for dx in range(-k, k + 1):
            m |= np.roll(np.roll(mask, dy, 0), dx, 1)
    return m


def run(ctx, frag, vert, col, depth, P, edge_aa=1.0, frames=1):
    import moderngl
    H, W = depth.shape
    ctex = ctx.texture((W, H), 4, (np.concatenate([col, np.ones((H, W, 1))], -1) * 255).astype(np.uint8).tobytes())
    ctex.filter = (moderngl.LINEAR, moderngl.LINEAR)   # what WorldLookPass sets for its draw
    dtex = ctx.depth_texture((W, H), depth.astype(np.float32).tobytes())
    dtex.compare_func = ''
    dtex.filter = (moderngl.NEAREST, moderngl.NEAREST)
    out = ctx.texture((W, H), 4)
    fbo = ctx.framebuffer(color_attachments=[out])
    prog = ctx.program(vertex_shader=vert, fragment_shader=frag)
    quad = np.array([-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 1, 0], dtype='f4')
    vao = ctx.vertex_array(prog, [(ctx.buffer(quad.tobytes()), '3f', 'Position')])
    inv = np.linalg.inv(P).astype('f4')
    neutral = dict(InvProjMat=tuple(inv.T.flatten()), Ortho=1.0, EdgeAA=edge_aa, HazeMax=0.0, Contrast=0.0,
                   SplitTone=0.0, Saturation=1.0, Vignette=0.0, Aspect=W / H)
    for k, v in neutral.items():
        if k in prog:
            prog[k].value = v
    prog['DiffuseSampler'].value = 0
    prog['DepthSampler'].value = 1
    ctex.use(0)
    dtex.use(1)
    fbo.use()
    vao.render()
    ctx.finish()
    t = time.time()
    for _ in range(frames):
        vao.render()
    ctx.finish()
    ms = (time.time() - t) / max(frames, 1) * 1000
    return np.frombuffer(out.read(), np.uint8).reshape(H, W, 4)[..., :3].astype(int), ms


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--shader", default=os.path.join(SHADERS, "bat_postfx.fsh"))
    a = ap.parse_args()
    import moderngl
    ctx = moderngl.create_standalone_context(backend='egl', require=330)
    frag = open(a.shader).read().replace('#version 150', '#version 330')
    vert = open(os.path.join(SHADERS, "bat_postfx.vsh")).read().replace('#version 150', '#version 330')
    W, H = 400, 300
    P, col, depth, box, line, text = scene(W, H)
    res, _ = run(ctx, frag, vert, col, depth, P)
    src = (col * 255).astype(np.uint8).astype(int)
    changed = np.abs(res - src).max(-1) > 2          # the pass's dither is half a step
    feature = box | line | text
    ground = ~near(feature, 3)
    border = box ^ (box & ~near(~box, 1)) | (near(box, 1) & ~box)
    checks = [
        ("ground and screen border unchanged", int((changed & ground).sum()) == 0,
         "%d of %d changed" % ((changed & ground).sum(), ground.sum())),
        ("box edges smoothed (>= 60%)", (changed & border).sum() >= 0.6 * border.sum(),
         "%d of %d changed" % ((changed & border).sum(), border.sum())),
        ("one-pixel line and its neighbours unchanged", int((changed & near(line, 1)).sum()) == 0,
         "%d changed" % (changed & near(line, 1)).sum()),
        ("one-pixel letters and their neighbours almost unchanged (<= 25%)",
         (changed & near(text, 1)).sum() <= 0.25 * near(text, 1).sum(),
         "%d of %d changed" % ((changed & near(text, 1)).sum(), near(text, 1).sum())),
    ]
    ok = True
    for name, passed, detail in checks:
        print("%-4s %-62s %s" % ("ok" if passed else "FAIL", name, detail))
        ok &= bool(passed)
    _, ms_off = run(ctx, frag, vert, np.resize(col, (900, 1600, 3)), np.resize(depth, (900, 1600)), P, 0.0, 5)
    _, ms_on = run(ctx, frag, vert, np.resize(col, (900, 1600, 3)), np.resize(depth, (900, 1600)), P, 1.0, 5)
    print("cost per 1600x900 frame on %s: %.1f ms without the edge smoothing, %.1f ms with it" % (
        ctx.info['GL_RENDERER'].split(' (')[0], ms_off, ms_on))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
