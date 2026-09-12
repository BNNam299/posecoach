package com.example.posecoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posecoach.capture.CaptureScreen
import com.example.posecoach.capture.ShotStore
import com.example.posecoach.home.HomeScreen
import com.example.posecoach.library.LibraryScreen
import com.example.posecoach.media.MediaLibrary
import com.example.posecoach.result.ResultScreen
import com.example.posecoach.ui.theme.Ds
import com.example.posecoach.ui.theme.PosecoachTheme
import java.io.File

/**
 * Điểm vào của app.
 *
 * ## Cấu trúc sau khi dọn (04/09/2026)
 *
 * Hai đường chạy thử đã **gỡ hẳn** khỏi mã nguồn:
 *
 * | Đã gỡ | Vì sao |
 * |---|---|
 * | Màn camera giả lập bằng video (`sim/`) | Đã làm xong việc của nó — giải rủi ro số 1, kiểm engine hướng dẫn khi chưa có người thật. Giữ lại chỉ làm code phình và mỗi lần sửa hướng dẫn phải sửa hai chỗ |
 * | Màn đo số liệu (`debug/`) | Các ngưỡng cần đo đã đo xong bằng ảnh thật + phân tích ngoài app |
 *
 * Còn đúng **một đường**: camera thật.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Dọn rác của lần chạy trước. Phải gọi lúc MỞ app: lúc đóng thì app đã bị
        // hệ điều hành giết, code dọn không bao giờ chạy tới.
        ShotStore.sweepOrphans(this)
        ShotStore.sweepRecordings(this)
        // Chép thư viện ảnh mẫu cài sẵn ra thư mục làm việc.
        MediaLibrary.seedBuiltInTemplates(this)

        setContent {
            PosecoachTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { AppRoot() }
            }
        }
    }
}

private sealed interface Screen {
    /** Hai tab chính. */
    data object Templates : Screen
    data object Library : Screen

    data class Capture(val template: File) : Screen
    data class Result(val template: File, val sessionDir: File) : Screen
}

private val Screen.isTab: Boolean get() = this is Screen.Templates || this is Screen.Library

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Templates) }

    BackHandler(enabled = !screen.isTab) { screen = Screen.Templates }

    when (val s = screen) {
        is Screen.Templates, is Screen.Library -> Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                if (s is Screen.Templates) {
                    HomeScreen(onPickTemplate = { file -> screen = Screen.Capture(file) })
                } else {
                    LibraryScreen()
                }
            }
            TabBar(
                current = s,
                onSelect = { screen = it },
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        is Screen.Capture -> CaptureScreen(
            templateFile = s.template,
            onBack = { screen = Screen.Templates },
            onFinished = { dir -> screen = Screen.Result(s.template, dir) },
        )

        is Screen.Result -> ResultScreen(
            templateFile = s.template,
            sessionDir = s.sessionDir,
            // HAI lối ra, khác nhau về ý định của người dùng:
            //  - "Lưu hết & chụp tiếp" -> quay thẳng lại camera, giữ nguyên ảnh mẫu
            //  - "Giữ ảnh này" / nút quay lại -> sang Thư viện xem thành quả
            onDone = { continueShooting ->
                screen = if (continueShooting) Screen.Capture(s.template) else Screen.Library
            },
        )
    }
}

@Composable
private fun TabBar(current: Screen, onSelect: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Ds.surface)
            .padding(vertical = 6.dp),
    ) {
        Tab("Ảnh mẫu", current is Screen.Templates, Modifier.weight(1f)) {
            onSelect(Screen.Templates)
        }
        Tab("Thư viện", current is Screen.Library, Modifier.weight(1f)) {
            onSelect(Screen.Library)
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) Ds.text else Ds.textMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}
