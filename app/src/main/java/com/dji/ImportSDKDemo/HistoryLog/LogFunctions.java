package com.dji.ImportSDKDemo.HistoryLog;

import android.util.Log;

import com.dji.ImportSDKDemo.NavigationBarActivities.ScanActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LogFunctions {

    private static final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private static final FirebaseUser user = mAuth.getCurrentUser();

    public static void uploadListToFirestore(LogEntry stringData) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        fetchData(new ScanActivity.FirestoreCallback() {
            @Override
            public void onComplete(List<LogEntry> result) {
                /*if(result.size() == 20){
                    result.remove(0);
                }*/
                result.add(stringData);
                Map<String, Object> logList = new HashMap<>();
                logList.put("listEntries", result);

                db.collection("Users").document(Objects.requireNonNull(user).getUid()).set(logList, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> Log.d("Firestore", "List successfully written!"))
                        .addOnFailureListener(e -> Log.w("Firestore", "Error writing document", e));
            }

            @Override
            public void onError(String message) {
                // Handle any errors, e.g., show an error message
                Log.d("Firestore", "Error: " + message);
            }
        });
    }


    public static void fetchData(ScanActivity.FirestoreCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Users").document(Objects.requireNonNull(user).getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<LogEntry> entries = new ArrayList<>();
                        @SuppressWarnings("unchecked") // Suppressing unchecked cast warning
                        List<Map<String, Object>> rawEntries = (List<Map<String, Object>>) documentSnapshot.get("listEntries");
                        if (rawEntries != null) {
                            for (Map<String, Object> entry : rawEntries) {
                                LogEntry logEntry = new LogEntry( Objects.requireNonNull(entry.get("batchName")).toString(), Objects.requireNonNull(entry.get("message")).toString());
                                entries.add(logEntry);
                            }
                        }
                        callback.onComplete(entries);
                    } else {
                        callback.onError("No document found");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}