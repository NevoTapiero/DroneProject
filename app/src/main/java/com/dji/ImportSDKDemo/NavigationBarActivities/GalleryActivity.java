package com.dji.ImportSDKDemo.NavigationBarActivities;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.ImportSDKDemo.BaseActivity;
import com.dji.ImportSDKDemo.Library.CustomActivity;
import com.dji.ImportSDKDemo.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GalleryActivity extends BaseActivity {
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseUser user = mAuth.getCurrentUser();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String batchId;
    private TextView selectedBatch;
    private final List<String> batchNames = new ArrayList<>(), categories = new ArrayList<>(Arrays.asList("Corn_common_rust", "Corn_healthy", "Corn_Infected", "Corn_northern_leaf_blight", "Corn_gray_leaf_spots", "unclassified"));

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        initUI();

        // Get the Intent that started this activity
        Intent intent = getIntent();

        // Check if the Intent has the extra "batchId"
        if (intent != null && intent.hasExtra("batchId")) {
            batchId = intent.getStringExtra("batchId"); // Extract the batch ID sent from ScanActivity
            selectedBatch.setText("Selected Batch: " + batchId);

        }
    }

    private void initUI() {
        selectedBatch = findViewById(R.id.selectedBatch);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.nav_gallery);


        // Set listener for navigation item selection using if-else instead of switch
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_fly) {
                startActivity(new Intent(this, FlyActivity.class));

            } else if (itemId == R.id.nav_scan) {
                startActivity(new Intent(this, ScanActivity.class));

            } else //noinspection StatementWithEmptyBody
                if (itemId == R.id.nav_gallery) {

            } else if (itemId == R.id.nav_profile) {
                //startActivity(new Intent(this, ProfileActivity.class));
            }
            return true;
        });

        Button selectBatchesButton = findViewById(R.id.selectBatchesButton);
        selectBatchesButton.setOnClickListener(v -> loadBatches());


        // Find the buttons for each category
        Button btnCommonRust = findViewById(R.id.btn_common_rust);
        Button btnHealthy = findViewById(R.id.btn_healthy);
        Button btnInfected = findViewById(R.id.btn_infected);
        Button btnNorthernLeafBlight = findViewById(R.id.btn_northern_leaf_blight);
        Button btnGrayLeafSpots = findViewById(R.id.btn_gray_leaf_spots);
        Button btnUnclassified = findViewById(R.id.btn_unclassified);

        setupCategoryButton(btnCommonRust, "Corn_common_rust/");
        setupCategoryButton(btnHealthy, "Corn_healthy/");
        setupCategoryButton(btnInfected, "Corn_Infected/");
        setupCategoryButton(btnNorthernLeafBlight, "Corn_northern_leaf_blight/");
        setupCategoryButton(btnGrayLeafSpots, "Corn_gray_leaf_spots/");
        setupCategoryButton(btnUnclassified, "unclassified/");
    }

    private void setupCategoryButton(Button button, String category) {
        button.setOnClickListener(v -> {
            if (batchId == null) {
                showToast("Please select a batch first", this);
            } else {
                navigateToCustomActivity(category);
            }
        });
    }


    private void loadBatches() {
        if (user != null) {
            final AtomicInteger counter = new AtomicInteger(categories.size());
            for (String category : categories) {
                getBatches(category, () -> {
                    if (counter.decrementAndGet() == 0) {
                        if (batchNames.isEmpty()) {
                            Log.d("Firestore", "No batches found");
                            Toast.makeText(this, "You have 0 batches to classify", Toast.LENGTH_SHORT).show();
                        } else {
                            showBatchSelectionDialog(batchNames);
                        }
                    }
                });
            }
        } else {
            Log.d("Firestore", "User not logged in");
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
        }
    }

    private void getBatches(String category, Runnable onComplete) {
        db.collection("Users")
                .document(user.getUid())
                .collection(category)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            batchNames.add(document.getId());
                        }
                    } else {
                        Log.d("Firestore Error", "Error getting documents: ", task.getException());
                    }
                    onComplete.run();
                });
    }



    @SuppressLint("SetTextI18n")
    private void showBatchSelectionDialog(List<String> batchNames) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Batch");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_batch_selection_gallery, null);
        ListView listView = dialogView.findViewById(R.id.listViewBatchesGallery);

        // Set up the adapter for the ListView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, batchNames);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        builder.setView(dialogView);

        builder.setPositiveButton("OK", (dialog, which) -> {
            int checkedItem = listView.getCheckedItemPosition();
            if (checkedItem >= 0) {  // Ensure an item is actually selected
                batchId = batchNames.get(checkedItem);
                selectedBatch.setText("Selected Batch: " + batchId);
                batchNames.clear();
            } else {
                // Handle the case where no selection was made
                Toast.makeText(this, "No batch selected!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            batchId = null;
            selectedBatch.setText("Batch");
            batchNames.clear();
        });

        // Set a dismiss listener to handle cases where the dialog is dismissed by pressing outside
        builder.setOnCancelListener(dialog -> {
            showToast("dismiss", getApplicationContext());
            Toast.makeText(this, "dismiss", Toast.LENGTH_SHORT).show();
            selectedBatch.setText("Batch");
            batchNames.clear();
        });


        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void navigateToCustomActivity(String category) {
        Intent intent = new Intent(this, CustomActivity.class);
        intent.putExtra("category", category);
        intent.putExtra("batchID", batchId);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}