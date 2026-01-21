# 服务监控器 - ServiceMonitor

一个基于Java Swing的桌面应用程序，用于监控和管理多个Java服务的运行状态。

## 功能特性

- ✅ 多服务管理：支持添加、删除、启动、停止、重启多个服务
- ✅ 自动重启：服务异常退出时自动重启
- ✅ 实时监控：实时显示服务状态和进程PID
- ✅ 配置持久化：配置自动保存到本地文件
- ✅ 一键操作：支持一键启动/停止所有服务
- ✅ 日志查看：实时查看服务运行日志

## 系统要求

- Java 17 或更高版本
- Windows / Linux / macOS
- 图形界面环境（GUI应用）

## 快速开始

### 1. 编译打包

```bash
# 使用Maven编译打包
mvn clean package

# 打包完成后，在 target 目录会生成：
# - ServiceMonitor-1.0-SNAPSHOT.jar (可执行JAR，包含所有依赖)
```

### 2. 运行方式

#### Windows系统

**方式一：使用启动脚本（推荐）**
```bash
# 双击运行
start.bat

# 或命令行运行
start.bat
```

**方式二：直接运行JAR**
```bash
java -jar target/ServiceMonitor-1.0-SNAPSHOT.jar
```

#### Linux系统

**方式一：使用启动脚本（推荐）**
```bash
# 添加执行权限
chmod +x start.sh

# 运行
./start.sh
```

**方式二：直接运行JAR**
```bash
java -jar target/ServiceMonitor-1.0-SNAPSHOT.jar
```

#### 服务器环境（无图形界面）

如果服务器没有图形界面，需要通过以下方式：

**方式一：X11转发（SSH）**
```bash
# 连接时启用X11转发
ssh -X user@server

# 然后运行
./start.sh
```

**方式二：VNC/远程桌面**
- 安装VNC服务器或远程桌面
- 通过远程桌面连接后运行应用

**方式三：使用Xvfb（虚拟显示）**
```bash
# 安装Xvfb
sudo apt-get install xvfb  # Ubuntu/Debian
sudo yum install xorg-x11-server-Xvfb  # CentOS/RHEL

# 使用虚拟显示运行
xvfb-run -a java -jar ServiceMonitor-1.0-SNAPSHOT.jar
```

## 使用说明

### 添加服务

1. 点击"添加服务"按钮
2. 填写服务信息：
   - **服务名称**：服务的显示名称
   - **Java路径**：Java可执行文件的完整路径
   - **工作目录**：服务运行的工作目录（JAR文件所在目录）
   - **启动参数**：每行一个参数，例如：
     ```
     -jar
     myapp.jar
     --spring.profiles.active=prod
     ```
   - **输出日志**：标准输出日志文件路径
   - **错误日志**：错误输出日志文件路径
3. 点击"确定"保存

### 管理服务

- **启动**：点击服务行的"启动"按钮
- **停止**：点击服务行的"停止"按钮
- **重启**：点击服务行的"重启"按钮
- **删除**：点击服务行的"删除"按钮
- **一键启动所有**：启动所有未运行的服务
- **一键停止所有**：停止所有正在运行的服务

### 配置文件

配置文件保存在程序运行目录的 `services.txt` 文件中，格式为：
```
服务名称|||Java路径|||工作目录|||启动参数（换行分隔）|||输出日志|||错误日志
```

可以直接编辑此文件，然后点击"刷新配置"按钮重新加载。

## 部署到服务器

### 1. 准备文件

将以下文件上传到服务器：
- `ServiceMonitor-1.0-SNAPSHOT.jar`
- `start.sh` (Linux) 或 `start.bat` (Windows)
- `services.txt` (可选，如果有配置)

### 2. 设置权限

```bash
chmod +x start.sh
chmod +x ServiceMonitor-1.0-SNAPSHOT.jar
```

### 3. 运行

```bash
./start.sh
```

### 4. 后台运行（可选）

使用 `nohup` 或 `screen` 在后台运行：

```bash
# 使用nohup
nohup ./start.sh > monitor.log 2>&1 &

# 或使用screen
screen -S monitor
./start.sh
# 按 Ctrl+A 然后 D 退出screen
```

## 常见问题

### Q: 服务启动后立即退出？

A: 检查以下几点：
- Java路径是否正确
- 工作目录是否存在
- JAR文件是否存在
- 查看错误日志文件（system.err.log）

### Q: 在服务器上无法显示界面？

A: 确保：
- 已安装图形界面环境
- 使用X11转发或VNC/远程桌面
- 或使用Xvfb虚拟显示

### Q: 如何修改JVM参数？

A: 编辑启动脚本，修改 `JAVA_OPTS` 变量：
```bash
JAVA_OPTS="-Xms256m -Xmx1024m -Dfile.encoding=UTF-8"
```

## 技术栈

- Java 17
- Java Swing (GUI)
- Maven (构建工具)

## 许可证

内部使用

