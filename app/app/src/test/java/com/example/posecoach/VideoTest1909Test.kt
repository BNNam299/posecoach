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
}
