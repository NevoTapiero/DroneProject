package com.dji.ImportSDKDemo.DroneMedia;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import dji.sdk.media.MediaFile;

class MyDiffCallback extends DiffUtil.Callback {
    List<MediaFile> oldFiles;
    List<MediaFile> newFiles;

    MyDiffCallback(List<MediaFile> oldFiles, List<MediaFile> newFiles) {
        this.oldFiles = oldFiles;
        this.newFiles = newFiles;
    }

    @Override
    public int getOldListSize() {
        return oldFiles.size();
    }

    @Override
    public int getNewListSize() {
        return newFiles.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        //determine if two items are the same
        return oldFiles.get(oldItemPosition).getFileName().equals(newFiles.get(newItemPosition).getFileName());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        //check if the content of two items are the same
        MediaFile oldFile = oldFiles.get(oldItemPosition);
        MediaFile newFile = newFiles.get(newItemPosition);
        return oldFile.getFileName().equals(newFile.getFileName()) && oldFile.getFileSize() == newFile.getFileSize();
        // You can extend this to check more fields
    }
}
