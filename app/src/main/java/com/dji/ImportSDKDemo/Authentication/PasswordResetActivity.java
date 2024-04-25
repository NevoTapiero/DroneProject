package com.dji.ImportSDKDemo.Authentication;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dji.ImportSDKDemo.R;
import com.google.firebase.auth.FirebaseAuth;

public class PasswordResetActivity extends AppCompatActivity {

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
                                Toast.makeText(PasswordResetActivity.this, "Reset link sent to your email", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(PasswordResetActivity.this, "Failed to send reset email", Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });
    }
}
