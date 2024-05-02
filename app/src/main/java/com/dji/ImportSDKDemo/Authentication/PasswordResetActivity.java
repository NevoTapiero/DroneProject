package com.dji.ImportSDKDemo.Authentication;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;

import com.dji.ImportSDKDemo.BaseActivity;
import com.dji.ImportSDKDemo.R;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class PasswordResetActivity extends BaseActivity {

    private EditText emailEditText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_reset);

        mAuth = FirebaseAuth.getInstance();
        emailEditText = findViewById(R.id.emailEditText);
        Button submitResetButton = findViewById(R.id.submitResetButton);

        submitResetButton.setOnClickListener(view -> {
            String email = emailEditText.getText().toString().trim();
            if (!TextUtils.isEmpty(email)) {
                mAuth.sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                showToast("Reset link sent to your email", this);
                            } else {
                                showToast("Failed to send reset link" + Objects.requireNonNull(task.getException()).getMessage(), this);
                            }
                        });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
