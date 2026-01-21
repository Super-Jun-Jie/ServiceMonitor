@echo off
chcp 65001 >nul

REM ==================================================
REM  手动指定 JDK 17 路径（★只改这一行★）
REM ==================================================
set JAVA_HOME=C:\source\develop\java\jdk-17.0.4\bin
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

REM ==================================================
REM  ServiceMonitor 启动脚本
REM ==================================================
echo ========================================
echo   服务监控器 - ServiceMonitor
echo ========================================
echo.

REM 检查指定的 Java 是否可用
"%JAVA_EXE%" -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 指定的 Java 启动失败
    echo 请检查 JAVA_HOME 路径是否正确
    pause
    exit /b 1
)

REM 切换到脚本所在目录
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM JAR 文件名（如有版本变化，只改这里）
set JAR_FILE=ServiceMonitor-1.0-SNAPSHOT.jar

if not exist "%JAR_FILE%" (
    echo [错误] 未找到 JAR 文件: %JAR_FILE%
    echo 请先执行: mvn clean package
    pause
    exit /b 1
)

REM JVM 参数
set JAVA_OPTS=-Xms128m -Xmx512m -Dfile.encoding=UTF-8

echo 正在启动 ServiceMonitor...
echo.

REM 启动 ServiceMonitor
"%JAVA_EXE%" %JAVA_OPTS% -jar "%JAR_FILE%"

REM 异常退出提示
if %errorlevel% neq 0 (
    echo.
    echo [错误] ServiceMonitor 异常退出，错误码: %errorlevel%
    pause
)
