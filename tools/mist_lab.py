"""Offline mist lab (Oct 29, valley mist r11e): a numpy port of bat_mist.fsh over a synthetic ring river (Verdant's is a ring), seen at the
RTS still's scale (20 px per block, a 55-degree view), in ~4 s - so a mist round is judged offline before a 30-minute
eyes run. Ported 1:1: the shader's hash / value noise / fbm, the wind-frame warp, the bank cover, body, haze and light;
ValleyField's cover and smoothed ground (scipy stand-ins: an exact distance transform, box filters).
Not ported: the depth buffer (the terrain is a height function), fog of war (share 0), the world look pass after.

    python3 tools/mist_lab.py out.png                      # the r11e look (defaults = the shader's numbers)
    python3 tools/mist_lab.py out.png FLOW=global          # r11d's one direction for the view (across a ring river)
    python3 tools/mist_lab.py out.png FLOW=shader          # r11-r11c's per-pixel strand direction (the rims)
    python3 tools/mist_lab.py out.png CAP=soft             # r10b-r11c's soft-knee cap (the pale veil)

Defaults must follow bat_mist.fsh: change both together. Needs numpy, scipy, Pillow."""
import sys, numpy as np
from PIL import Image
P = dict(BANK_DENSITY=2.5, DEEP_LIFT=0.9, HAZE=0.04, HAZE_HEIGHT=1.6, SIGMA=0.22, DENSITY=0.6, MAXCOVER=0.85,
         LIT_MODE='r11c', TOP_SOFT=2.5, TOP_HARD=3.5, COV_A=2.2, COV_B=-0.6, BODY_LO=0.1, BODY_HI=0.45, CAP='clip', STEPS=12, FLOW='cells', X0=-200.0, CELL=32.0, CF_X=0.06, CF_Z=0.45, DF_X=0.1, DF_Z=0.5)
for a in sys.argv[2:]:
    k, v = a.split('='); P[k] = type(P[k])(v) if not isinstance(P[k], str) else v
W, H = 600, 360
PX = 0.05                              # 20 px per block, as the game's 05m still
xs = 20 + np.arange(W) * PX; zs = 58 + np.arange(H) * PX
P.setdefault('CELL', 32.0)
X, Z = np.meshgrid(xs, zs)
# terrain: lawn y=64, a river 9 blocks wide winding along x, water surface 63, sand 1 block each side, a terrace step
RC = np.array([10.0, 160.0]); RR = 95.0
d = np.abs(np.hypot(X - RC[0], Z - RC[1]) - RR)
cz = Z  # unused
ground = np.where(d < 4.5, 63.0, 64.0)
water = d < 4.5; sand = (d >= 4.5) & (d < 5.5)
col = np.zeros((H, W, 3))
col[...] = (0.33, 0.42, 0.19)
col[sand] = (0.76, 0.68, 0.50); col[water] = (0.33, 0.43, 0.62)
rng = np.random.default_rng(1); col *= (0.93 + 0.14 * rng.random((H, W)))[..., None]
# ValleyField (python port: blocks grid at 2-block step, then upsampled bilinear)
import importlib.util
def valley():
    step = 2; gx = np.arange(-40, 120, step); gz = np.arange(-20, 140, step)
    GX, GZ = np.meshgrid(gx, gz)
    gd = np.abs(np.hypot(GX - RC[0], GZ - RC[1]) - RR)
    h = np.where(gd < 4.5, 63, 64)
    w = gd < 4.5
    from scipy import ndimage
    dist = ndimage.distance_transform_edt(~w) * step
    wet = np.exp(-dist / 7.5) * np.exp(-np.maximum(0, h - 63 - 1.5) / 3.0)
    sm = ndimage.uniform_filter(ndimage.uniform_filter(h.astype(float), 5, mode='nearest'), 5, mode='nearest')
    wide = ndimage.uniform_filter(h.astype(float), 13, mode='nearest')
    hol = np.clip((wide - sm - 1.0) / 1.8, 0, 1)
    cov = np.where(w, 1.0, np.clip(np.maximum(wet, hol), 0, 1))
    from scipy.interpolate import RegularGridInterpolator
    def up(a):
        f = RegularGridInterpolator((gz, gx), a, method='linear'); return f(np.stack([Z, X], -1))
    return up(cov), up(sm)
LOCAL, SMOOTH = valley()
# --- the shader's noise, ported
def fract(x): return x - np.floor(x)
def hash3(p):
    p = fract(p * 0.3183099 + np.array([0.71, 0.113, 0.419])); p = p * 17.0
    return fract(p[..., 0] * p[..., 1] * p[..., 2] * (p[..., 0] + p[..., 1] + p[..., 2]))
