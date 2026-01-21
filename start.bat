@echo off
chcp 65001 >nul
echo ========================================
echo   服务监控器 - ServiceMonitor
echo ========================================
echo.

REM 检查Java是否安装
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到Java，请先安装Java 17或更高版本
    echo 下载地址: https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)

REM 获取脚本所在目录
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM 查找JAR文件
set JAR_FILE=ServiceMonitor-1.0-SNAPSHOT.jar
if not exist "%JAR_FILE%" (
    echo [错误] 未找到JAR文件: %JAR_FILE%
    echo 请先运行: mvn clean package
    pause
    exit /b 1
)

echo 正在启动服务监控器...
echo.

REM 设置JVM参数
set JAVA_OPTS=-Xms128m -Xmx512m -Dfile.encoding=UTF-8

REM 启动应用
java %JAVA_OPTS% -jar "%JAR_FILE%"

if %errorlevel% neq 0 (
    echo.
    echo [错误] 程序异常退出，错误代码: %errorlevel%
    pause
)

