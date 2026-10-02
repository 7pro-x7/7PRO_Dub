"""
Procedural 3D tutor "Mr. Adam" for 7PRO — writes a glTF 2.0 binary (.glb) with ARKit-named
face morph targets, a "Head" node for head motion, PBR materials and vertex colours.
Pure numpy, CC0 (original work generated for the user).
"""
import json, struct, math, os
FEMALE = os.environ.get("AV_GENDER") == "female"
import numpy as np

TARGETS = ["jawOpen","mouthClose","mouthFunnel","mouthPucker","mouthSmileLeft","mouthSmileRight",
           "mouthStretchLeft","mouthStretchRight","mouthPressLeft","mouthPressRight","mouthRollLower",
           "mouthUpperUpLeft","mouthUpperUpRight","mouthLowerDownLeft","mouthLowerDownRight",
           "cheekSquintLeft","cheekSquintRight","eyeBlinkLeft","eyeBlinkRight",
           "eyeLookUpLeft","eyeLookUpRight","eyeLookDownLeft","eyeLookDownRight",
           "eyeLookInLeft","eyeLookInRight","eyeLookOutLeft","eyeLookOutRight",
           "browInnerUp","browOuterUpLeft","browOuterUpRight"]
TI = {n:i for i,n in enumerate(TARGETS)}

def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)

def rot_x(p, c, ang):
    s, co = math.sin(ang), math.cos(ang)
    q = p - c
    return np.stack([q[:,0], q[:,1]*co - q[:,2]*s, q[:,1]*s + q[:,2]*co], 1) + c

def rot_y(p, c, ang):
    s, co = math.sin(ang), math.cos(ang)
    q = p - c
    return np.stack([q[:,0]*co + q[:,2]*s, q[:,1], -q[:,0]*s + q[:,2]*co], 1) + c

class Prim:
    def __init__(self, name, mat, pos, idx, col=None):
        self.name, self.mat = name, mat
        self.pos = np.asarray(pos, np.float32)
        self.idx = np.asarray(idx, np.uint32).reshape(-1)
        self.col = None if col is None else np.asarray(col, np.float32)
        self.t = np.zeros((len(TARGETS), len(self.pos), 3), np.float32)

def normals(pos, idx):
    tri = idx.reshape(-1, 3)
    a, b, c = pos[tri[:,0]], pos[tri[:,1]], pos[tri[:,2]]
    fn = np.cross(b - a, c - a)
    n = np.zeros_like(pos)
    for k in range(3): np.add.at(n, tri[:,k], fn)
    l = np.linalg.norm(n, axis=1, keepdims=True); l[l == 0] = 1
    return (n / l).astype(np.float32)

def grid_faces(rows, cols, wrap=True, skip=None):
    f = []
    cc = cols if wrap else cols - 1
    for r in range(rows - 1):
        for c in range(cc):
            if skip and skip(r, c): continue
            a = r*cols + c; b = r*cols + (c+1) % cols
            d = (r+1)*cols + c; e = (r+1)*cols + (c+1) % cols
            f += [a, d, b, b, d, e]
    return f

# ------------------------------------------------------------------ head (skin)
ROWS, COLS = 131, 144
EYE_C = [(0.40, 0.095), (-0.40, 0.095)]   # almond centres in unit-direction (ux, uy)
EYE_A, EYE_H, EYE_BEND = (0.172, 0.040, 0.019) if FEMALE else (0.170, 0.036, 0.016)

def almond_rho(ux, uy, cx, cy):
    t = (ux - cx) / EYE_A
    top = cy + EYE_BEND * (1 - np.clip(t*t, 0, 1)) - 0.006 * t * np.sign(cx)   # slight upward tilt outwards
    return np.sqrt(t*t + ((uy - top) / EYE_H)**2)

