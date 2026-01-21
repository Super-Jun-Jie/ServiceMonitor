package com.love.util;

import com.love.model.ServiceConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final String CONFIG_FILE = "services.txt";
    private static final String SEPARATOR = "|||";

    public static void saveConfigs(List<ServiceConfig> configs) throws IOException {
        if (configs == null) {
            throw new IllegalArgumentException("配置列表不能为null");
        }
        
        // 先写入临时文件，然后重命名，确保原子性
        File tempFile = new File(CONFIG_FILE + ".tmp");
        File configFile = new File(CONFIG_FILE);
        
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
            for (ServiceConfig config : configs) {
                if (config == null) {
                    continue; // 跳过null配置
                }
                // 保存格式：name|||javaExe|||workDir|||args
                // 不再保存日志路径，日志路径由设置统一管理
                writer.write(escape(config.getName()) + SEPARATOR);
                writer.write(escape(config.getJavaExe()) + SEPARATOR);
                writer.write(escape(config.getWorkDir()) + SEPARATOR);
                writer.write(escape(config.getArgs()));
                writer.newLine();
            }
            writer.flush();
        }
        
        // 原子性替换：先删除旧文件，再重命名临时文件
        if (configFile.exists() && !configFile.delete()) {
            throw new IOException("无法删除旧配置文件");
        }
        if (!tempFile.renameTo(configFile)) {
            throw new IOException("无法重命名临时配置文件");
        }
    }

    public static List<ServiceConfig> loadConfigs() {
        List<ServiceConfig> configs = new ArrayList<>();
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            return configs;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    String[] parts = line.split("\\|\\|\\|", -1);
                    // 格式：name|||javaExe|||workDir|||args（4个字段）
                    if (parts.length == 4) {
                        ServiceConfig config = new ServiceConfig();
                        config.setName(unescape(parts[0]));
                        config.setJavaExe(unescape(parts[1]));
                        config.setWorkDir(unescape(parts[2]));
                        config.setArgs(unescape(parts[3]));
                        
                        // 验证配置有效性
                        String name = config.getName();
                        if (name == null || name.trim().isEmpty()) {
                            System.err.println("警告: 第 " + lineNumber + " 行配置的服务名称为空，已跳过");
                            continue;
                        }
                        
                        configs.add(config);
                    } else {
                        System.err.println("警告: 第 " + lineNumber + " 行配置格式不正确（需要4个字段，用|||分隔），已跳过。");
                        System.err.println("      正确格式: 服务名称|||Java路径|||工作目录|||启动参数");
                        System.err.println("      当前字段数: " + parts.length);
                        if (parts.length > 0) {
                            System.err.println("      服务名称: " + (parts[0].length() > 50 ? parts[0].substring(0, 50) + "..." : parts[0]));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("警告: 解析第 " + lineNumber + " 行配置时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("加载配置失败: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("加载配置时发生未知错误: " + e.getMessage());
            e.printStackTrace();
        }
        return configs;
    }

    private static String escape(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace(SEPARATOR, "\\|\\|\\|");
    }

    private static String unescape(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        return str.replace("\\|\\|\\|", SEPARATOR)
                  .replace("\\r", "\r")
                  .replace("\\n", "\n")
                  .replace("\\\\", "\\");
    }
}