def vnoise(x):
    i = np.floor(x); f = fract(x); f = f * f * (3 - 2 * f)
    def h(o): return hash3(i + np.array(o, float))
    a = h((0,0,0)) + (h((1,0,0)) - h((0,0,0))) * f[..., 0]
    b = h((0,1,0)) + (h((1,1,0)) - h((0,1,0))) * f[..., 0]
    c = h((0,0,1)) + (h((1,0,1)) - h((0,0,1))) * f[..., 0]
    e = h((0,1,1)) + (h((1,1,1)) - h((0,1,1))) * f[..., 0]
    return (a + (b - a) * f[..., 1]) * (1 - f[..., 2]) + (c + (e - c) * f[..., 1]) * f[..., 2]
def fbm(p):
    n = 0.5 * vnoise(p) + 0.25 * vnoise(p * 2.03 + [17.1, 3.7, 9.2]) + 0.125 * vnoise(p * 4.07 + [5.3, 11.9, 2.1])
    return n / 0.875
def ss(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t)
T = 100.0
P.setdefault('FLOW','smooth'); P.setdefault('X0',-200.0)
# flow: along the river (tangent of cz)
dcz = 10 / 40.0 * np.cos(X / 40.0); fl = np.stack([np.ones_like(dcz), dcz], -1); fl /= np.linalg.norm(fl, axis=-1, keepdims=True)
if P['FLOW'] in ('cells',):
    fl = np.broadcast_to(np.array([1.0, 0.0]), (H, W, 2))
elif P['FLOW'] == 'shader':
    gzz, gxx = np.gradient(LOCAL, PX)          # per block
    al = np.stack([-gzz * 2, gxx * 2], -1)     # 2-block central difference as the shader
    m = np.linalg.norm(al, axis=-1); wind = np.array([np.cos(0.6), np.sin(0.6)])
    al = al / np.maximum(m, 1e-6)[..., None]; al *= np.sign((al @ wind) + 1e-4)[..., None]
    t = ss(0.02, 0.08, m)[..., None]; fl = wind * (1 - t) + al * t; fl /= np.linalg.norm(fl, axis=-1, keepdims=True)
    fl = np.where((m < 0.02)[..., None], wind, fl)
elif P['FLOW'] == 'global':
    ang = 0.0; fl = np.broadcast_to(np.array([np.cos(ang + 1.3), np.sin(ang + 1.3)]), (H, W, 2))   # an arbitrary global
def cell_dir(cx, cz):
    # the river's local direction at a cell centre: tangent of the ring (the game: the tensor over the cell's window)
    v = np.array([cx - RC[0], cz - RC[1]]); r = np.linalg.norm(v)
    return np.array([-v[1], v[0]]) / r
def frame(w):     # w (...,3) world -> wind frame along flow
    w = w.copy(); w[..., 0] += P['X0']
    return np.stack([w[..., 0] * fl[..., 0] + w[..., 2] * fl[..., 1], w[..., 1], -w[..., 0] * fl[..., 1] + w[..., 2] * fl[..., 0]], -1)
def warpAt(k):
    w1 = np.stack([vnoise(k * 0.03 + [0, T * 0.035, 0]), vnoise(k * 0.03 + [7.3, T * 0.035 + 3.1, 2.9])], -1) - 0.5
    k2 = k.copy(); k2[..., 0] += w1[..., 0] * 16; k2[..., 2] += w1[..., 1] * 16
    c = np.stack([vnoise(k2 * 0.11 + [1.7, T * 0.09, 4.2]), vnoise(k2 * 0.11 + [9.1, T * 0.09, 0.6])], -1) - 0.5
    return w1 * 16 + c * 4
def cov_cells(w):
    # noise in each of the 4 nearest cells' rigid frames (origin = the cell centre), blended bilinearly
    C = P['CELL']; gx = (w[..., 0] / C) - 0.5; gz = (w[..., 2] / C) - 0.5
    ix = np.floor(gx); iz = np.floor(gz); fx = gx - ix; fz = gz - iz
    fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz)
    out = 0
    for ox, oz, wt in ((0, 0, (1 - fx) * (1 - fz)), (1, 0, fx * (1 - fz)), (0, 1, (1 - fx) * fz), (1, 1, fx * fz)):
        cx = (ix + ox + 0.5) * C; cz = (iz + oz + 0.5) * C
        # per-pixel cell centres -> per-pixel dirs (vectorised tangent)
        vx = cx - RC[0]; vz = cz - RC[1]; r = np.hypot(vx, vz); dx = -vz / r; dz = vx / r
        rx = w[..., 0] - cx; rz = w[..., 2] - cz
        a = rx * dx + rz * dz + 37.0 * (ix + ox) + 11.0 * (iz + oz); b = -rx * dz + rz * dx + 23.0 * (iz + oz) - 5.0 * (ix + ox)
        n = fbm(np.stack([a * P['CF_X'], np.full(a.shape, T * 0.004), b * P['CF_Z']], -1))
        out = out + wt * n
    return out
