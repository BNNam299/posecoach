package com.example.posecoach

import com.example.posecoach.guidance.Band
import com.example.posecoach.guidance.CriterionStatus
import com.example.posecoach.guidance.CuePresenter
import com.example.posecoach.guidance.GateState
import com.example.posecoach.template.Criterion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Những gì video test 19/09/2026 lộ ra — khoá lại để không quay về. */
class VideoTest1909Test {

    private val band = Band(accept = 0.1, enter = 0.15, unlock = 0.3, actionFloor = 0.02)

    private fun cau(tamTay: Boolean) = CuePresenter().update(
        listOf(
            CriterionStatus(
                Criterion.ELEVATION, GateState.UNMEASURED, null, null, band,
                unmeasuredTooLong = true, tamTay = tamTay,
            )
        ),
        0L, 0.0,
    )!!

    @Test
    fun `selfie khong duoc bao lui ra`() {
        val t = cau(tamTay = true)
        assertFalse(t, t.contains("lùi"))
        assertTrue(t, t.contains("duỗi tay"))
    }

    @Test
    fun `nguoi khac chup van bao lui ra`() {
        assertTrue(cau(tamTay = false).contains("lùi ra"))
    }

    @Test
    fun `he so ong kinh tinh tu goc mo that, trung hang so cu voi may 73 do`() {
        val h = com.example.posecoach.guidance.DistanceEstimator.heSoOngKinh(73.0, 1f)!!
        assertTrue("$h", kotlin.math.abs(h - 0.678) < 0.01)
        // Zoom 2x thì góc mở hẹp lại, hệ số quy về 1x phải y nguyên.
        val v2 = Math.toDegrees(2 * kotlin.math.atan(kotlin.math.tan(Math.toRadians(73.0) / 2) / 2))
        val h2 = com.example.posecoach.guidance.DistanceEstimator.heSoOngKinh(v2, 2f)!!
        assertTrue(kotlin.math.abs(h2 - h) < 1e-9)
        assertTrue(com.example.posecoach.guidance.DistanceEstimator.heSoOngKinh(null, 1f) == null)
    }
}