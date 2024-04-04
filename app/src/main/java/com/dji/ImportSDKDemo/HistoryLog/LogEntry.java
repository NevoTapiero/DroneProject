package com.dji.ImportSDKDemo.HistoryLog;

public class LogEntry {
    private final String timestamp;
    private final String message;

    public LogEntry(String timestamp, String message) {
        this.timestamp = timestamp;
        this.message = message;
    }


    public String getTimestamp() {
        return timestamp;
    }



    public String getMessage() {
        return message;
    }
}