def bank(k, w=None):
    if P['FLOW'] == 'cells':
        cov = COVC
    else:
        cov = fbm(np.stack([k[..., 0] * P['CF_X'], np.full(k.shape[:-1], T * 0.004), k[..., 2] * P['CF_Z']], -1))
    c = LOCAL * (P['COV_A'] * cov + P['COV_B'])
    bil = vnoise(np.stack([k[..., 0] * 0.11, np.full(k.shape[:-1], T * 0.03), k[..., 2] * 0.2], -1)) - 0.5
    top = 1.0 + 3.6 * ss(0.32, 0.78, c) + 1.4 * bil
    return c, top
def body(c, top, h, det):
    b = ss(top + P['TOP_SOFT'] + 1.5 * det, top - P['TOP_HARD'], h)
    return b * ss(P['BODY_LO'], P['BODY_HI'], c + 0.6 * (det - 0.5))
LIT = np.array([0.93, 0.91, 0.85]); DEEP0 = np.array([0.70, 0.73, 0.80]) * 0.85
deep = DEEP0 + (LIT - DEEP0) * P['DEEP_LIFT']
toV = np.array([0.0, np.sin(np.radians(55)), np.cos(np.radians(55))])
surf = np.stack([X, ground, Z], -1)
g = SMOOTH
topL = g + 10.5
t0 = np.maximum(0, (g - 0.5 - ground) / toV[1]); ln = np.maximum(np.minimum((topL - ground) / toV[1], 48) - t0, 0)
ds = ln / P['STEPS']
jit = fract(52.9829189 * fract(np.arange(W)[None, :] * 0.06711056 + np.arange(H)[:, None] * 0.00583715))
wp = warpAt(frame(surf + toV * (t0 + 0.5 * ln)[..., None]))
midw = surf + toV * (t0 + 0.5 * ln)[..., None]
COVC = cov_cells(midw) if P['FLOW'] == 'cells' else None
c = col.copy(); trans = np.ones((H, W))
for i in range(P['STEPS']):
    w = surf + toV * (t0 + (i + jit) * ds)[..., None]
    k = frame(w); k[..., 0] += wp[..., 0]; k[..., 2] += wp[..., 1]
    h = w[..., 1] - g
    cv, top = bank(k)
    det = fbm(k * [P['DF_X'], 0.3, P['DF_Z']] + [0, T * 0.05, 0])
    bd = body(cv, top, h, det)
    haze = P['HAZE'] * np.exp(-np.maximum(h, 0) / P['HAZE_HEIGHT']) * (0.6 + 0.8 * det)
    dens = P['DENSITY'] * (P['BANK_DENSITY'] * bd + LOCAL * haze)
    a = 1 - np.exp(-P['SIGMA'] * dens * ds)
    if P['LIT_MODE'] == 'r11c':
        sh = body(cv, top, h + 1.5, det); lit = np.clip(0.95 - 0.35 * sh * bd, 0, 1)
    elif P['LIT_MODE'] == 'flat':
        lit = 0.85 + 0.15 * (det - 0.5) * 2
    mc = deep + (LIT - deep) * lit[..., None]
    c = c * (1 - a[..., None]) + mc * a[..., None]; trans *= 1 - a
seen = 1 - trans
lum = col @ [0.2126, 0.7152, 0.0722]
washed = col + (lum[..., None] - col) * (0.35 * seen)[..., None]
c = c + (washed - col) * trans[..., None]
ks = frame(surf); ks[..., 0] += wp[..., 0]; ks[..., 2] += wp[..., 1]
streak = fbm(ks * [0.16, 0.3, 0.5] + [0, T * 0.04, 0])
cap = P['MAXCOVER'] * 0.82 * (0.72 + 0.5 * streak)
cover = 1 - trans
if P['CAP'] == 'soft':
    soft = cap * (1 - np.exp(-cover / np.maximum(cap, 1e-3))); kc = np.where(cover > 1e-4, np.minimum(soft / np.maximum(cover, 1e-6), 1), 1)
elif P['CAP'] == 'clip':
    capc = P['MAXCOVER'] * (0.9 + 0.2 * streak); kc = np.where(cover > capc, capc / np.maximum(cover, 1e-6), 1)
else:
    kc = np.ones_like(cover)
out = col + (c - col) * kc[..., None]
out[LOCAL <= 0.004] = col[LOCAL <= 0.004]
Image.fromarray((np.clip(out, 0, 1) * 255).astype('uint8')).save(sys.argv[1])
print('cover mean on water %.2f, on lawn %.3f' % (cover[water].mean(), cover[~water & ~sand].mean()))
