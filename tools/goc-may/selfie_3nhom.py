# -*- coding: utf-8 -*-
"""Phan 3 nhom tren/ngang/duoi tu dac trung mat-co-vai, kiem bo-mot-ra."""
import numpy as np
from pathlib import Path
# Duong dan TUONG DOI theo vi tri du an — khong ghi o dia hay ten nguoi dung may.
GOC = Path(__file__).resolve().parents[2]          # thu muc goc du an
MODEL_APP = GOC / "app" / "app" / "src" / "main" / "assets" / "pose_landmarker_full.task"
TEST_MEDIA = GOC / "test-media"
KET_QUA = Path(__file__).resolve().parent / "_ket-qua"  # khong day len Git
KET_QUA.mkdir(exist_ok=True)
A = np.load(KET_QUA / "selfie_feats.npy")
s = A[:, 1]; X = A[:, 2:]
y = np.where(s <= -15, 0, np.where(s >= 15, 2, 1))
print("so khung moi nhom (tren, ngang, duoi):", [int((y == g).sum()) for g in range(3)])
ok = 0; nham_nguoc = 0; conf = np.zeros((3, 3), int)
for i in range(len(A)):
    m = np.ones(len(A), bool); m[i] = False
    mu, sd = X[m].mean(0), X[m].std(0) + 1e-9
    Z = (X[m] - mu) / sd; z = (X[i] - mu) / sd
    c = [Z[y[m] == g].mean(0) for g in range(3)]
    p = int(np.argmin([np.linalg.norm(z - cc) for cc in c]))
    conf[y[i], p] += 1
    ok += p == y[i]; nham_nguoc += abs(p - y[i]) == 2
print(f"trung {ok}/{len(A)} = {ok/len(A):.0%}   nham NGUOC HAN (tren<->duoi): {nham_nguoc}")
print("hang = that, cot = doan (tren, ngang, duoi)\n", conf)
print(f"doan bua theo nhom dong nhat: {max((y == g).mean() for g in range(3)):.0%}")
