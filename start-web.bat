@echo off
echo 启动服务监控器 Web端...
echo.
java -cp "target/ServiceMonitor-1.0-SNAPSHOT.jar;target/lib/*" com.love.web.ServiceMonitorWebApplication
pause

