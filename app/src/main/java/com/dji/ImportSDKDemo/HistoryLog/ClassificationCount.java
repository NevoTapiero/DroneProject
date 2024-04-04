package com.dji.ImportSDKDemo.HistoryLog;

public class ClassificationCount {
    private final String classificationName;
    private final long count;

    public ClassificationCount(String classificationName, long count) {
        this.classificationName = classificationName;
        this.count = count;
    }

    public String getClassName() {
        return classificationName;
    }

    public long getCount() {
        return count;
    }
}

