#!/bin/bash

# 设置编码
export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8

echo "========================================"
echo "  服务监控器 - ServiceMonitor"
echo "========================================"
echo ""

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 检查Java是否安装
if ! command -v java &> /dev/null; then
    echo "[错误] 未检测到Java，请先安装Java 17或更高版本"
    echo "安装命令: sudo apt-get install openjdk-17-jdk  (Ubuntu/Debian)"
    echo "          sudo yum install java-17-openjdk      (CentOS/RHEL)"
    exit 1
fi

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "[错误] Java版本过低，需要Java 17或更高版本"
    echo "当前版本: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

# 查找JAR文件
JAR_FILE="ServiceMonitor-1.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "[错误] 未找到JAR文件: $JAR_FILE"
    echo "请先运行: mvn clean package"
    exit 1
fi

echo "正在启动服务监控器..."
echo ""

# 设置JVM参数
JAVA_OPTS="-Xms128m -Xmx512m -Dfile.encoding=UTF-8"

# 检查是否有图形界面（用于服务器环境）
if [ -z "$DISPLAY" ] && [ -z "$WAYLAND_DISPLAY" ]; then
    echo "[警告] 未检测到图形界面环境"
    echo "如果通过SSH连接，请使用X11转发: ssh -X user@server"
    echo "或者使用VNC/远程桌面"
    echo ""
    read -p "是否继续启动? (y/n): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 0
    fi
fi

# 启动应用
java $JAVA_OPTS -jar "$JAR_FILE"

EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    echo "[错误] 程序异常退出，错误代码: $EXIT_CODE"
    exit $EXIT_CODE
fi

