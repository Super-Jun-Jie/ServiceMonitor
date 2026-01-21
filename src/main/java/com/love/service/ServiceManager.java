package com.love.service;

import com.love.model.AppSettings;
import com.love.model.ServiceConfig;
import com.love.util.ConfigManager;
import com.love.util.SettingsManager;
import com.love.util.SimpleProcessWatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 服务管理器 - 统一管理服务监控逻辑（供桌面端和Web端共享）
 */
public class ServiceManager {
    private static ServiceManager instance;
    
    private List<ServiceConfig> configs;
    private Map<Integer, SimpleProcessWatcher> watchers;
    private Map<Integer, ScheduledExecutorService> statusUpdateServices;
    private AppSettings appSettings;
    
    private ServiceManager() {
        configs = new ArrayList<>();
        watchers = new HashMap<>();
        statusUpdateServices = new HashMap<>();
        appSettings = SettingsManager.loadSettings();
        loadConfigs();
    }
    
    public static synchronized ServiceManager getInstance() {
        if (instance == null) {
            instance = new ServiceManager();
        }
        return instance;
    }
    
    public List<ServiceConfig> getConfigs() {
        return new ArrayList<>(configs);
    }
    
    public ServiceConfig getConfig(int index) {
        if (index >= 0 && index < configs.size()) {
            return configs.get(index);
        }
        return null;
    }
    
    public void addConfig(ServiceConfig config) throws Exception {
        configs.add(config);
        saveConfigs();
    }
    
    public void updateConfig(int index, ServiceConfig config) throws Exception {
        if (index >= 0 && index < configs.size()) {
            configs.set(index, config);
            saveConfigs();
        }
    }
    
    public void deleteConfig(int index) throws Exception {
        if (index >= 0 && index < configs.size()) {
            stopService(index);
            configs.remove(index);
            saveConfigs();
        }
    }
    
    public void startService(int index) throws Exception {
        if (index < 0 || index >= configs.size()) {
            throw new IllegalArgumentException("无效的服务索引: " + index);
        }
        
        ServiceConfig config = configs.get(index);
        if (watchers.containsKey(index) && watchers.get(index).isRunning()) {
            throw new IllegalStateException("服务 " + config.getName() + " 已在运行中");
        }
        
        String[] args = config.getArgsArray();
        
        // 自动生成日志路径
        String logBasePath = appSettings.getLogBasePath();
        File logDir = new File(logBasePath, config.getName());
        logDir.mkdirs();
        
        File outLog = new File(logDir, "output.log");
        File errLog = new File(logDir, "error.log");
        
        SimpleProcessWatcher watcher = new SimpleProcessWatcher(
            config.getJavaExe(),
            config.getWorkDir(),
            args,
            outLog,
            errLog
        );
        watcher.setLogCallback(msg -> System.out.println("[" + config.getName() + "] " + msg));
        
        // start() 方法现在会等待并确认进程真正启动成功
        // 如果进程在3秒内退出（如端口占用），会抛出IOException
        watcher.start();
        
        watchers.put(index, watcher);
        startStatusUpdate(index);
    }
    
    public void stopService(int index) {
        if (index < 0 || index >= configs.size()) {
            return;
        }
        
        SimpleProcessWatcher watcher = watchers.remove(index);
        if (watcher != null) {
            watcher.stop();
        }
        
        ScheduledExecutorService service = statusUpdateServices.remove(index);
        if (service != null) {
            service.shutdown();
        }
    }
    
    public void restartService(int index) throws Exception {
        stopService(index);
        Thread.sleep(1000);
        startService(index);
    }
    
    public ServiceStatus getServiceStatus(int index) {
        if (index < 0 || index >= configs.size()) {
            return null;
        }
        
        ServiceConfig config = configs.get(index);
        SimpleProcessWatcher watcher = watchers.get(index);
        
        ServiceStatus status = new ServiceStatus();
        status.setName(config.getName());
        status.setIndex(index);
        
        if (watcher != null) {
            if (watcher.isProcessAlive()) {
                status.setStatus("运行中");
                status.setPid(watcher.getProcessId());
            } else if (watcher.isRunning()) {
                status.setStatus("进程已退出");
                status.setPid(-1);
            } else {
                status.setStatus("已停止");
                status.setPid(-1);
            }
        } else {
            status.setStatus("未启动");
            status.setPid(-1);
        }
        
        return status;
    }
    
    public List<ServiceStatus> getAllServiceStatus() {
        List<ServiceStatus> statusList = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            statusList.add(getServiceStatus(i));
        }
        return statusList;
    }
    
    public AppSettings getAppSettings() {
        return appSettings;
    }
    
    public void updateAppSettings(AppSettings settings) throws Exception {
        this.appSettings = settings;
        SettingsManager.saveSettings(settings);
    }
    
    private void loadConfigs() {
        configs = ConfigManager.loadConfigs();
    }
    
    private void saveConfigs() throws Exception {
        ConfigManager.saveConfigs(configs);
    }
    
    private void startStatusUpdate(int index) {
        ScheduledExecutorService oldService = statusUpdateServices.remove(index);
        if (oldService != null) {
            oldService.shutdown();
        }
        
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StatusUpdate-" + index);
            t.setDaemon(true);
            return t;
        });
        statusUpdateServices.put(index, service);
    }
    
    /**
     * 服务状态DTO
     */
    public static class ServiceStatus {
        private String name;
        private int index;
        private String status;
        private long pid;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getPid() { return pid; }
        public void setPid(long pid) { this.pid = pid; }
    }
}

