# -*- coding: utf-8 -*-
"""Selfie: canh tay cam may co bao goc may khong? Doi chieu cam bien."""
import sys, math, cv2, runpy
import numpy as np
import mediapipe as mp
from mediapipe.tasks import python as mpp
from mediapipe.tasks.python import vision
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
SP = r"C:\Users\BNNAM\AppData\Local\Temp\claude\d--PROJECTS-Pj-demo\f5bb0ec0-8855-49ce-9c37-c32cab065072\scratchpad"
src = open(SP + r"\selfie_dac_trung.py", encoding="utf-8").read()
NHAN = eval(src[src.index("NHAN = ") + 7: src.index("]", src.index("NHAN = ")) + 1])
MODEL = r"D:\PROJECTS\Pj-demo\app\app\src\main\assets\pose_landmarker_full.task"
VIDEO = r"D:\PROJECTS\Pj-demo\test-media\9-selfie-goc\selfie-goc.mp4"
det = vision.PoseLandmarker.create_from_options(vision.PoseLandmarkerOptions(
    base_options=mpp.BaseOptions(model_asset_path=MODEL),
    running_mode=vision.RunningMode.IMAGE, num_poses=1))
L_SH, R_SH, L_EL, R_EL, L_WR, R_WR = 11, 12, 13, 14, 15, 16
cap = cv2.VideoCapture(VIDEO); fps = cap.get(cv2.CAP_PROP_FPS)
ang = lambda v: math.degrees(math.atan2(v[2], -v[1]))
rows = []; thay = 0
for t, s in NHAN:
    cap.set(cv2.CAP_PROP_POS_FRAMES, int(t * fps))
    ok, f = cap.read()
    if not ok: continue
    f = f[:int(f.shape[0] * 0.72)]
    r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(f, cv2.COLOR_BGR2RGB)))
    if not r.pose_landmarks: continue
    lm = r.pose_landmarks[0]; w = np.array([[l.x, l.y, l.z] for l in r.pose_world_landmarks[0]])
    vis = lambda i: lm[i].visibility >= 0.5 and 0 <= lm[i].x <= 1 and 0 <= lm[i].y <= 1
    # tay cam may = tay co khuyu tay lo ro hon
    best = None
    for sh, el in ((L_SH, L_EL), (R_SH, R_EL)):
        if vis(sh) and vis(el):
            cand = (lm[el].visibility, sh, el)
            best = max(best, cand) if best else cand
    if not best:
        rows.append((t, s, None, None)); continue
    thay += 1
    _, sh, el = best
    span = math.hypot(lm[L_SH].x - lm[R_SH].x, (lm[L_SH].y - lm[R_SH].y) * f.shape[0] / f.shape[1])
    cao2d = (lm[sh].y - lm[el].y) * f.shape[0] / f.shape[1] / max(span, 1e-3)  # duong = khuyu cao hon vai
    goc3d = ang(w[el] - w[sh])  # huong canh tay tren trong 3D
    rows.append((t, s, cao2d, goc3d))
ok = [r for r in rows if r[2] is not None]
print(f"thay khuyu tay: {thay}/{len(rows)} khung")
if len(ok) > 3:
    a = np.array(ok, float)
    for i, ten in ((2, "khuyu cao hon vai (2D)"), (3, "canh tay tren 3D")):
        print(f"{ten:24s} r={np.corrcoef(a[:, i], a[:, 1])[0, 1]:+.3f}")
for t, s, c, g in rows:
    print(f"{t:5.0f}s cam bien {s:+4.0f}  " + (f"khuyu/vai {c:+.2f}  tay3D {g:+6.1f}" if c is not None else "khong thay khuyu"))
