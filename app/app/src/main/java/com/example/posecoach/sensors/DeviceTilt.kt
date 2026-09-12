package com.example.posecoach.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Đọc độ nghiêng của máy từ cảm biến.
 *
 * Nuôi hai thứ:
 *   - Mục 4 (máy ngửa/chúc) — tiêu chí DUY NHẤT được siết tới 5°, vì cảm biến
 *     chính xác dưới 1°. Mọi tiêu chí khác phải nới rộng hơn nhiều.
 *   - Tốc độ góc — dùng cho cổng chống chụp lúc tay đang đưa (15°/s), cơ chế
 *     đóng băng hướng dẫn khi lắc mạnh (45°/s), và loại khung hình rung khi
 *     chọn ảnh.
 *
 * ⚠️ Lấy từ VECTOR TRỌNG LỰC, không lấy từ góc quay tổng hợp của hệ thống.
 * Bản iOS đã học bài này: dùng `attitude.pitch` bị "khoá gimbal" khi cầm máy
 * dựng đứng — đúng tư thế chụp ảnh dọc mà app này dùng nhiều nhất.
 */
class DeviceTilt(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gravitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    /** Máy này có đủ cảm biến không. Thiếu con quay hồi chuyển là hỏng 4 thứ (FOOTGUNS mục 2). */
    val hasGravity: Boolean get() = gravitySensor != null
    val hasGyroscope: Boolean get() = gyroSensor != null

    @Volatile var state: Reading = Reading()
        private set

    data class Reading(
        /**
         * Góc ngẩng của trục ống kính so với MẶT PHẲNG NGANG, độ.
         * Dương = máy ngửa lên trời. Âm = máy chúc xuống đất. 0 = ngang.
         */
        val cameraPitchDeg: Double = 0.0,
        /** Độ vẹo chân trời, độ. Chỉ đọc để tự nắn ảnh, KHÔNG dùng để hướng dẫn. */
        val rollDeg: Double = 0.0,
        /** Tốc độ quay của máy, độ/giây. Càng lớn = tay càng đang di chuyển. */
        val angularSpeedDegPerSec: Double = 0.0,
        val hasData: Boolean = false,
    )

    fun start() {
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                val gx = event.values[0].toDouble()
                val gy = event.values[1].toDouble()
                val gz = event.values[2].toDouble()
                val mag = sqrt(gx * gx + gy * gy + gz * gz)
                if (mag < 1e-6) return

                // --- Cách suy góc ngẩng của ống kính ---
                // Hệ trục của Android (máy cầm dọc, nhìn vào màn hình):
                //   x sang phải · y lên phía đỉnh máy · z hướng ra phía người dùng
                // Camera SAU nhìn theo hướng ngược lại màn hình, tức trục (0, 0, -1).
                // Cảm biến trọng lực trả về vector HƯỚNG LÊN (quy ước gia tốc kế):
                // đặt máy nằm ngửa trên bàn thì đọc được (0, 0, +9.81).
                //
                // sin(góc ngẩng) = tích vô hướng của trục ống kính với hướng lên
                //                = (0,0,-1) · (gx,gy,gz)/|g|
                //                = -gz / |g|
                //
                // Kiểm lại bằng 3 tư thế:
                //   nằm ngửa trên bàn (camera chúc xuống đất): gz=+9.81 → -90° ✓
                //   cầm dọc bình thường (camera nhìn ngang):   gy=+9.81 → 0°   ✓
                //   nằm sấp trên bàn (camera nhìn lên trời):   gz=-9.81 → +90° ✓
                val sinPitch = (-gz / mag).coerceIn(-1.0, 1.0)
                val pitch = Math.toDegrees(asin(sinPitch))

                // Độ vẹo. Dấu cần kiểm lại trên máy thật, nhưng KHÔNG gấp: tài liệu
                // chốt là không hướng dẫn người dùng nắn vẹo, chỉ tự nắn ảnh dưới 3°.
                val roll = Math.toDegrees(atan2(-gx, gy))

                state = state.copy(cameraPitchDeg = pitch, rollDeg = roll, hasData = true)
            }

            Sensor.TYPE_GYROSCOPE -> {
                // Con quay trả về radian/giây quanh 3 trục. Lấy độ lớn tổng hợp.
                val wx = event.values[0].toDouble()
                val wy = event.values[1].toDouble()
                val wz = event.values[2].toDouble()
                val speed = Math.toDegrees(sqrt(wx * wx + wy * wy + wz * wz))
                state = state.copy(angularSpeedDegPerSec = speed)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
