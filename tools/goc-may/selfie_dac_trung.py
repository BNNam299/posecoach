# -*- coding: utf-8 -*-
"""Selfie: dac trung tu anh (MediaPipe Pose, dung model cua app) doi chieu goc may cam bien.
Video: test-media/9-selfie-goc/selfie-goc.mp4 (15/09/2026)."""
import sys, math, cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks import python as mpp
from mediapipe.tasks.python import vision
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
MODEL = r"D:\PROJECTS\Pj-demo\app\app\src\main\assets\pose_landmarker_full.task"
VIDEO = r"D:\PROJECTS\Pj-demo\test-media\9-selfie-goc\selfie-goc.mp4"
det = vision.PoseLandmarker.create_from_options(vision.PoseLandmarkerOptions(
    base_options=mpp.BaseOptions(model_asset_path=MODEL),
    running_mode=vision.RunningMode.IMAGE, num_poses=1))
NHAN = [(13,24),(15,17),(17,11),(19,2),(21,2),(23,3),(25,-4),(27,-1),(29,1),(31,-1),(33,0),(35,1),
(37,0),(39,-1),(41,-1),(43,3),(45,2),(47,2),(49,1),(51,2),(53,4),(55,-5),(57,7),(59,4),(61,5),(63,8),
(65,-24),(67,-32),(69,-31),(71,-31),(73,-30),(75,-29),(77,-27),(79,-25),(81,-25),(83,-25),(85,-21),
(87,-27),(89,-42),(91,-39),(93,-34),(95,-37),(97,-57),(99,-56),(101,-50),(103,-29),(105,6),(107,-21),
(109,-39),(111,-46),(113,-46),(115,-17),(117,-11),(119,-4),(121,-4),(123,34),(125,51),(127,49),
(129,50),(131,45),(133,45),(135,21),(137,25),(139,19),(141,20),(143,21),(145,1),(147,39),(149,27),
(151,35),(153,37),(155,45),(157,7)]
NOSE, LEYE, REYE, MOUTH_L, MOUTH_R, L_SH, R_SH = 0, 2, 5, 9, 10, 11, 12
cap = cv2.VideoCapture(VIDEO); fps = cap.get(cv2.CAP_PROP_FPS)
ang = lambda v: math.degrees(math.atan2(v[2], -v[1]))
rows = []
for t, s in NHAN:
    feats = []
    for dt in (-0.2, 0.0, 0.2):
        cap.set(cv2.CAP_PROP_POS_FRAMES, int((t + dt) * fps))
        ok, f = cap.read()
        if not ok: continue
        h = f.shape[0]
        f = f[:int(h * 0.72)]
        r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(f, cv2.COLOR_BGR2RGB)))
        if not r.pose_landmarks: continue
        p = np.array([[l.x, l.y * 0.72] for l in r.pose_landmarks[0]])  # y theo ti le khung goc
        H, W = f.shape[0] / 0.72, f.shape[1]
        p[:, 0] *= W; p[:, 1] *= H  # ra pixel de ti le dung
        v = np.array([l.visibility for l in r.pose_landmarks[0]])
        w = np.array([[l.x, l.y, l.z] for l in r.pose_world_landmarks[0]])
        if min(v[L_SH], v[R_SH], v[NOSE]) < 0.5: continue
        sh2 = (p[L_SH] + p[R_SH]) / 2; span = np.linalg.norm(p[L_SH] - p[R_SH])
        eye2 = (p[LEYE] + p[REYE]) / 2; mouth2 = (p[MOUTH_L] + p[MOUTH_R]) / 2
        sh3 = (w[L_SH] + w[R_SH]) / 2
        feats.append([
            ang(w[NOSE] - sh3),                          # co-dau 3D
            (sh2[1] - p[NOSE][1]) / span,                # co bieu kien / vai
            (mouth2[1] - eye2[1]) / span,                # mat-mieng / vai
            (p[NOSE][1] - eye2[1]) / np.linalg.norm(p[LEYE] - p[REYE]),  # mui duoi mat / 2 mat
            (mouth2[1] - p[NOSE][1]) / (p[NOSE][1] - eye2[1] + 1e-6),    # mieng-mui / mui-mat
            sh2[1] / H,                                  # vai o dau khung
        ])
    if feats:
        rows.append([t, s] + list(np.median(np.array(feats), axis=0)))
A = np.array(rows)
TEN = ["co-dau 3D", "co/vai", "mat-mieng/vai", "mui/2mat", "mieng-mui/mui-mat", "vai y"]
print(f"n = {len(A)} / {len(NHAN)}")
s = A[:, 1]
for i, ten in enumerate(TEN):
    x = A[:, 2 + i]
    r = np.corrcoef(x, s)[0, 1]
    k, b = np.polyfit(x, s, 1)
    e = np.abs(k * x + b - s).mean()
    print(f"{ten:20s} r={r:+.3f}  du doan goc -> sai so TB {e:5.1f} do")
# ket hop tuyen tinh tat ca
X = np.c_[A[:, 2:], np.ones(len(A))]
c, *_ = np.linalg.lstsq(X, s, rcond=None)
print(f"ket hop tat ca: sai so TB {np.abs(X @ c - s).mean():.1f} do")
# phan 3 nhom
nhom = lambda g: np.where(g <= -15, 0, np.where(g >= 15, 2, 1))
np.save(r"C:\Users\BNNAM\AppData\Local\Temp\claude\d--PROJECTS-Pj-demo\f5bb0ec0-8855-49ce-9c37-c32cab065072\scratchpad\selfie_feats.npy", A)
for i, ten in enumerate(TEN):
    x = A[:, 2 + i]
    print(f"  {ten:20s}", "  ".join(f"{['tren','ngang','duoi'][g]}: {x[nhom(s)==g].mean():+.3f}±{x[nhom(s)==g].std():.3f}" for g in range(3)))
