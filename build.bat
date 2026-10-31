@echo off
rem ============================================================
rem 今日规划 · 一键打包脚本（无需 Android Studio）
rem
rem 用法一（交互式）：双击运行，按提示选择包类型并输入版本号
rem 用法二（命令行）：build.bat [debug^|release] [版本号]
rem   例：build.bat debug 1.0.50
rem       build.bat release 1.1.0
rem ============================================================
setlocal enabledelayedexpansion

rem 本机工具链路径
set "JAVA_HOME=D:\develop\jdk\jdk17"
set "ANDROID_HOME=D:\develop\android-sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

cd /d "%~dp0"

set "BUILD_TYPE=%~1"
set "NEW_NAME=%~2"

if /i "%BUILD_TYPE%"=="debug" goto :have_type
if /i "%BUILD_TYPE%"=="release" goto :have_type

rem ---------- 交互式选择包类型 ----------
echo ============================================================
echo   今日规划 · 打包脚本
echo ============================================================
echo   [1] 开发包 Debug   —— 可调试、体积大，自己测试用
echo   [2] 生产包 Release —— 正式分发用
echo ------------------------------------------------------------
set /p BUILD_TYPE=请选择构建类型 [1/2]（回车默认 1）：
if "%BUILD_TYPE%"=="" set "BUILD_TYPE=1"
if "%BUILD_TYPE%"=="1" set "BUILD_TYPE=debug"
if "%BUILD_TYPE%"=="2" set "BUILD_TYPE=release"
if /i "%BUILD_TYPE%"=="debug" goto :have_type
if /i "%BUILD_TYPE%"=="release" goto :have_type
echo.
echo 无效的选择：%BUILD_TYPE%
goto :fail

:have_type
rem ---------- 读取当前版本 ----------
set "CURRENT_NAME="
set "CURRENT_CODE="
for /f "usebackq tokens=1,2 delims==" %%a in ("version.properties") do (
    if /i "%%a"=="versionName" set "CURRENT_NAME=%%b"
    if /i "%%a"=="versionCode" set "CURRENT_CODE=%%b"
)
if "%CURRENT_NAME%"=="" set "CURRENT_NAME=1.0.0"
if "%CURRENT_CODE%"=="" set "CURRENT_CODE=1"

if not "%NEW_NAME%"=="" goto :version_ready
echo.
echo 当前版本：%CURRENT_NAME%  （versionCode %CURRENT_CODE%）
set /p NEW_NAME=请输入版本号（如 1.0.50，回车保持不变）：
:version_ready
if "%NEW_NAME%"=="" set "NEW_NAME=%CURRENT_NAME%"

rem ---------- 写入 version.properties（versionCode 由 Gradle 打包后自动 +1）----------
> version.properties echo #auto-incremented after assemble
>> version.properties echo versionName=%NEW_NAME%
>> version.properties echo versionCode=%CURRENT_CODE%
echo.
echo 版本号已设为：%NEW_NAME%  （versionCode 打包后自动 +1）

rem ---------- 构建 ----------
if /i "%BUILD_TYPE%"=="release" goto :build_release

echo.
echo [1/1] 构建开发包 Debug APK ...
call gradlew.bat assembleDebug
if not "%ERRORLEVEL%"=="0" goto :fail
set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
set "APK_TYPE=开发包（Debug）"
goto :succeed

:build_release
echo.
echo [1/1] 构建生产包 Release APK ...
call gradlew.bat assembleRelease
if not "%ERRORLEVEL%"=="0" goto :fail
set "APK_PATH=app\build\outputs\apk\release\app-release.apk"
set "APK_TYPE=生产包（Release）"

:succeed
echo.
echo ============================================================
echo 构建成功：%APK_TYPE%  %NEW_NAME%
echo   %APK_PATH%
echo.
echo 安装到已连接设备（需开启 USB 调试）：
echo   adb install -r "%APK_PATH%"
if /i "%BUILD_TYPE%"=="release" call :release_note
echo ============================================================
goto :done

:release_note
echo.
echo 签名说明：
echo   根目录有 keystore.properties 时使用其中的正式签名；
echo   没有时自动回退到 debug 签名，保证 APK 可直接安装。
exit /b 0

:fail
echo.
echo ============================================================
echo 构建失败，请查看上方日志。
echo ============================================================

:done
rem 命令行带参数调用时不暂停，便于脚本/CI 使用
if "%~1"=="" pause
endlocal
