package com.dji.ImportSDKDemo.Library;

import static com.qx.wz.dj.rtcm.StringUtil.TAG;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.ImportSDKDemo.R;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {
    private final List<ImageData> imageDataList; // List to hold image data

    public GalleryAdapter() {
        this.imageDataList = new ArrayList<>(); // Initialize the list here
    }

    // Update the adapter's data with a new list of image data
    @SuppressLint("NotifyDataSetChanged")
    public void updateData(List<ImageData> newImageDataList) {
        imageDataList.clear();
        imageDataList.addAll(newImageDataList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ImageData imageData = imageDataList.get(position);

        Picasso.get()
                .load(imageData.getImageUrl())
                .placeholder(R.drawable.placeholder_image) // Optional: image shown during loading
                .error(R.drawable.error_image) // Optional: image shown on error
                .into(holder.imageView, new com.squareup.picasso.Callback() { // Use Picasso's Callback
                    @Override
                    public void onSuccess() {
                        // Image loaded successfully
                        Log.d(TAG, "Image loaded successfully for " + imageData.getImageUrl());
                    }

                    @Override
                    public void onError(Exception e) {
                        // Image load failed
                        Log.e(TAG, "Picasso load error for " + imageData.getImageUrl(), e);
                    }
                });

        // Set the timestamp and location
        holder.timestampView.setText(imageData.getTimestamp().toString());
        holder.locationView.setText("Lat: " + imageData.getLatitude() + ", Long: " + imageData.getLongitude());
    }



    @Override
    public int getItemCount() {
        return imageDataList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView timestampView; // TextView for timestamp
        TextView locationView; // TextView for location

        public ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view_id);
            timestampView = itemView.findViewById(R.id.timestamp_view_id); // Make sure you have a TextView with this ID in your layout
            locationView = itemView.findViewById(R.id.location_view_id); // Make sure you have a TextView with this ID in your layout
        }
    }

    // Define the ImageData class inside the LibraryAdapter
    public static class ImageData {
        String imageUrl;
        Date timestamp; // Assuming timestamp is stored as a Date object
        double latitude;
        double longitude;

        // Constructor
        public ImageData(String imageUrl, Date timestamp, double latitude, double longitude) {
            this.imageUrl = imageUrl;
            this.timestamp = timestamp;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        // Getters
        public String getImageUrl() { return imageUrl; }
        public Date getTimestamp() { return timestamp; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
}