def head_surface(TH, PH, with_disp=True):
    ux, uy, uz = np.sin(TH)*np.sin(PH), np.cos(TH), np.sin(TH)*np.cos(PH)
    A, B = (0.075, 0.103) if FEMALE else (0.078, 0.105)
    C = np.where(uz > 0, 0.094, 0.101)
    x, y, z = ux*A, uy*B, uz*C
    low = np.clip(-uy, 0, 1)
    x = x * (1 - (0.24 if FEMALE else 0.20)*low**1.6)
    z = np.where(uz > 0, z*(1 - 0.06*low), z*(1 - 0.20*low))
    z = np.where((uz > 0) & (uy > 0.35), z*(1 - 0.05*(uy-0.35)), z)
    # flatten the face plane: keep mouth and chin forward instead of following the sphere back
    facew = np.exp(-(ux/0.55)**2) * np.clip(uz, 0, 1)
    z = z + facew * (0.020*np.clip(-uy + 0.05, 0, 1)**0.85)
    x = x * (1 - 0.05*np.clip(uy, 0, 1)**2)
    P = np.stack([x, y, z], -1)
    if not with_disp: return P, ux, uy, uz
    front = np.clip(uz, 0, 1)**2
    def bump(cx, cy, amp, sx, sy=None, fr=True):
        sy = sy or sx
        return amp * np.exp(-(((ux-cx)/sx)**2 + ((uy-cy)/sy)**2)) * (front if fr else 1)
    d = np.zeros_like(ux)
    for s in (-1, 1):
        d += bump(0.40*s, 0.10, -0.006, 0.16, 0.10)       # eye sockets (soft)
        d += bump(0.36*s, 0.25, 0.0040, 0.16, 0.05)       # brow ridge
        d += bump(0.60*s, -0.06, 0.0050, 0.16, 0.12)      # cheekbones
        d += bump(0.55*s, -0.40, 0.0030, 0.18, 0.18)      # fuller lower cheeks
        d += bump(0.10*s, -0.215, 0.0060, 0.050, 0.040)  # nostril wings
        d += bump(0.82*s, 0.25, -0.004, 0.2, 0.2, fr=False)
    d += bump(0, 0.24, 0.003, 0.2, 0.06)
    d += bump(0, 0.09, 0.0045, 0.040, 0.07) + bump(0, -0.01, 0.0095, 0.045, 0.07)   # nose bridge
    nk = 0.78 if FEMALE else 1.0
    d += bump(0, -0.125, 0.0175*nk, 0.050 if FEMALE else 0.055, 0.065) + bump(0, -0.20, 0.0135*nk, 0.055 if FEMALE else 0.060, 0.045)  # nose tip
    d += bump(0, -0.33, 0.0035, 0.07, 0.04)                                          # philtrum
    d += bump(0, -0.385, 0.0055, 0.21, 0.032)                                        # upper lip
    d += bump(0, -0.465, 0.0062, 0.18, 0.042)                                        # lower lip
    d += bump(0, -0.555, -0.0028, 0.18, 0.04)                                        # under-lip crease
    d += bump(0, -0.80, 0.0085, 0.26, 0.14)                                          # chin
    nrm = P / np.linalg.norm(P, axis=-1, keepdims=True)
    return P + nrm * d[..., None], ux, uy, uz

th = np.linspace(0, math.pi, ROWS)
ph = np.linspace(-math.pi, math.pi, COLS, endpoint=False)
TH, PH = np.meshgrid(th, ph, indexing="ij")
_, ux, uy, uz = head_surface(TH, PH, with_disp=False)

# eye openings: cut the almond, then pull the first ring of vertices exactly onto its edge
rho = np.full(ux.shape, 9.0)
for cx, cy in EYE_C:
    rho = np.minimum(rho, np.where(np.sign(ux) == np.sign(cx), almond_rho(ux, uy, cx, cy), 9.0) + np.where(uz > 0, 0, 9))
inside = rho < 1.0
rim = np.zeros_like(inside)
rim[:-1] |= inside[1:]; rim[1:] |= inside[:-1]
rim |= np.roll(inside, 1, 1) | np.roll(inside, -1, 1)
rim &= ~inside
for cx, cy in EYE_C:
    sel = rim & (np.sign(ux) == np.sign(cx))
    for (r, c) in zip(*np.where(sel)):
        # walk towards the almond centre until rho == 1 (bisection in (ux, uy))
        u0, v0 = ux[r, c], uy[r, c]
        lo, hi = 0.0, 1.0
        for _ in range(30):
            m_ = (lo + hi) / 2
            uu = u0 + (cx - u0)*m_; vv = v0 + (cy - v0)*m_
            if almond_rho(np.array(uu), np.array(vv), cx, cy) > 1: lo = m_
            else: hi = m_
        uu = u0 + (cx - u0)*lo; vv = v0 + (cy - v0)*lo
        zz = math.sqrt(max(1e-6, 1 - uu*uu - vv*vv))
        TH[r, c] = math.acos(np.clip(vv, -1, 1)); PH[r, c] = math.atan2(uu, zz)
