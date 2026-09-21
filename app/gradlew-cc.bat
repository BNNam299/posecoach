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
REM ⚠️ KHÔNG ghi đường dẫn ổ đĩa ở đây — file này nằm trong Git (21/09/2026). Mỗi máy
REM    tự đặt biến JAVA_HOME; thiếu thì script báo rõ rồi dừng.
REM ---------------------------------------------------------------------------

if "%JAVA_HOME%"=="" (
    echo Chua dat bien JAVA_HOME. Dat JAVA_HOME tro toi thu muc Java cua may nay ^(Android Studio co san: thu muc "jbr" trong thu muc cai Android Studio^) roi chay lai.
    exit /b 1
)

call "%~dp0gradlew.bat" %*
exit /b %ERRORLEVEL%
