package com.mail.ab;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText etFrom, etTo, etSubject, etBody;
    private TextView tvFileName;
    private MaterialButton btnSend, btnAttach;
    private ProgressBar loader;
    private Uri attachmentUri = null;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etFrom = findViewById(R.id.etFrom);
        etTo = findViewById(R.id.etTo);
        etSubject = findViewById(R.id.etSubject);
        etBody = findViewById(R.id.etBody);
        tvFileName = findViewById(R.id.tvFileName);
        btnSend = findViewById(R.id.btnSend);
        btnAttach = findViewById(R.id.btnAttach);
        loader = findViewById(R.id.loader);

        // تهيئة Firebase Database (المسار: outbox)
        dbRef = FirebaseDatabase.getInstance().getReference("outbox");

        // توليد اسم بريد مؤقت للعرض
        String tempUser = "ab" + (new Random().nextInt(900000) + 100000);
        etFrom.setText(tempUser + "@1secmail.com");

        handleIncomingIntent();

        btnSend.setOnClickListener(v -> {
            if (etTo.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "يرجى تحديد المستلم", Toast.LENGTH_SHORT).show();
                return;
            }
            sendViaFirebaseQueue();
        });

        btnAttach.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 101);
        });
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            etSubject.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT));
            etBody.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                attachmentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                tvFileName.setText("المرفق جاهز للإرسال");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            attachmentUri = data.getData();
            tvFileName.setText("المرفق: " + attachmentUri.getLastPathSegment());
        }
    }

    private void sendViaFirebaseQueue() {
        loader.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        String requestId = dbRef.push().getKey();
        Map<String, Object> mailData = new HashMap<>();
        mailData.put("to", etTo.getText().toString().trim());
        mailData.put("subject", etSubject.getText().toString().trim());
        mailData.put("body", etBody.getText().toString());
        mailData.put("from_info", etFrom.getText().toString());

        if (attachmentUri != null) {
            try {
                byte[] bytes = getBytesFromUri(attachmentUri);
                mailData.put("attachmentData", Base64.encodeToString(bytes, Base64.NO_WRAP));
                mailData.put("attachmentName", attachmentUri.getLastPathSegment());
            } catch (IOException e) { e.printStackTrace(); }
        }

        // الرفع إلى Firebase (مجاني وسريع)
        dbRef.child(requestId).setValue(mailData).addOnCompleteListener(task -> {
            loader.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                Toast.makeText(this, "تم الإرسال بنجاح (عبر الطابور الآمن)", Toast.LENGTH_LONG).show();
                finish();
            } else {
                btnSend.setEnabled(true);
                Toast.makeText(this, "فشل الرفع: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private byte[] getBytesFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }
}
