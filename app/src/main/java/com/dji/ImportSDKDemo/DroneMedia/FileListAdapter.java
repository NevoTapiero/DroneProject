package com.dji.ImportSDKDemo.DroneMedia;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.ImportSDKDemo.R;

import java.util.List;

import dji.sdk.media.MediaFile;

public class FileListAdapter extends RecyclerView.Adapter<MediaFileViewHolder> {
    private final List<MediaFile> mediaFiles;

    public FileListAdapter(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles;
    }

    @NonNull
    @Override
    public MediaFileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.media_info_item, parent, false);
        return new MediaFileViewHolder(itemView);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(MediaFileViewHolder holder, int position) {
        MediaFile mediaFile = mediaFiles.get(position);
        holder.fileNameTextView.setText(mediaFile.getFileName());
        holder.fileTypeTextView.setText(mediaFile.getMediaType().name());
        holder.fileSizeTextView.setText(mediaFile.getFileSize() + " Bytes");
        mediaFile.fetchThumbnail(error -> new Handler(Looper.getMainLooper()).post(() -> {
            if (error == null) {
                // Thumbnail fetch succeeded
                Bitmap thumbnail = mediaFile.getThumbnail();
                if (thumbnail != null) {
                    holder.fileThumbnailImageView.setImageBitmap(thumbnail);
                } else {
                    // Handle the case where the thumbnail is null
                    holder.fileThumbnailImageView.setImageResource(R.drawable.error_image);
                }
            } else {
                // Thumbnail fetch failed
                holder.fileThumbnailImageView.setImageResource(R.drawable.error_image);
            }
        }));

    }

    @Override
    public int getItemCount() {
        return mediaFiles.size();
    }

    // Method to update the list of media files
    public void updateFileList(List<MediaFile> newMediaFiles) {
        // Calculate the difference between the old list and the new list
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new MyDiffCallback(this.mediaFiles, newMediaFiles));

        // Clear the existing data and add all the new items
        this.mediaFiles.clear();
        this.mediaFiles.addAll(newMediaFiles);

        // Notify the adapter of the changes calculated by DiffUtil
        diffResult.dispatchUpdatesTo(this);
    }

}

