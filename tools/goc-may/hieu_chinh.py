# -*- coding: utf-8 -*-
import runpy, numpy as np
ns = runpy.run_path(r"C:\Users\BNNAM\AppData\Local\Temp\claude\d--PROJECTS-Pj-demo\f5bb0ec0-8855-49ce-9c37-c32cab065072\scratchpad\lech_goc.py")
a = np.array(ns["cap"], float); s, g = a[:, 1], a[:, 2]
print("\n--- chia theo SO SUY TU ANH (thu app thuc su co voi anh mau) ---")
for lo, hi in [(-60, -35), (-35, -25), (-25, -20), (-20, -15), (-15, -10), (-10, -5), (-5, 5)]:
    m = (g >= lo) & (g < hi)
    if m.any():
        print(f"anh [{lo:+},{hi:+})  n={m.sum():2d}  anh trung vi {np.median(g[m]):+.0f}  cam bien trung vi {np.median(s[m]):+.0f}  (tu {s[m].min():+.0f} den {s[m].max():+.0f})")

def loo(fn):
    e = []
    for i in range(len(a)):
        k = np.ones(len(a), bool); k[i] = False
        e.append(abs(fn(g[k], s[k], g[i]) - s[i]))
    return np.mean(e)

def lin(gx, sx, q):
    k, b = np.polyfit(gx, sx, 1); return k * q + b
def none(gx, sx, q): return q
def const(gx, sx, q): return q + np.mean(sx - gx)
def piece(gx, sx, q):
    xs = [-50, -20, -8]
    ys = [np.median(sx[np.abs(gx - x) <= 6]) for x in xs]
    if q <= xs[0]: return ys[0] + (q - xs[0])
    if q >= xs[-1]: return ys[-1] + (q - xs[-1])
    return np.interp(q, xs, ys)
for ten, fn in [("khong sua", none), ("cong hang so", const), ("tuyen tinh", lin), ("gay khuc 3 moc", piece)]:
    print(f"{ten:16s} sai so TB (bo-mot-ra) {loo(fn):.1f} do")
k, b = np.polyfit(g, s, 1); print(f"tuyen tinh: cam bien = {k:.2f} * anh {b:+.1f}")
xs = [-50, -20, -8]; print("gay khuc:", [(x, float(np.median(s[np.abs(g - x) <= 6]))) for x in xs])
