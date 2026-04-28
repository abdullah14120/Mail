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
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONException;
import org.json.JSONObject;
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
    private RequestQueue queue;

    // سنستخدم 1secMail لأنها تقبل الأسماء العشوائية فوراً
    private String generatedEmail = "";

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

        queue = Volley.newRequestQueue(this);

        // 1. توليد بريد عشوائي فوراً (بدون انتظار السيرفر)
        setupTemporaryIdentity();

        // 2. استقبال البيانات من التطبيقات الأخرى
        handleIncomingIntent();

        btnSend.setOnClickListener(v -> {
            if (etTo.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "يرجى تحديد المستلم", Toast.LENGTH_SHORT).show();
                return;
            }
            sendEmailViaForwarder();
        });

        btnAttach.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 101);
        });
    }

    private void setupTemporaryIdentity() {
        // توليد اسم عشوائي (مثلاً: ab84321)
        String user = "ab" + (new Random().nextInt(900000) + 100000);
        generatedEmail = user + "@1secmail.com";
        etFrom.setText(generatedEmail);
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            etSubject.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT));
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) etBody.setText(text);
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                attachmentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                updateFileLabel();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            attachmentUri = data.getData();
            updateFileLabel();
        }
    }

    private void updateFileLabel() {
        if (attachmentUri != null) {
            tvFileName.setText("المرفق: " + attachmentUri.getLastPathSegment());
            tvFileName.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }
    }

    // الطريقة الأضمن لتجنب 405 هي الإرسال عبر وسيط (السكربت الخاص بك)
    // لأن 1secMail و Mail.tm يمنعون الإرسال المباشر من الـ API المجاني
    private void sendEmailViaForwarder() {
        loader.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        // سنقوم بالإرسال إلى السكربت الخاص بك، والسكربت هو من يرسل للجيميل
        // هذا هو الحل النهائي الذي يستخدمه كبار المطورين
        String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbz10tkJV8IWZYaZ-3Hv5w--PWNIlmzkClB-yga3T0eGn5KiTfalwKnLc6KDlVvzmnTFRw/exec";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("action", "send");
            jsonBody.put("from", generatedEmail);
            jsonBody.put("to", etTo.getText().toString().trim());
            jsonBody.put("subject", etSubject.getText().toString().trim());
            
            String body = etBody.getText().toString();
            if (attachmentUri != null) {
                byte[] fileBytes = getBytesFromUri(attachmentUri);
                if (fileBytes != null) {
                    String base64File = Base64.encodeToString(fileBytes, Base64.NO_WRAP);
                    jsonBody.put("attachmentData", base64File);
                    jsonBody.put("attachmentName", attachmentUri.getLastPathSegment());
                }
            }
            jsonBody.put("body", body);

        } catch (Exception e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SCRIPT_URL, jsonBody,
                response -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "تم إرسال التقرير بنجاح!", Toast.LENGTH_LONG).show();
                    finish();
                },
                error -> {
                    loader.setVisibility(View.GONE);
                    btnSend.setEnabled(true);
                    Toast.makeText(this, "فشل الإرسال عبر الوسيط", Toast.LENGTH_SHORT).show();
                });

        queue.add(request);
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
