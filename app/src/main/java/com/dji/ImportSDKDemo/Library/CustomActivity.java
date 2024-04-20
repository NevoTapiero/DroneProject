package com.dji.ImportSDKDemo.Library;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.ImportSDKDemo.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CustomActivity extends AppCompatActivity {
    private static final String TAG = "MyApp"; // Define a tag at the top of your class
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseUser user = mAuth.getCurrentUser();
    private GalleryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom);

        // Get the selected category name and the batch ID from the intent
        String categoryName = getIntent().getStringExtra("category");
        String batchID = getIntent().getStringExtra("batchID");

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        adapter = new GalleryAdapter();

        // Set the adapter for the RecyclerView
        recyclerView.setAdapter(adapter);

        // Set the layout manager (e.g., GridLayoutManager)
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        // Load images only from the selected category
        loadImagesFromFirestore(categoryName, batchID);
    }


    private void loadImagesFromFirestore(String categoryName, String batchID) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users")
                .document(user.getUid())
                .collection(categoryName)
                .document(batchID)
                .collection("images")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<GalleryAdapter.ImageData> imageDataList = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> docData = document.getData();
                            String imageUrl = Objects.requireNonNull(docData.get("imageUrl")).toString();
                            Log.d(TAG, "Image URL: " + imageUrl); //log the URL
                            Date timestamp = docData.get("timestamp") instanceof Date ? (Date) docData.get("timestamp") : new Date(); // Check and convert timestamp
                            double latitude = docData.get("latitude") instanceof Number ? ((Number) Objects.requireNonNull(docData.get("latitude"))).doubleValue() : 0.0; // Check and convert latitude
                            double longitude = docData.get("longitude") instanceof Number ? ((Number) Objects.requireNonNull(docData.get("longitude"))).doubleValue() : 0.0; // Check and convert longitude

                            imageDataList.add(new GalleryAdapter.ImageData(imageUrl, timestamp, latitude, longitude));
                        }
                        updateImageList(imageDataList);
                    } else {
                        Log.d(TAG, "Error getting documents: ", task.getException());
                    }
                });
    }

    private void updateImageList(List<GalleryAdapter.ImageData> imageDataList) {
        adapter.updateData(imageDataList);
    }
}
