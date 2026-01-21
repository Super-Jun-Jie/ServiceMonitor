package com.love.util;

import com.love.model.AppSettings;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class SettingsManager {
    private static final String SETTINGS_FILE = "settings.txt";
    private static final String SEPARATOR = "|||";

    public static void saveSettings(AppSettings settings) throws IOException {
        if (settings == null) {
            throw new IllegalArgumentException("设置不能为null");
        }
        
        // 先写入临时文件，然后重命名，确保原子性
        File tempFile = new File(SETTINGS_FILE + ".tmp");
        File settingsFile = new File(SETTINGS_FILE);
        
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
            writer.write(escape(settings.getLogBasePath()));
            writer.newLine();
            writer.flush();
        }
        
        // 原子性替换
        if (settingsFile.exists() && !settingsFile.delete()) {
            throw new IOException("无法删除旧设置文件");
        }
        if (!tempFile.renameTo(settingsFile)) {
            throw new IOException("无法重命名临时设置文件");
        }
    }

    public static AppSettings loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) {
            // 返回默认设置
            return new AppSettings();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                String logBasePath = unescape(line.trim());
                return new AppSettings(logBasePath);
            }
        } catch (IOException e) {
            System.err.println("加载设置失败: " + e.getMessage());
        }
        
        // 返回默认设置
        return new AppSettings();
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

