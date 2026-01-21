package com.love.model;

public class ServiceConfig {
    private String name;
    private String javaExe;
    private String workDir;
    private String args;  // 用换行符分隔的参数
    private String outLog;
    private String errLog;

    public ServiceConfig() {
    }

    public ServiceConfig(String name, String javaExe, String workDir, String args, String outLog, String errLog) {
        this.name = name;
        this.javaExe = javaExe;
        this.workDir = workDir;
        this.args = args;
        this.outLog = outLog;
        this.errLog = errLog;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJavaExe() {
        return javaExe;
    }

    public void setJavaExe(String javaExe) {
        this.javaExe = javaExe;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public String getOutLog() {
        return outLog;
    }

    public void setOutLog(String outLog) {
        this.outLog = outLog;
    }

    public String getErrLog() {
        return errLog;
    }

    public void setErrLog(String errLog) {
        this.errLog = errLog;
    }

    public String[] getArgsArray() {
        return args != null ? args.split("\\n") : new String[0];
    }
}

