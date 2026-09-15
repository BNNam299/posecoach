# -*- coding: utf-8 -*-
"""Phan 3 nhom tren/ngang/duoi tu dac trung mat-co-vai, kiem bo-mot-ra."""
import numpy as np
A = np.load(r"C:\Users\BNNAM\AppData\Local\Temp\claude\d--PROJECTS-Pj-demo\f5bb0ec0-8855-49ce-9c37-c32cab065072\scratchpad\selfie_feats.npy")
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
