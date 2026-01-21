package com.love.util;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.function.Consumer;

public class SimpleProcessWatcher {

    private String javaExe;
    private String workDir;
    private String[] args;
    private File outLog;
    private File errLog;
    
    private volatile Process process; // 使用volatile确保可见性
    private volatile Consumer<String> logCallback;
    private volatile boolean running = false; // 使用volatile确保可见性
    private Thread monitorThread;
    private volatile long lastStartTime = 0; // 上次启动时间
    private volatile int consecutiveFailures = 0; // 连续失败次数
    private static final long MIN_RESTART_INTERVAL = 10000; // 最小重启间隔10秒
    private static final int MAX_CONSECUTIVE_FAILURES = 5; // 最大连续失败次数
    private final Object processLock = new Object(); // 进程操作的锁

    public SimpleProcessWatcher(String javaExe, String workDir, String[] args, 
                                File outLog, File errLog) {
        this.javaExe = javaExe;
        this.workDir = workDir;
        this.args = args;
        this.outLog = outLog;
        this.errLog = errLog;
    }

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    public void start() throws IOException {
        synchronized (processLock) {
            if (running) {
                log("监控已在运行中");
                return;
            }
            running = true;
        }
        
        try {
            startProcess();
            long pid = process.pid();
            
            // 等待并确认进程真的启动成功了
            // 等待5秒，然后进行多重检查
            Thread.sleep(5000);
            
            // 检查1：使用Process.isAlive()检查
            if (!isAlive()) {
                String errorInfo = readRecentErrorLog();
                synchronized (processLock) {
                    running = false;
                }
                String errorMsg = "进程启动后退出，可能原因：端口被占用、配置错误或程序异常。";
                if (!errorInfo.isEmpty()) {
                    errorMsg += "\n错误信息: " + errorInfo;
                }
                errorMsg += "\n请检查日志文件: " + (errLog != null ? errLog.getAbsolutePath() : "未知");
                throw new IOException(errorMsg);
            }
            
            // 检查2：使用系统命令验证PID是否真实存在
            if (!verifyProcessExists(pid)) {
                String errorInfo = readRecentErrorLog();
                synchronized (processLock) {
                    running = false;
                }
                String errorMsg = "进程PID " + pid + " 不存在，启动失败。可能原因：进程已退出或被终止。";
                if (!errorInfo.isEmpty()) {
                    errorMsg += "\n错误信息: " + errorInfo;
                }
                errorMsg += "\n请检查日志文件: " + (errLog != null ? errLog.getAbsolutePath() : "未知");
                throw new IOException(errorMsg);
            }
            
            // 检查3：检查错误日志，看是否有端口占用等严重错误
            String errorInfo = readRecentErrorLog();
            if (!errorInfo.isEmpty() && (errorInfo.contains("端口") || errorInfo.contains("port") || 
                errorInfo.contains("Address already in use") || errorInfo.contains("BindException") ||
                errorInfo.contains("bind") || errorInfo.contains("BindException"))) {
                // 发现端口占用等严重错误，即使进程还在运行也认为启动失败
                synchronized (processLock) {
                    running = false;
                    if (process != null && process.isAlive()) {
                        process.destroy();
                    }
                }
                throw new IOException("检测到端口被占用或绑定失败: " + errorInfo);
            }
            
            log("进程启动成功并运行正常，PID = " + pid + "（已通过系统验证）");
        } catch (IOException e) {
            // 启动失败，重置状态
            synchronized (processLock) {
                running = false;
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (processLock) {
                running = false;
            }
            throw new IOException("启动确认过程被中断", e);
        }
        
        monitorThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(5000);
                    if (!running) {
                        break;
                    }
                    if (!isAlive()) {
                        long currentTime = System.currentTimeMillis();
                        long timeSinceLastStart = currentTime - lastStartTime;
                        
                        // 如果进程启动后很快退出（小于10秒），说明可能有问题
                        if (timeSinceLastStart < MIN_RESTART_INTERVAL && lastStartTime > 0) {
                            consecutiveFailures++;
                            log("进程启动后快速退出（" + timeSinceLastStart + "ms），连续失败次数: " + consecutiveFailures);
                            
                            // 如果连续失败次数过多，停止自动重启
                            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                                log("连续失败次数过多（" + consecutiveFailures + "次），停止自动重启。请检查服务配置。");
                                running = false; // 停止监控
                                break;
                            }
                            
                            // 增加重启延迟，避免频繁重启
                            long delay = Math.min(MIN_RESTART_INTERVAL - timeSinceLastStart, MIN_RESTART_INTERVAL);
                            log("等待 " + (delay / 1000) + " 秒后重启...");
                            Thread.sleep(delay);
                        } else {
                            // 正常退出，重置失败计数
                            consecutiveFailures = 0;
                            log("进程已退出，准备重启...");
                        }
                        
                        if (running) {
                            try {
                                startProcess();
                                // 启动后等待2秒，确认进程真的运行起来了
                                Thread.sleep(2000);
                                if (!isAlive()) {
                                    consecutiveFailures++;
                                    log("进程启动后立即退出，可能配置有误");
                                } else {
                                    // 进程正常运行，重置失败计数
                                    if (consecutiveFailures > 0) {
                                        consecutiveFailures = 0;
                                        log("进程已正常运行");
                                    }
                                }
                            } catch (IOException e) {
                                consecutiveFailures++;
                                log("重启失败: " + e.getMessage());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } else {
                        // 进程正常运行，重置失败计数
                        if (consecutiveFailures > 0) {
                            consecutiveFailures = 0;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public void stop() {
        synchronized (processLock) {
            running = false;
            consecutiveFailures = 0; // 重置失败计数
        }
        
        if (monitorThread != null) {
            monitorThread.interrupt();
            // 等待监控线程结束（最多等待2秒）
            try {
                monitorThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        Process processToStop;
        synchronized (processLock) {
            processToStop = process;
            process = null; // 清空引用，避免重复停止
        }
        
        if (processToStop != null && processToStop.isAlive()) {
            long pid = processToStop.pid();
            try {
                // 先尝试正常关闭
                processToStop.destroy();
                log("正在停止进程 PID=" + pid);
                
                // 等待进程退出，最多等待3秒
                boolean terminated = processToStop.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                
                if (!terminated) {
                    // 如果还没退出，强制终止
                    log("进程未正常退出，强制终止 PID=" + pid);
                    processToStop.destroyForcibly();
                    
                    // 再等待1秒
                    terminated = processToStop.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
                    
                    if (!terminated) {
                        // 如果还是没退出，尝试使用系统命令强制终止（Windows）
                        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                            try {
                                Process killProcess = new ProcessBuilder(
                                    "taskkill", "/F", "/T", "/PID", String.valueOf(pid)
                                ).start();
                                killProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                                // 清理kill进程的资源
                                try {
                                    killProcess.destroyForcibly();
                                } catch (Exception ignored) {
                                }
                                log("已使用 taskkill 强制终止进程树 PID=" + pid);
                            } catch (Exception e) {
                                log("使用 taskkill 终止失败: " + e.getMessage());
                            }
                        }
                    }
                }
                
                log("进程已停止 PID=" + pid);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 强制终止
                try {
                    if (processToStop.isAlive()) {
                        processToStop.destroyForcibly();
                    }
                } catch (Exception ignored) {
                }
                log("进程强制终止 PID=" + pid);
            } catch (Exception e) {
                log("停止进程时发生异常: " + e.getMessage());
            } finally {
                // 确保资源清理
                try {
                    if (processToStop != null) {
                        processToStop.destroyForcibly();
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isProcessAlive() {
        // 使用 isAlive() 方法检查进程是否存活
        // 这个方法会实时检查进程状态，即使进程被外部杀死也能检测到
        Process currentProcess;
        synchronized (processLock) {
            currentProcess = process;
        }
        return currentProcess != null && currentProcess.isAlive();
    }

    public long getProcessId() {
        Process currentProcess;
        synchronized (processLock) {
            currentProcess = process;
        }
        return currentProcess != null ? currentProcess.pid() : -1;
    }

    private void startProcess() throws IOException {
        // 验证配置
        if (javaExe == null || javaExe.trim().isEmpty()) {
            throw new IOException("Java路径不能为空");
        }
        File javaFile = new File(javaExe);
        if (!javaFile.exists() || !javaFile.isFile()) {
            throw new IOException("Java可执行文件不存在: " + javaExe);
        }
        
        File workDirFile = new File(workDir);
        if (!workDirFile.exists() || !workDirFile.isDirectory()) {
            throw new IOException("工作目录不存在: " + workDir);
        }
        
        // 确保日志文件目录存在
        if (outLog != null && outLog.getParentFile() != null) {
            outLog.getParentFile().mkdirs();
        }
        if (errLog != null && errLog.getParentFile() != null) {
            errLog.getParentFile().mkdirs();
        }
        
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(buildCommand());
        pb.directory(workDirFile);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outLog));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(errLog));

        Process newProcess;
        try {
            newProcess = pb.start();
        } catch (IOException e) {
            log("启动进程失败: " + e.getMessage());
            throw e;
        }
        
        synchronized (processLock) {
            // 如果之前有进程，先清理
            if (process != null && process.isAlive()) {
                try {
                    process.destroyForcibly();
                } catch (Exception e) {
                    // 忽略清理异常
                }
            }
            process = newProcess;
            lastStartTime = System.currentTimeMillis();
        }
        
        log("启动成功，PID = " + newProcess.pid());
    }

    private boolean isAlive() {
        Process currentProcess;
        synchronized (processLock) {
            currentProcess = process;
        }
        try {
            return currentProcess != null && currentProcess.isAlive();
        } catch (Exception e) {
            // 检查进程状态时可能抛出异常，返回false
            return false;
        }
    }

    private String[] buildCommand() {
        String[] cmd = new String[args.length + 1];
        cmd[0] = javaExe;
        System.arraycopy(args, 0, cmd, 1, args.length);
        return cmd;
    }

    private void log(String msg) {
        if (msg == null) {
            return;
        }
        try {
            String logMsg = LocalDateTime.now() + " | " + msg;
            System.out.println(logMsg);
            Consumer<String> callback = logCallback;
            if (callback != null) {
                try {
                    callback.accept(logMsg);
                } catch (Exception e) {
                    // 防止回调异常影响主流程
                    System.err.println("日志回调异常: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // 防止日志记录异常
            System.err.println("记录日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 读取最近的错误日志（最后几行），用于判断启动是否成功
     */
    private String readRecentErrorLog() {
        if (errLog == null || !errLog.exists()) {
            return "";
        }
        
        try {
            // 读取文件的最后2KB内容
            long fileLength = errLog.length();
            long readLength = Math.min(2048, fileLength);
            
            if (readLength <= 0) {
                return "";
            }
            
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(errLog, "r")) {
                raf.seek(Math.max(0, fileLength - readLength));
                byte[] buffer = new byte[(int) readLength];
                raf.readFully(buffer);
                String content = new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
                
                // 只返回最后10行，避免信息过多
                String[] lines = content.split("\n");
                int startLine = Math.max(0, lines.length - 10);
                StringBuilder result = new StringBuilder();
                for (int i = startLine; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (!line.isEmpty()) {
                        if (result.length() > 0) {
                            result.append(" ");
                        }
                        result.append(line);
                    }
                }
                return result.toString();
            }
        } catch (Exception e) {
            // 读取日志失败不影响启动流程
            return "";
        }
    }
    
    /**
     * 使用系统命令验证进程是否真实存在
     * @param pid 进程ID
     * @return true表示进程存在，false表示不存在
     */
    private boolean verifyProcessExists(long pid) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process checkProcess;
            
            if (os.contains("windows")) {
                // Windows: 使用 tasklist 命令
                checkProcess = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid).start();
            } else {
                // Linux/Unix: 使用 ps 命令
                checkProcess = new ProcessBuilder("ps", "-p", String.valueOf(pid)).start();
            }
            
            // 等待命令执行完成
            int exitCode = checkProcess.waitFor();
            
            if (os.contains("windows")) {
                // Windows: tasklist 找到进程返回0，未找到返回非0
                // 还需要检查输出中是否包含PID
                if (exitCode == 0) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(checkProcess.getInputStream(), 
                            java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains(String.valueOf(pid))) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            } else {
                // Linux/Unix: ps 找到进程返回0，未找到返回非0
                return exitCode == 0;
            }
        } catch (Exception e) {
            // 如果系统命令执行失败，回退到使用 isAlive() 方法
            log("无法使用系统命令验证进程，回退到进程对象检查: " + e.getMessage());
            return isAlive();
        }
    }
}

