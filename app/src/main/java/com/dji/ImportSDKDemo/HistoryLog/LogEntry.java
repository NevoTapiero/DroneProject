package com.dji.ImportSDKDemo.HistoryLog;

public class LogEntry {
    private final String batchName;
    private final String message;

    public LogEntry(String batchName, String message) {
        this.batchName = batchName;
        this.message = message;
    }



    public String getBatchName() {
        return batchName;
    }



    public String getMessage() {
        return message;
    }
}
