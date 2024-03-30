package com.dji.ImportSDKDemo;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ImageCountAdapter extends RecyclerView.Adapter<ImageCountAdapter.ImageCountViewHolder> {

    private final List<String> imageCounts;

    public ImageCountAdapter(List<String> imageCounts) {
        this.imageCounts = imageCounts;
    }

    @NonNull
    @Override
    public  ImageCountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ImageCountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageCountViewHolder holder, int position) {
        holder.tvImageCount.setText(imageCounts.get(position));
    }

    @Override
    public int getItemCount() {
        return imageCounts.size();
    }

    public static class ImageCountViewHolder extends RecyclerView.ViewHolder {
        TextView tvImageCount;

        public ImageCountViewHolder(@NonNull View itemView) {
            super(itemView);
            tvImageCount = itemView.findViewById(android.R.id.text1);
        }
    }
}