P, ux, uy, uz = head_surface(TH, PH)
# tuck the rim in a little so the lids read as having thickness
nr_ = P / np.linalg.norm(P, axis=-1, keepdims=True)
P = P - nr_ * (rim * 0.0022)[..., None]
front = np.clip(uz, 0, 1)**2

# mouth slit: the rows either side of the lip line meet when closed and part when the jaw opens
rm = int(np.argmin(np.abs(np.cos(th) - (-0.425))))
span = np.abs(ph) <= 0.38
colsS = np.where(span)[0]
mid = (P[rm, span] + P[rm+1, span]) / 2
P[rm, span] = mid; P[rm+1, span] = mid
for k, off in ((1, 0.55), (2, 0.2)):
    for side in (colsS.min()-k, colsS.max()+k):
        mm = (P[rm, side] + P[rm+1, side]) / 2
        P[rm, side] = P[rm, side]*(1-off) + mm*off; P[rm+1, side] = P[rm+1, side]*(1-off) + mm*off
head_pos = P.reshape(-1, 3)
inside_f = inside.reshape(-1)
def skip_head(r, c):
    c2 = (c+1) % COLS
    if r == rm and span[c] and span[c2]: return True
    return inside[r, c] or inside[r, c2] or inside[min(r+1, ROWS-1), c] or inside[min(r+1, ROWS-1), c2]
head_idx = grid_faces(ROWS, COLS, skip=skip_head)

lipw = (np.exp(-((ux/0.20)**2 + ((uy+0.385)/0.028)**2)) + np.exp(-((ux/0.17)**2 + ((uy+0.462)/0.038)**2))) * front
lipw = np.clip(lipw, 0, 1)
cheek = sum(np.exp(-(((ux-0.55*s)/0.14)**2 + ((uy+0.12)/0.12)**2)) for s in (-1, 1)) * front
col = np.ones((ROWS, COLS, 3))
col = col*(1-lipw[..., None]) + np.array([0.80, 0.46, 0.48] if FEMALE else [0.86, 0.60, 0.58])*lipw[..., None]
col = col*(1-0.12*cheek[..., None]) + np.array([1.0, 0.86, 0.82])*0.12*cheek[..., None]
col[rim] = col[rim] * 0.62    # lash line
head_col = np.concatenate([col.reshape(-1, 3), np.ones((ROWS*COLS, 1))], 1)

head = Prim("Skin", "skin", head_pos, head_idx, head_col)
Hx, Hy, Hz = head_pos[:,0], head_pos[:,1], head_pos[:,2]
UX, UY, UZ = ux.reshape(-1), uy.reshape(-1), uz.reshape(-1)
FR = front.reshape(-1)

