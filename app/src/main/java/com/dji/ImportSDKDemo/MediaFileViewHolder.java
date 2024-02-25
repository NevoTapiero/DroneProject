package com.dji.ImportSDKDemo;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class MediaFileViewHolder extends RecyclerView.ViewHolder {
    TextView fileNameTextView;
    TextView fileTypeTextView;
    TextView fileSizeTextView;
    ImageView fileThumbnailImageView;

    public MediaFileViewHolder(View itemView) {
        super(itemView);
        fileNameTextView = itemView.findViewById(R.id.filename);
        fileTypeTextView = itemView.findViewById(R.id.filetype);
        fileSizeTextView = itemView.findViewById(R.id.fileSize);
        fileThumbnailImageView = itemView.findViewById(R.id.filethumbnail); // If you decide to show thumbnails
    }
}
