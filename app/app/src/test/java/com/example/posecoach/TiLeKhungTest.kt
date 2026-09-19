package com.example.posecoach

import com.example.posecoach.capture.TiLeKhung
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tỉ lệ khung người dùng chọn (19/09/2026). Xem `TiLeKhung`. */
class TiLeKhungTest {

    @Test
    fun `bon ti le tieu chuan va Full lay theo man hinh`() {
        assertEquals(0.75, TiLeKhung.BA_BON.tiLe(0.46), 1e-9)
        assertEquals(9.0 / 16.0, TiLeKhung.CHIN_MUOI_SAU.tiLe(0.46), 1e-9)
        assertEquals(1.0, TiLeKhung.VUONG.tiLe(0.46), 1e-9)
        assertEquals(0.46, TiLeKhung.FULL.tiLe(0.46), 1e-9)
    }

    @Test
    fun `bam nut di vong qua du bon muc roi quay lai`() {
        var t = TiLeKhung.BA_BON
        val da = mutableListOf(t)
        repeat(4) { t = t.tiep(); da += t }
        assertEquals(listOf("3:4", "9:16", "1:1", "Full", "3:4"), da.map { it.nhan })
    }

    @Test
    fun `cam may ngang thi lat ti le`() {
        assertEquals(0.75, TiLeKhung.theoHuong(0.75, 480, 640), 1e-9)
        assertEquals(1 / 0.75, TiLeKhung.theoHuong(0.75, 640, 480), 1e-9)
    }

    @Test
    fun `khung 9_16 cat hai ben, nguoi dung giua van o giua, nguoi lech bi day ra mep`() {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        pts[Lm.NOSE] = P2(0.5, 0.3); vis[Lm.NOSE] = 0.99f
        pts[Lm.LEFT_SHOULDER] = P2(0.2, 0.4); vis[Lm.LEFT_SHOULDER] = 0.99f
        val f = PoseFrame(pts, vis, MutableList(33) { P3(0.0, 0.0, 0.0) }, 0L)
        val c = f.croppedToAspect(9.0 / 16.0, 3.0 / 4.0)
        assertEquals(0.5, c.points[Lm.NOSE].x, 1e-9)
        assertEquals(0.3, c.points[Lm.NOSE].y, 1e-9)
        // Khung 9:16 hẹp bằng 3/4 khung 3:4: điểm ở 0,2 ra (0,2 − 0,125) / 0,75 = 0,1.
        assertEquals(0.1, c.points[Lm.LEFT_SHOULDER].x, 1e-9)
    }
}
