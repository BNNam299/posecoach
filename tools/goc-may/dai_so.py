# -*- coding: utf-8 -*-
"""Cat dai dong GOC MAY moi `buoc` giay, xep thanh nhieu tam, moi tam `moi` khung.
dai_so.py <video> <tien_to_ra> <buoc> <moi> [y0 y1]"""
import sys, cv2, numpy as np

video, ra, buoc, moi = sys.argv[1], sys.argv[2], float(sys.argv[3]), int(sys.argv[4])
y0 = float(sys.argv[5]) if len(sys.argv) > 5 else 0.600
y1 = float(sys.argv[6]) if len(sys.argv) > 6 else 0.662
cap = cv2.VideoCapture(video)
fps = cap.get(cv2.CAP_PROP_FPS)
n = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
dur = n / fps
ts, t = [], 1.0
while t < dur - 0.3:
    ts.append(round(t, 1)); t += buoc
rows, k = [], 0
def xa():
    global rows, k
    if rows:
        cv2.imwrite(f"{ra}-{k:02d}.jpg", np.vstack(rows), [cv2.IMWRITE_JPEG_QUALITY, 85])
        k += 1; rows = []
for t in ts:
    cap.set(cv2.CAP_PROP_POS_FRAMES, int(t * fps))
    ok, f = cap.read()
    if not ok: continue
    h, w = f.shape[:2]
    band = f[int(h * y0):int(h * y1), :]
    band = cv2.resize(band, (w // 2, band.shape[0] // 2))
    lab = np.full((22, w // 2, 3), 20, np.uint8)
    cv2.putText(lab, f"{t:.1f}s", (6, 17), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (80, 255, 255), 2)
    rows.append(np.vstack([lab, band]))
    if len(rows) == moi: xa()
xa()
print("xong", len(ts), "khung", k, "tam")
