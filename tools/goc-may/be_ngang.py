# -*- coding: utf-8 -*-
"""Thu tach 3 kieu chup tu tren cao bang BE NGANG (vai/hong/goi/co chan/tai),
chuan hoa theo be ngang that (worldLandmarks). Kem luoi anh de xem."""
import os, sys, math, glob
import numpy as np, cv2
import mediapipe as mp
from mediapipe.tasks import python as mpp
from mediapipe.tasks.python import vision
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = r"D:\PROJECTS\Pj-demo\test-media\11-tren-cao-khoang-cach"
MODEL = r"D:\PROJECTS\Pj-demo\app\app\src\main\assets\pose_landmarker_full.task"
SP = r"C:\Users\BNNAM\AppData\Local\Temp\claude\d--PROJECTS-Pj-demo\f5bb0ec0-8855-49ce-9c37-c32cab065072\scratchpad"
det = vision.PoseLandmarker.create_from_options(vision.PoseLandmarkerOptions(
    base_options=mpp.BaseOptions(model_asset_path=MODEL), running_mode=vision.RunningMode.IMAGE, num_poses=1))
NHOM = ["gan-1x", "goc-rong-0.5x", "xa-zoom"]
CAP = {"tai": (7, 8), "vai": (11, 12), "hong": (23, 24), "goi": (25, 26), "cochan": (27, 28)}
V = 0.5
rows, cells = [], []
for g in NHOM:
    for f in sorted(glob.glob(os.path.join(ROOT, g, "*"))):
        im = cv2.imdecode(np.fromfile(f, np.uint8), cv2.IMREAD_COLOR)
        if im is None: continue
        h, w = im.shape[:2]
        r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(im, cv2.COLOR_BGR2RGB)))
        th = cv2.resize(im, (int(w * 360 / h), 360))
        cv2.putText(th, g[:6] + " " + os.path.basename(f)[:6], (4, 20), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 255, 255), 2)
        dat = {}
        if r.pose_landmarks:
            lm = r.pose_landmarks[0]; wl = r.pose_world_landmarks[0]
            ok = lambda i: lm[i].visibility >= V and 0 <= lm[i].x <= 1 and 0 <= lm[i].y <= 1
            for ten, (a, b) in CAP.items():
                if ok(a) and ok(b):
                    img = math.hypot((lm[a].x - lm[b].x) * w, (lm[a].y - lm[b].y) * h)
                    wd = math.dist((wl[a].x, wl[a].y, wl[a].z), (wl[b].x, wl[b].y, wl[b].z))
                    if wd > 1e-3 and img > 1: dat[ten] = img / wd   # do phong dai tai bo phan do
            for i in range(33):
                if lm[i].visibility >= V:
                    cv2.circle(th, (int(lm[i].x * th.shape[1]), int(lm[i].y * 360)), 3, (0, 255, 0), -1)
        cells.append(th)
        def lr(a, b): return math.log(dat[a] / dat[b]) if a in dat and b in dat else None
        rows.append((g, os.path.basename(f)[:8], lr("vai", "hong"), lr("vai", "goi"), lr("vai", "cochan"), lr("tai", "cochan"), sorted(dat)))
# in bang
fmt = lambda x: f"{x:+.2f}" if x is not None else "  -- "
print(f"{'nhom':14} {'anh':9} {'vai/hong':>8} {'vai/goi':>8} {'vai/chan':>8} {'tai/chan':>8}  thay")
for g, n, a, b, c, d, s in rows:
    print(f"{g:14} {n:9} {fmt(a):>8} {fmt(b):>8} {fmt(c):>8} {fmt(d):>8}  {','.join(s)}")
print()
for k, ten in [(2, "vai/hong"), (3, "vai/goi"), (4, "vai/chan"), (5, "tai/chan")]:
    s = []
    for g in NHOM:
        v = [r[k] for r in rows if r[0] == g and r[k] is not None]
        s.append(f"{g}: n={len(v)} " + (f"{np.mean(v):+.2f} [{min(v):+.2f},{max(v):+.2f}]" if v else "-"))
    print(f"{ten:9}", " | ".join(s))
# luoi anh
W = max(c.shape[1] for c in cells)
cells = [cv2.copyMakeBorder(c, 0, 0, 0, W - c.shape[1], cv2.BORDER_CONSTANT) for c in cells]
while len(cells) % 6: cells.append(np.zeros_like(cells[0]))
cv2.imwrite(os.path.join(SP, "tren_luoi.jpg"), np.vstack([np.hstack(cells[i:i + 6]) for i in range(0, len(cells), 6)]))
