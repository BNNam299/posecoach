plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.posecoach"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.posecoach"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    /**
     * TACH APK THEO LOAI CHIP.
     *
     * Vi sao: mot APK "gop tat" nang 135 MB vi phai chua thu vien may cho CA
     * bon loai chip. Moi may chi dung dung MOT loai, ba loai con lai la rac.
     *
     * Bat cai nay thi Gradle sinh ra bon file rieng trong
     * app/build/outputs/apk/debug/ :
     *
     *   app-arm64-v8a-debug.apk    -> DIEN THOAI THAT (gan nhu moi may Android 10+)
     *   app-armeabi-v7a-debug.apk  -> dien thoai 32-bit doi cu, rat hiem
     *   app-x86_64-debug.apk       -> MAY AO trong Android Studio
     *   app-x86-debug.apk          -> may ao 32-bit doi cu
     *
     * isUniversalApk = true de VAN giu them mot file gop tat, phong khi can
     * mot file chay duoc o moi noi (vi du gui cho nguoi khac ma khong biet may ho).
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    androidResources {
        // File model của MediaPipe (.task) phải nằm nguyên dạng trong APK, KHÔNG được nén.
        // Thư viện nạp model bằng cách ánh xạ thẳng vào bộ nhớ; nếu file bị nén thì
        // lúc chạy sẽ báo lỗi không đọc được model - lỗi chỉ lộ ra khi chạy thật,
        // build vẫn thành công bình thường.
        noCompress += "task"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- PoseCoach: camera ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)      // nền tảng camera thực tế bên dưới
    implementation(libs.androidx.camera.lifecycle)    // tự bật/tắt camera theo vòng đời màn hình
    implementation(libs.androidx.camera.view)         // khung hiển thị hình camera
    implementation(libs.androidx.camera.video)        // quay video 15-30 giây

    // --- PoseCoach: nhận diện khung xương người ---
    implementation(libs.mediapipe.tasks.vision)

    // --- PoseCoach: goc mat + mat nham (tieu chi 1 cho anh chan dung, va hau ky) ---
    implementation(libs.mlkit.face.detection)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}