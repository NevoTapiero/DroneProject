package com.dji.ImportSDKDemo.HistoryLog;

public class LogEntry {
    private final StringBuilder batchName;
    private final String message;

    public LogEntry(StringBuilder batchName, String message) {
        this.batchName = batchName;
        this.message = message;
    }


    public StringBuilder getBatchName() {
        return batchName;
    }



    public String getMessage() {
        return message;
    }
}
