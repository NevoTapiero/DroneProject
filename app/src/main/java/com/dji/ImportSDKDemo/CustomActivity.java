package com.dji.ImportSDKDemo;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class CustomActivity extends AppCompatActivity {
    private static final String TAG = "MyApp"; // Define a tag at the top of your class

    private LibraryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom);

        // Get the selected category name from the intent
        String categoryName = getIntent().getStringExtra("category");

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        adapter = new LibraryAdapter(this);

        // Set the adapter for the RecyclerView
        recyclerView.setAdapter(adapter);

        // Set the layout manager (e.g., GridLayoutManager)
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        // Load images only from the selected category
        loadImagesFromFirestore(categoryName);
    }


    private void loadImagesFromFirestore(String categoryName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(categoryName).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<LibraryAdapter.ImageData> imageDataList = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> docData = document.getData();
                            String imageUrl = docData.get("imageUrl").toString();
                            Log.d(TAG, "Image URL: " + imageUrl); //log the URL
                            Date timestamp = docData.get("timestamp") instanceof Date ? (Date) docData.get("timestamp") : new Date(); // Check and convert timestamp
                            double latitude = docData.get("latitude") instanceof Number ? ((Number) docData.get("latitude")).doubleValue() : 0.0; // Check and convert latitude
                            double longitude = docData.get("longitude") instanceof Number ? ((Number) docData.get("longitude")).doubleValue() : 0.0; // Check and convert longitude

                            imageDataList.add(new LibraryAdapter.ImageData(imageUrl, timestamp, latitude, longitude));
                        }
                        updateImageList(imageDataList);
                    } else {
                        Log.d(TAG, "Error getting documents: ", task.getException());
                    }
                });
    }

    private void updateImageList(List<LibraryAdapter.ImageData> imageDataList) {
        adapter.updateData(imageDataList);
    }
}
