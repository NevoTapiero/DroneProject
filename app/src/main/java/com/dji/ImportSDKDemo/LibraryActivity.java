package com.dji.ImportSDKDemo;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

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
        btnCommonRust.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToCustomActivity("Corn_common_rust/");
            }
        });

        btnGrayLeafSpots.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToCustomActivity("Corn_gray_leaf_spots/");
            }
        });

        btnHealthy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToCustomActivity("Corn_healthy/");
            }
        });

        btnInfected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToCustomActivity("Corn_Infected/");
            }
        });

        btnNorthernLeafBlight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToCustomActivity("Corn_northern_leaf_blight/");
            }
        });

        btnUnclassified.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToCustomActivity("unclassified/");
            }
        });
    }

    private void navigateToCustomActivity(String category) {
        Intent intent = new Intent(this, CustomActivity.class);
        intent.putExtra("category", category);
        startActivity(intent);
    }
}
