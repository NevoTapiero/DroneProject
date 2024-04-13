package com.dji.ImportSDKDemo.HistoryLog;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.ImportSDKDemo.R;

import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<LogEntry> logEntries;
    private final OnItemClickListener listener;

    public LogAdapter(List<LogEntry> logEntries, OnItemClickListener listener) {
        this.logEntries = logEntries;
        this.listener = listener;
    }

    // Method to get the current dataset
    public List<LogEntry> getLogEntries() {
        return logEntries;
    }

    /**
     * reset the log entries and notify the adapter
     */
    @SuppressLint("NotifyDataSetChanged")
    public void resetEntries() {
        // Add the new entry to the beginning of the list
        this.logEntries.clear();

        // Notify the adapter of the change
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_item, parent, false);
        return new LogViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogEntry logEntry = logEntries.get(position);
        holder.batchNameTextView.setText(logEntry.getBatchName());
        holder.messageTextView.setText(logEntry.getMessage());
        holder.itemView.setOnClickListener(v -> listener.onItemClick(logEntry));
    }

    @Override
    public int getItemCount() {
        return logEntries.size();
    }

    public static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView batchNameTextView, messageTextView;

        LogViewHolder(View itemView) {
            super(itemView);
            batchNameTextView = itemView.findViewById(R.id.timestampTextView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
        }
    }

    public void updateData(List<LogEntry> newLogEntries) {
        for (LogEntry entry : newLogEntries) {
            if (!logEntries.contains(entry)) {
                // Check if the list size has reached the limit
                if (logEntries.size() >= 20) {
                    // Remove the first (oldest) entry to maintain list size
                    logEntries.remove(0);
                    // Notify the adapter that an item has been removed from the beginning
                    notifyItemRemoved(0);
                }
                // Add new entry at the end of the list
                logEntries.add(entry);
                // Notify the adapter that an item is inserted at the new last position
                notifyItemInserted(logEntries.size() - 1);
            }
        }
    }

    public interface OnItemClickListener {
        void onItemClick(LogEntry item);
    }
}


