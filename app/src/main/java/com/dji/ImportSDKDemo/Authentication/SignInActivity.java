package com.dji.ImportSDKDemo.Authentication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;

import com.dji.ImportSDKDemo.ApplicationState;
import com.dji.ImportSDKDemo.BaseActivity;
import com.dji.ImportSDKDemo.NavigationBarActivities.FlyActivity;
import com.dji.ImportSDKDemo.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SignInActivity extends BaseActivity implements View.OnClickListener {

    private EditText emailInput;
    private EditText passwordInput;
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private GoogleSignInClient mGoogleSignInClient;
    private static final String TAG = SignInActivity.class.getSimpleName();
    private TextView forgotPasswordText;
    private AppCompatButton logInButton;
    private AppCompatButton createAccountButton;
    private ProgressBar mProgressBar;
    private FirebaseFirestore db;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        ApplicationState.isAppStarted = true;

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();


        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("603414430717-j3bp6i774hiaa2nvg871tsq3k4ocel8t.apps.googleusercontent.com")
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);


        // Initialize the views and listeners setup...
        setupViews();
        setupListeners();

    }

    private void setupViews() {
        // Initialize views
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        logInButton = findViewById(R.id.logInButton);

        createAccountButton = findViewById(R.id.createAccountButton);
        mProgressBar = findViewById(R.id.loadingProgressBar);

    }

    private void setupListeners() {
        createAccountButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SignUpActivity.class);
            startActivity(intent);
        });

        forgotPasswordText.setOnClickListener(v -> {
            Intent intent = new Intent(SignInActivity.this, PasswordResetActivity.class);
            startActivity(intent);
        });

        logInButton.setOnClickListener(view -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                showToast("Email and password cannot be empty", this);
                return;
            }

            // Sign in with Firebase Authentication
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(SignInActivity.this, task -> {
                        if (task.isSuccessful()) {
                            // Sign in success
                            showToast("Sign in successful", this);

                            // Navigate to the main activity
                            navigateToMain();
                        } else {
                            // If sign in fails, display a message to the user.
                            showToast("Authentication failed: " + Objects.requireNonNull(task.getException()).getMessage(), this);
                        }
                    });
        });
    }


    private void navigateToMain() {
        Intent intent = new Intent(SignInActivity.this, FlyActivity.class);
        startActivity(intent);
        finish();
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null) {
                            // Prepare user data
                            Map<String, Object> userData = getUserData(user);

                            // Add or update a new document with user's UID as the document ID
                            db.collection("Users").document(user.getUid())
                                    .set(userData, SetOptions.merge())
                                    .addOnSuccessListener(aVoid -> {
                                        mProgressBar.setVisibility(View.VISIBLE);
                                        setScreenTouchable(false);
                                        Log.d(TAG, "DocumentSnapshot successfully written!");
                                        showToast("User registered and data saved", this);

                                        // Navigate to the next activity or update the UI
                                        navigateToMain();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.w(TAG, "Error writing document", e);
                                        showToast("Error saving user data", this);
                                    });
                        }
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        showToast("Authentication failed: " + Objects.requireNonNull(task.getException()).getMessage(), this);
                    }
                });
    }


    @NonNull
    private static Map<String, Object> getUserData(FirebaseUser user) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("firstName", Objects.requireNonNull(user.getDisplayName()).split(" ")[0]); // Assuming the first name is the first part of the Display Name
        userData.put("lastName", user.getDisplayName().split(" ").length > 1 ? user.getDisplayName().split(" ")[1] : ""); // Assuming the last name is the second part of the Display Name
        userData.put("email", user.getEmail());
        return userData;
    }

    public void setScreenTouchable(boolean touchable) {
        FrameLayout overlay = findViewById(R.id.overlay);
        if (touchable) {
            overlay.setVisibility(View.GONE); // Hide overlay to enable touch events
        } else {
            overlay.setVisibility(View.VISIBLE); // Show overlay to disable touch events
        }
    }

    ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    handleSignInResult(GoogleSignIn.getSignedInAccountFromIntent(result.getData()));
                } else {
                    Log.w(TAG, "Google Sign-In Intent was not OK: " + result.getResultCode());
                }
            }
    );

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "firebaseAuthWithGoogle:" + account.getIdToken());
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode(), e);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.googleSignInButton) {
            signInWithGoogle();

        }
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
