package com.dji.ImportSDKDemo.HistoryLog;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LogManager {
    private static final String TAG = "LogManager";


    /**
     * initializes the JSON file with an empty logs array
     * @param context the application context
     */
    public static void initializeLogFile(Context context) {
        try {
            File logFile = getLogFile(context);
            if (!logFile.exists()) {
                // Create a new JSON object with an empty logs array
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("logs", new JSONArray());

                // Write the empty structure to the file
                try (FileWriter fileWriter = new FileWriter(logFile)) {
                    fileWriter.write(jsonObject.toString());
                }
            }
            Toast.makeText(context, logFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "An error occurred: ", e);
        }
    }


    /**
     * deletes the JSON file
     * @param context the application context
     */
    public static void deleteJSON(Context context) {
        File file = new File(getLogFile(context).getAbsolutePath());
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                System.out.println("File deleted successfully");
            } else {
                System.out.println("Failed to delete the file");
            }
        }
    }


    public static void appendLogToJsonFile(Context context, LogEntry newLogEntry) {
        File logFile = getLogFile(context);
        try {
            JSONArray logsArray;
            if (logFile.exists() && logFile.length() > 0) {
                String content = new String(Files.readAllBytes(logFile.toPath()));
                JSONObject jsonObject = new JSONObject(content);
                logsArray = jsonObject.getJSONArray("logs");
                JSONArray newLogsArray = new JSONArray();
                // Create a new log entry and insert at start
                JSONObject newLog = new JSONObject();
                newLog.put("batchName", newLogEntry.getBatchName());
                newLog.put("message", newLogEntry.getMessage());
                newLogsArray.put(newLog);
                for (int i = 0; i < logsArray.length(); i++) {
                    newLogsArray.put(logsArray.getJSONObject(i));
                }
                logsArray = newLogsArray;
            } else {
                logsArray = new JSONArray();
                JSONObject newLog = new JSONObject();
                newLog.put("batchName", newLogEntry.getBatchName());
                newLog.put("message", newLogEntry.getMessage());
                logsArray.put(newLog);
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("logs", logsArray);
            try (FileWriter fileWriter = new FileWriter(logFile)) {
                fileWriter.write(jsonObject.toString());
                Log.i(TAG, "Successfully appended log to JSON file.");
            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred while appending log to JSON file: ", e);
        }
    }


    /**
     * converts the JSON file to a list of LogEntry objects
     * @param context the application context
     * @return a list of LogEntry objects
     */
    public static List<LogEntry> readLogsFromFile(Context context) {
        List<LogEntry> logEntries = new ArrayList<>();
        File logFile = getLogFile(context);
        try {
            if (logFile.exists()) {
                // Read the existing JSON file
                String content = new String(Files.readAllBytes(logFile.toPath()));
                JSONObject jsonObject = new JSONObject(content);
                JSONArray logsArray = jsonObject.getJSONArray("logs");

                // Convert the JSONArray into a list of LogEntry objects
                for (int i = 0; i < logsArray.length(); i++) {
                    JSONObject logObject = logsArray.getJSONObject(i);
                    StringBuilder batchName = new StringBuilder(logObject.getString("batchName"));
                    String message = logObject.getString("message");
                    logEntries.add(new LogEntry(batchName, message));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred while reading logs from JSON file: ", e);
        }
        return logEntries;
    }

    /**
     * Returns the log file, creating it if it doesn't exist.
     * The log file is named "appLogs.json" and is stored in the application's internal storage.
     *
     * @param context The application context, used to access the files directory.
     * @return The log file.
     */
    private static File getLogFile(Context context) {
        // This will store the log file in the app's internal storage
        return new File(context.getFilesDir(), "appLogs.json");
    }
}
