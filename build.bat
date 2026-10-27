@echo off
rem ============================================================
rem 专注清单一键打包脚本（无需 Android Studio）
rem 双击运行即可产出 app-debug.apk
rem ============================================================
setlocal

rem 本机工具链路径
set "JAVA_HOME=D:\develop\jdk\jdk17"
set "ANDROID_HOME=D:\develop\android-sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

cd /d "%~dp0"

if "%~1"=="release" (
    echo [1/1] 构建 Release APK ...
    call gradlew.bat assembleRelease
) else (
    echo [1/1] 构建 Debug APK ...
    call gradlew.bat assembleDebug
)

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================================
    echo 构建成功，APK 位于：
    echo   app\build\outputs\apk\debug\app-debug.apk
    echo   app\build\outputs\apk\release\app-release.apk
    echo.
    echo 连接手机并开启 USB 调试后，可执行安装：
    echo   adb install app\build\outputs\apk\debug\app-debug.apk
    echo ============================================================
) else (
    echo.
    echo 构建失败，请查看上方日志。
)

endlocal
pause