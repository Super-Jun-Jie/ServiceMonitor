package com.love.web;

import com.love.model.AppSettings;
import com.love.model.ServiceConfig;
import com.love.service.ServiceManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API 控制器
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ServiceMonitorController {
    
    private final ServiceManager serviceManager = ServiceManager.getInstance();
    
    /**
     * 获取所有服务状态
     */
    @GetMapping("/services")
    public ResponseEntity<List<ServiceManager.ServiceStatus>> getServices() {
        return ResponseEntity.ok(serviceManager.getAllServiceStatus());
    }
    
    /**
     * 获取服务详情
     */
    @GetMapping("/services/{index}")
    public ResponseEntity<?> getService(@PathVariable int index) {
        ServiceConfig config = serviceManager.getConfig(index);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        
        ServiceManager.ServiceStatus status = serviceManager.getServiceStatus(index);
        Map<String, Object> result = new HashMap<>();
        result.put("config", config);
        result.put("status", status);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 添加服务
     */
    @PostMapping("/services")
    public ResponseEntity<?> addService(@RequestBody ServiceConfig config) {
        try {
            serviceManager.addConfig(config);
            return ResponseEntity.ok(Map.of("success", true, "message", "服务已添加"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 更新服务配置
     */
    @PutMapping("/services/{index}")
    public ResponseEntity<?> updateService(@PathVariable int index, @RequestBody ServiceConfig config) {
        try {
            serviceManager.updateConfig(index, config);
            return ResponseEntity.ok(Map.of("success", true, "message", "服务配置已更新"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 删除服务
     */
    @DeleteMapping("/services/{index}")
    public ResponseEntity<?> deleteService(@PathVariable int index) {
        try {
            serviceManager.deleteConfig(index);
            return ResponseEntity.ok(Map.of("success", true, "message", "服务已删除"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 启动服务
     */
    @PostMapping("/services/{index}/start")
    public ResponseEntity<?> startService(@PathVariable int index) {
        try {
            serviceManager.startService(index);
            return ResponseEntity.ok(Map.of("success", true, "message", "服务已启动"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 停止服务
     */
    @PostMapping("/services/{index}/stop")
    public ResponseEntity<?> stopService(@PathVariable int index) {
        try {
            serviceManager.stopService(index);
            return ResponseEntity.ok(Map.of("success", true, "message", "服务已停止"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 重启服务
     */
    @PostMapping("/services/{index}/restart")
    public ResponseEntity<?> restartService(@PathVariable int index) {
        try {
            serviceManager.restartService(index);
            return ResponseEntity.ok(Map.of("success", true, "message", "服务已重启"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 一键启动所有服务
     */
    @PostMapping("/services/start-all")
    public ResponseEntity<?> startAllServices() {
        try {
            List<ServiceConfig> configs = serviceManager.getConfigs();
            int successCount = 0;
            int failCount = 0;
            
            for (int i = 0; i < configs.size(); i++) {
                try {
                    serviceManager.startService(i);
                    successCount++;
                    Thread.sleep(200); // 间隔200ms
                } catch (Exception e) {
                    failCount++;
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已启动 " + successCount + " 个服务，失败 " + failCount + " 个"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 一键停止所有服务
     */
    @PostMapping("/services/stop-all")
    public ResponseEntity<?> stopAllServices() {
        try {
            List<ServiceConfig> configs = serviceManager.getConfigs();
            for (int i = 0; i < configs.size(); i++) {
                serviceManager.stopService(i);
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "所有服务已停止"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 获取应用设置
     */
    @GetMapping("/settings")
    public ResponseEntity<AppSettings> getSettings() {
        return ResponseEntity.ok(serviceManager.getAppSettings());
    }
    
    /**
     * 更新应用设置
     */
    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody AppSettings settings) {
        try {
            serviceManager.updateAppSettings(settings);
            return ResponseEntity.ok(Map.of("success", true, "message", "设置已保存"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

