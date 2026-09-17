@echo off
REM ---------------------------------------------------------------------------
REM gradlew-cc.bat - script bọc quanh gradlew.bat, dùng khi build bằng dòng lệnh.
REM
REM VÌ SAO CẦN: Java đi kèm sẵn bên trong Android Studio, không nằm trong PATH
REM của hệ thống. gradlew.bat tự kiểm tra biến JAVA_HOME NGAY TỪ ĐẦU, trước khi
REM Gradle kịp khởi động - nên đặt org.gradle.java.home trong gradle.properties
REM không cứu được bước này.
REM
REM Chỉ đặt JAVA_HOME khi nó còn trống, để không đè lên cấu hình sẵn có của máy.
REM
REM ⚠️ Đường dẫn dưới đây RIÊNG CHO MÁY NÀY. Máy khác cài Android Studio ở chỗ
REM    khác thì sửa lại dòng set bên dưới.
REM ---------------------------------------------------------------------------

if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Users\nambn\AppData\Local\Programs\Temurin\jdk-17.0.20.1+1"

call "%~dp0gradlew.bat" %*
exit /b %ERRORLEVEL%
