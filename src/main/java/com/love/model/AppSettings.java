package com.love.model;

public class AppSettings {
    private String logBasePath; // 日志基础路径

    public AppSettings() {
        // 默认日志路径为程序运行目录下的logs
        this.logBasePath = System.getProperty("user.dir") + "/logs";
    }

    public AppSettings(String logBasePath) {
        this.logBasePath = logBasePath;
    }

    public String getLogBasePath() {
        return logBasePath;
    }

    public void setLogBasePath(String logBasePath) {
        this.logBasePath = logBasePath;
    }
}