mouth_y = float(mid[:,1].mean()); mouth_z = float(mid[:,2].max())
jaw_c = np.array([0, mouth_y + 0.004, mouth_z - 0.080])
JAW = math.radians(17)
below = (np.arange(ROWS*COLS) // COLS) > rm
wj = np.where(below, 1.0, 0.0) * smooth(-0.035, 0.02, Hz) * smooth(-0.20, -0.05, UY - (-0.425) + 0.0) * 0 + 0
spanv = np.tile(span, ROWS)
# Inside the lip span the lower lip moves fully from the slit down; beside it the skin ramps in
# smoothly over the cheeks, so opening the jaw never leaves a crease across the face.
wv = np.where(spanv & below, 1.0, smooth(0.0, 0.16, -0.425 - UY))
wj = wv * smooth(-0.05, 0.03, Hz) * smooth(1.0, 0.45, np.abs(UX)) * smooth(0.98, 0.6, -UY)
def jaw_apply(p, w, ang=JAW):
    return (rot_x(p, jaw_c, ang) - p) * w[:, None]
head.t[TI["jawOpen"]] = jaw_apply(head_pos, wj)

# lip region helpers (unit-sphere coordinates)
def lipzone(cy, sy, sx=0.26):
    return np.exp(-((UX/sx)**2 + ((UY-cy)/sy)**2)) * FR
upper = lipzone(-0.39, 0.06) * (~below)
lower = lipzone(-0.47, 0.07) * below
corner = lambda s: np.exp(-(((UX - 0.30*s)/0.09)**2 + ((UY+0.43)/0.07)**2)) * FR
ex = np.array([1.0, 0, 0], np.float32); ey = np.array([0, 1.0, 0], np.float32); ez = np.array([0, 0, 1.0], np.float32)
for s, L in ((1, "Left"), (-1, "Right")):   # character's left is +X
    head.t[TI["mouthSmile"+L]] = (corner(s)[:, None] * (ex*0.006*s + ey*0.0065 - ez*0.003))
    head.t[TI["mouthStretch"+L]] = (corner(s)[:, None] * (ex*0.0065*s - ey*0.0015))
    side = smooth(-0.05, 0.25, UX*s)
    head.t[TI["mouthPress"+L]] = ((upper+lower)*side)[:, None] * (-ez*0.0025)
    head.t[TI["mouthUpperUp"+L]] = (upper*side)[:, None] * (ey*0.004)
    head.t[TI["mouthLowerDown"+L]] = (lower*side)[:, None] * (-ey*0.005)
    ck = np.exp(-(((UX-0.52*s)/0.14)**2 + ((UY+0.14)/0.10)**2)) * FR
    head.t[TI["cheekSquint"+L]] = ck[:, None] * (ey*0.003 + ez*0.001)
lips = np.clip(lipzone(-0.43, 0.10, 0.30), 0, 1)
head.t[TI["mouthPucker"]] = lips[:, None] * np.stack([-Hx*0.38, np.zeros_like(Hx), np.full_like(Hx, 0.009)], 1)
head.t[TI["mouthFunnel"]] = lips[:, None] * np.stack([-Hx*0.22, np.zeros_like(Hx), np.full_like(Hx, 0.007)], 1) + jaw_apply(head_pos, wj*lips, math.radians(5))
head.t[TI["mouthClose"]] = (upper[:, None] * (-ey*0.0025)) + (lower[:, None] * (ey*0.0025))
head.t[TI["mouthRollLower"]] = lower[:, None] * (-ez*0.004 + ey*0.002)
fore = lambda cx: np.exp(-(((UX-cx)/0.14)**2 + ((UY-0.30)/0.08)**2)) * FR
head.t[TI["browInnerUp"]] = (fore(0.12) + fore(-0.12))[:, None] * (ey*0.0025)
head.t[TI["browOuterUpLeft"]] = fore(0.40)[:, None] * (ey*0.0025)
head.t[TI["browOuterUpRight"]] = fore(-0.40)[:, None] * (ey*0.0025)

prims = [head]
def surface_at(cx, cy):
    """Head surface point in unit direction (cx, cy, +z)."""
    d = np.array([cx, cy, math.sqrt(max(0, 1 - cx*cx - cy*cy))])
    i = np.argmin((UX-d[0])**2 + (UY-d[1])**2 + (UZ-d[2])**2)
    return head_pos[i]

# ------------------------------------------------------------------ neck
nr, nc = 12, 48
ang = np.linspace(-math.pi, math.pi, nc, endpoint=False)
yy = np.linspace(-0.06, -0.19, nr)
neck = np.array([[0.056*math.sin(a), yv, 0.058*math.cos(a) - 0.016] for yv in yy for a in ang])
prims.append(Prim("Neck", "skin", neck, grid_faces(nr, nc), np.ones((len(neck), 4))))

# ------------------------------------------------------------------ ears
def ellipsoid(c, r, n=16, m=24, rotz=0.0):
    t = np.linspace(0, math.pi, n); p = np.linspace(-math.pi, math.pi, m, endpoint=False)
    T, Pp = np.meshgrid(t, p, indexing="ij")
    v = np.stack([np.sin(T)*np.sin(Pp)*r[0], np.cos(T)*r[1], np.sin(T)*np.cos(Pp)*r[2]], -1).reshape(-1, 3)
    if rotz: v = rot_y(v, np.zeros(3), rotz)
    return v + np.asarray(c), grid_faces(n, m)
for s in (-1, 1):
    v, f = ellipsoid((0.071*s, 0.0, -0.008), (0.009, 0.028, 0.017), rotz=0.35*s)
    prims.append(Prim("Ear", "skin", v, f, np.ones((len(v), 4))))

# ------------------------------------------------------------------ eyes, lids, irises
RE = 0.0120
for s, L in ((1, "Left"), (-1, "Right")):
    cx0, cy0 = EYE_C[0][0]*s, EYE_C[0][1]
    selr = rim & (np.sign(ux) == s)
    rp = P[selr]
    rc = rp.mean(0)
    ec = np.array([rc[0], rc[1] + 0.0008, rp[:, 2].max() - 0.0008 - RE])
    v, f = ellipsoid(ec, (RE, RE, RE), 18, 28)
    prims.append(Prim("Sclera", "sclera", v, f))
    def cap(amax, rr, n=8, m=28):
        a = np.linspace(0, amax, n); b = np.linspace(-math.pi, math.pi, m, endpoint=False)
        Aa, Bb = np.meshgrid(a, b, indexing="ij")
        v = np.stack([np.sin(Aa)*np.cos(Bb), np.sin(Aa)*np.sin(Bb), np.cos(Aa)], -1).reshape(-1, 3)*rr + ec
        return v, grid_faces(n, m)
    for nm, amax, rr in (("Iris", 0.50, RE+0.0002), ("Pupil", 0.20, RE+0.00035)):
        v, f = cap(amax, rr)
        p = Prim(nm, nm.lower(), v, f)
        yaw, pit = math.radians(18), math.radians(13)
        p.t[TI["eyeLookUp"+L]] = rot_x(v, ec, -pit) - v
        p.t[TI["eyeLookDown"+L]] = rot_x(v, ec, pit) - v
        p.t[TI["eyeLookOut"+L]] = rot_y(v, ec, yaw*s) - v
        p.t[TI["eyeLookIn"+L]] = rot_y(v, ec, -yaw*s) - v
        prims.append(p)
    # upper lid: tucked behind the brow skin when open, sweeps down over the eye to blink
    RL = RE + 0.0010
    n, m = 14, 30
    u = np.linspace(0, 1, n); bb = np.linspace(-1.6, 1.6, m)
    U, Bb = np.meshgrid(u, bb, indexing="ij")
    def lid(amax):
        a = U*amax
        return (np.stack([np.sin(a)*np.sin(Bb), np.cos(a), np.sin(a)*np.cos(Bb)], -1)*RL + ec).reshape(-1, 3)
    open_, closed = lid(math.radians(56)), lid(math.radians(118))
    lidcol = np.ones((n*m, 4)); edge = (U.reshape(-1) > 0.88)
    lidcol[edge, :3] = 0.35
    p = Prim("UpperLid", "skin", open_, grid_faces(n, m, wrap=False), lidcol)
    p.t[TI["eyeBlink"+L]] = closed - open_
    p.t[TI["cheekSquint"+L]] = lid(math.radians(62)) - open_
    prims.append(p)
    # eyebrow ribbon
    xs = np.linspace(0.012, 0.049, 12) * s
    pts = []
    for i, xv in enumerate(xs):
        t_ = i/11
        cy = (0.28 + 0.06*math.sin(t_*math.pi*0.85) - 0.035*t_) if FEMALE else (0.265 + 0.045*math.sin(t_*math.pi*0.9) - 0.03*t_)
        cx = xv / 0.076
        sp2 = surface_at(cx, cy)
        th_ = (0.0027 if FEMALE else 0.0042)*(1 - 0.5*t_)
        pts.append(sp2 + np.array([0, th_, 0.0016])); pts.append(sp2 + np.array([0, -th_, 0.0016]))
    pts = np.array(pts)
    f = []
    for i in range(11):
        a0, b0, a1, b1 = 2*i, 2*i+1, 2*i+2, 2*i+3
        f += [a0, b0, a1, a1, b0, b1] if s > 0 else [a0, a1, b0, a1, b1, b0]
    p = Prim("Brow", "hair", pts, f)
    tt = np.repeat(np.linspace(0, 1, 12), 2)
    p.t[TI["browInnerUp"]] = np.stack([np.zeros(24), 0.0032*(1-tt), np.zeros(24)], 1)
    p.t[TI["browOuterUp"+L]] = np.stack([np.zeros(24), 0.0032*tt, np.zeros(24)], 1)
    prims.append(p)

# ------------------------------------------------------------------ mouth interior, teeth
mc = np.array([0, mouth_y - 0.001, mouth_z - 0.027])
v, f = ellipsoid(mc, (0.026, 0.013, 0.018), 14, 24)
p = Prim("MouthInside", "mouth", v, f)
p.t[TI["jawOpen"]] = jaw_apply(v, np.clip((mouth_y - v[:,1]) / 0.01, 0, 1))
prims.append(p)
def teeth(yc, h, lower):
    a = np.linspace(-1.0, 1.0, 18); rr = 0.022
    pts = []
    for av in a:
        cx, cz = rr*math.sin(av), mouth_z - 0.0085 - rr*(1 - math.cos(av))*0.9
        pts += [[cx, yc + h/2, cz], [cx, yc - h/2, cz]]
    pts = np.array(pts); ff = []
    for i in range(17):
        a0, b0, a1, b1 = 2*i, 2*i+1, 2*i+2, 2*i+3
        ff += [a0, b0, a1, a1, b0, b1]
    return pts, ff
v, f = teeth(mouth_y + 0.0042, 0.0070, False)
prims.append(Prim("UpperTeeth", "teeth", v, f))
v, f = teeth(mouth_y - 0.0050, 0.0060, True)
p = Prim("LowerTeeth", "teeth", v, f); p.t[TI["jawOpen"]] = jaw_apply(v, np.ones(len(v)))
prims.append(p)

# ------------------------------------------------------------------ hair (short, neat)
hr = 64
aph = np.abs(PH)
if FEMALE:
    hmask_th = np.where(aph < 0.85, 0.74 + 0.10*(aph/0.85)**2,
                np.where(aph < 1.15, 0.84 + 1.10*((aph-0.85)/0.30), 1.94 + 0.35*((aph-1.15)/1.99)))
else:
    hmask_th = np.where(aph < 0.85, 0.80 + 0.06*(aph/0.85)**2,
                np.where(aph < 1.85, 0.86 + 0.55*((aph-0.85)/1.0), 1.41 + 0.50*((aph-1.85)/1.29)))
edge = hmask_th - TH                       # > 0 inside the hair area
vol = 0.006 + 0.010*np.clip(np.cos(TH), 0, 1)**1.5 + 0.0014*np.sin(PH*11)*np.sin(TH*9)
thick = np.where(edge > 0, vol*smooth(0.0, 0.14, edge) + 0.0008, -0.004*smooth(0.0, 0.06, -edge))
HP = P + (P/np.linalg.norm(P, axis=-1, keepdims=True))*thick[..., None]
if FEMALE:
    # Bob: below ear level the hair falls straight down from the widest ring instead of hugging
    # the jaw, and it stays clear of the face (only behind the cheeks).
    ring = int(np.argmin(np.abs(th - 1.30)))
    for r in range(ring + 1, ROWS):
        w = (smooth(1.02, 1.35, aph[r]) * smooth(1.30, 1.55, TH[r]))[:, None]
        base = HP[ring].copy()
        base[:, 0] *= 1.04; base[:, 2] = base[:, 2]*1.04 - 0.004
        curtain = np.stack([base[:, 0], P[r, :, 1] - 0.006, base[:, 2]], -1)
        HP[r] = HP[r]*(1 - w) + curtain*w
    # gentle volume and a soft inward curl at the ends
    endw = smooth(1.75, 2.25, TH)[..., None] * smooth(1.02, 1.35, aph)[..., None]
    HP[..., 0:1] = HP[..., 0:1] * (1 - 0.05*endw)
    HP[..., 2:3] = HP[..., 2:3] * (1 - 0.05*endw)
    # Clean hair ends: past the hairline every column collapses onto its exact end point
    # (interpolated between rows), so the edge is a smooth curve instead of grid stair-steps.
    for c in range(COLS):
        tend = float(hmask_th[0, c])
        fr = np.interp(tend, th, np.arange(ROWS))
        r0 = int(min(max(math.floor(fr), 0), ROWS - 2)); fr_ = fr - r0
        endp = HP[r0, c]*(1 - fr_) + HP[r0 + 1, c]*fr_
        HP[r0 + 1:, c] = endp
HP[..., 1] += 0.003*np.clip(np.cos(TH), 0, 1)
keep = (edge > -0.10) | FEMALE
hair_idx = grid_faces(ROWS, COLS, skip=lambda r, c: not (keep[r, c] and keep[min(r+1, ROWS-1), c] and keep[r, (c+1) % COLS] and keep[min(r+1, ROWS-1), (c+1) % COLS]))
hcol = np.ones((ROWS*COLS, 4)); hcol[:, :3] = (0.85 + 0.15*np.sin(PH*23)*np.sin(TH*17)).reshape(-1, 1)
prims.append(Prim("Hair", "hair", HP.reshape(-1, 3), hair_idx, hcol))

if FEMALE:
    for s_ in (-1, 1):
        v, f = ellipsoid((0.074*s_, -0.030, -0.006), (0.0032, 0.0032, 0.0032), 8, 12)
        prims.append(Prim("Earring", "gold", v, f))
head_prims = list(prims)

# ------------------------------------------------------------------ body (sweater + collar), node space
secs = [(-0.150, 0.066, 0.062, -0.016, 2.0), (-0.168, 0.092, 0.074, -0.016, 2.1), (-0.188, 0.150, 0.090, -0.016, 2.4),
        (-0.208, 0.198, 0.102, -0.014, 2.8), (-0.238, 0.218, 0.112, -0.010, 3.0), (-0.300, 0.224, 0.118, -0.006, 3.0),
        (-0.400, 0.222, 0.120, -0.002, 2.9), (-0.480, 0.218, 0.118, 0.0, 2.8)]
bc = 72
ang = np.linspace(-math.pi, math.pi, bc, endpoint=False)
bpos = []
for yv, rx, rz, zo, e in secs:
    for a in ang:
        sx_, cz_ = math.sin(a), math.cos(a)
        px = rx*np.sign(sx_)*abs(sx_)**(2/e); pz = rz*np.sign(cz_)*abs(cz_)**(2/e) + zo
        bpos.append([px, yv, pz])
bpos = np.array(bpos)
body = Prim("Sweater", "sweater", bpos, grid_faces(len(secs), bc), None)
# collar: white shirt band around the neck
cr = [(-0.128, 0.061, 0.061), (-0.138, 0.064, 0.063), (-0.152, 0.071, 0.068), (-0.165, 0.084, 0.076)]
cpos = np.array([[r*math.sin(a), yv, rz*math.cos(a) - 0.016] for yv, r, rz in cr for a in ang])
collar = Prim("Collar", "shirt", cpos, grid_faces(len(cr), bc))
body_prims = [body, collar]

# ------------------------------------------------------------------ write glb
MATS = {
    "skin":    dict(c=[0.78, 0.55, 0.43, 1], r=0.58, m=0.0),
    "sclera":  dict(c=[0.93, 0.91, 0.88, 1], r=0.25, m=0.0),
    "iris":    dict(c=[0.30, 0.18, 0.09, 1], r=0.18, m=0.0),
    "pupil":   dict(c=[0.02, 0.02, 0.02, 1], r=0.10, m=0.0),
    "hair":    dict(c=[0.085, 0.062, 0.048, 1], r=0.86, m=0.0),
    "mouth":   dict(c=[0.30, 0.09, 0.09, 1], r=0.6, m=0.0),
    "teeth":   dict(c=[0.93, 0.91, 0.86, 1], r=0.35, m=0.0),
    "sweater": dict(c=[0.40, 0.15, 0.21, 1] if FEMALE else [0.12, 0.20, 0.32, 1], r=0.85, m=0.0),
    "gold":    dict(c=[0.95, 0.76, 0.38, 1], r=0.25, m=1.0),
    "shirt":   dict(c=[0.92, 0.93, 0.95, 1], r=0.7, m=0.0),
}
mat_names = list(MATS)
buf = bytearray(); views = []; accs = []
def add_view(data, target=None):
    while len(buf) % 4: buf.append(0)
    off = len(buf); buf.extend(data)
    v = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
    if target: v["target"] = target
    views.append(v); return len(views) - 1
def add_acc(arr, ctype, typ, target=None, minmax=False):
    arr = np.ascontiguousarray(arr)
    vi = add_view(arr.tobytes(), target)
    a = {"bufferView": vi, "componentType": ctype, "count": int(arr.shape[0]), "type": typ}
    if minmax:
        a["min"] = arr.min(0).tolist(); a["max"] = arr.max(0).tolist()
    accs.append(a); return len(accs) - 1
def add_sparse_target(delta):
    nz = np.where(np.abs(delta).sum(1) > 1e-7)[0].astype(np.uint32)
    cnt = len(delta)
    mn = delta.min(0).tolist() if len(nz) else [0, 0, 0]; mx = delta.max(0).tolist() if len(nz) else [0, 0, 0]
    a = {"componentType": 5126, "count": cnt, "type": "VEC3", "min": mn, "max": mx}
    if len(nz):
        iv = add_view(nz.tobytes()); vv = add_view(np.ascontiguousarray(delta[nz].astype(np.float32)).tobytes())
        a["sparse"] = {"count": int(len(nz)), "indices": {"bufferView": iv, "componentType": 5125}, "values": {"bufferView": vv}}
    accs.append(a); return len(accs) - 1

def mesh_json(plist, name, with_targets):
    out = []
    for p in plist:
        n = normals(p.pos, p.idx)
        attrs = {"POSITION": add_acc(p.pos, 5126, "VEC3", 34962, True), "NORMAL": add_acc(n, 5126, "VEC3", 34962)}
        if p.col is not None: attrs["COLOR_0"] = add_acc(p.col.astype(np.float32), 5126, "VEC4", 34962)
        prim = {"attributes": attrs, "indices": add_acc(p.idx, 5125, "SCALAR", 34963), "material": mat_names.index(p.mat)}
        if with_targets: prim["targets"] = [{"POSITION": add_sparse_target(p.t[i])} for i in range(len(TARGETS))]
        out.append(prim)
    m = {"name": name, "primitives": out}
    if with_targets:
        m["weights"] = [0.0]*len(TARGETS); m["extras"] = {"targetNames": TARGETS}
    return m

def build(path, head_y=0.0):
    global buf, views, accs
    buf = bytearray(); views = []; accs = []
    meshes = [mesh_json(head_prims, "Face", True), mesh_json(body_prims, "Body", False)]
    gltf = {
        "asset": {"version": "2.0", "generator": "7PRO tutor builder", "copyright": "CC0 — generated for 7PRO"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [
            {"name": "MrAdam", "children": [1, 2]},
            {"name": "Body", "mesh": 1},
            {"name": "Head", "translation": [0, 0.0, 0], "children": [3]},
            {"name": "FaceMesh", "mesh": 0},
        ],
        "meshes": meshes,
        "materials": [{"name": k, "pbrMetallicRoughness": {"baseColorFactor": v["c"], "metallicFactor": v["m"], "roughnessFactor": v["r"]},
                       **({"doubleSided": True} if k in ("skin", "hair", "teeth") else {})} for k, v in MATS.items()],
        "accessors": accs, "bufferViews": views,
    }
    while len(buf) % 4: buf.append(0)
    gltf["buffers"] = [{"byteLength": len(buf)}]
    js = json.dumps(gltf, separators=(",", ":")).encode()
    while len(js) % 4: js += b" "
    total = 12 + 8 + len(js) + 8 + len(buf)
    with open(path, "wb") as fh:
        fh.write(struct.pack("<III", 0x46546C67, 2, total))
        fh.write(struct.pack("<II", len(js), 0x4E4F534A)); fh.write(js)
        fh.write(struct.pack("<II", len(buf), 0x004E4942)); fh.write(bytes(buf))
    return total

if __name__ == "__main__":
    n = build("/tmp/avatar/tutor.glb")
    print("glb bytes", n, "verts", sum(len(p.pos) for p in head_prims + body_prims), "tris", sum(len(p.idx) for p in head_prims + body_prims)//3)
