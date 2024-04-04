package com.dji.ImportSDKDemo.Library;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.dji.ImportSDKDemo.R;

public class LibraryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        // Find the buttons for each category
        Button btnCommonRust = findViewById(R.id.btn_common_rust);
        Button btnHealthy = findViewById(R.id.btn_healthy);
        Button btnInfected = findViewById(R.id.btn_infected);
        Button btnNorthernLeafBlight = findViewById(R.id.btn_northern_leaf_blight);
        Button btnGrayLeafSpots = findViewById(R.id.btn_gray_leaf_spots);
        Button btnUnclassified = findViewById(R.id.btn_unclassified);

        // Set click listeners for each button
        btnCommonRust.setOnClickListener(v -> navigateToCustomActivity("Corn_common_rust/"));

        btnGrayLeafSpots.setOnClickListener(v -> navigateToCustomActivity("Corn_gray_leaf_spots/"));

        btnHealthy.setOnClickListener(v -> navigateToCustomActivity("Corn_healthy/"));

        btnInfected.setOnClickListener(v -> navigateToCustomActivity("Corn_Infected/"));

        btnNorthernLeafBlight.setOnClickListener(v -> navigateToCustomActivity("Corn_northern_leaf_blight/"));

        btnUnclassified.setOnClickListener(v -> navigateToCustomActivity("unclassified/"));
    }

    private void navigateToCustomActivity(String category) {
        Intent intent = new Intent(this, CustomActivity.class);
        intent.putExtra("category", category);
        startActivity(intent);
    }
}