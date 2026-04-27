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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText etFrom, etTo, etSubject, etBody;
    private TextView tvFileName;
    private MaterialButton btnSend, btnAttach;
    private ProgressBar loader;
    private String currentToken = "";
    private Uri attachmentUri = null; 
    private RequestQueue queue;

    // رابط السكربت الخاص بك
    private final String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbz10tkJV8IWZYaZ-3Hv5w--PWNIlmzkClB-yga3T0eGn5KiTfalwKnLc6KDlVvzmnTFRw/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ربط العناصر
        etFrom = findViewById(R.id.etFrom);
        etTo = findViewById(R.id.etTo);
        etSubject = findViewById(R.id.etSubject);
        etBody = findViewById(R.id.etBody);
        tvFileName = findViewById(R.id.tvFileName);
        btnSend = findViewById(R.id.btnSend);
        btnAttach = findViewById(R.id.btnAttach);
        loader = findViewById(R.id.loader);

        queue = Volley.newRequestQueue(this);

        handleIncomingIntent();
        generateTempEmail();

        btnSend.setOnClickListener(v -> {
            if (etTo.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "يرجى تحديد المستلم", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentToken == null || currentToken.isEmpty()) {
                Toast.makeText(this, "يرجى انتظار تجهيز البريد...", Toast.LENGTH_SHORT).show();
                return;
            }
            sendEmail();
        });

        btnAttach.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 101);
        });
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            etSubject.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT));
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) etBody.setText(text);

            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                attachmentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                updateFileLabel();
            }
        } else if (Intent.ACTION_SENDTO.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null && "mailto".equals(uri.getScheme())) {
                etTo.setText(uri.getSchemeSpecificPart());
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

    private void generateTempEmail() {
        loader.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, SCRIPT_URL, null,
                response -> {
                    try {
                        etFrom.setText(response.getString("email"));
                        // تخزين التوكن وتجريده من أي مسافات زائدة قد تسبب خطأ 401 أو 405
                        currentToken = response.getString("token").trim();
                        loader.setVisibility(View.GONE);
                        btnSend.setEnabled(true);
                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "فشل الاتصال بالسكربت", Toast.LENGTH_LONG).show();
                });
        queue.add(request);
    }

    private void sendEmail() {
        loader.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        // الرابط المباشر والقانوني للإرسال (تأكد من عدم وجود / في النهاية)
        final String URL = "https://api.mail.tm/messages";

        JSONObject jsonBody = new JSONObject();
        try {
            // تجريد الحقول تماماً لضمان القبول
            JSONArray toArray = new JSONArray();
            toArray.put(etTo.getText().toString().trim());
            
            jsonBody.put("to", toArray);
            jsonBody.put("subject", etSubject.getText().toString().trim());
            
            String messageText = etBody.getText().toString().trim();
            if (attachmentUri != null) {
                messageText += "\n\n[الملف المرفق: " + attachmentUri.getLastPathSegment() + "]";
            }
            jsonBody.put("text", messageText);

        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest sendReq = new JsonObjectRequest(Request.Method.POST, URL, jsonBody,
                response -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "تم الإرسال بنجاح!", Toast.LENGTH_LONG).show();
                    finish();
                },
                error -> {
                    loader.setVisibility(View.GONE);
                    btnSend.setEnabled(true);
                    if (error.networkResponse != null) {
                        // إظهار الكود الرقمي للخطأ للتشخيص (405 تعني خلل في الطريقة أو التوجيه)
                        Toast.makeText(this, "خطأ السيرفر: " + error.networkResponse.statusCode, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "فشل الإرسال: تحقق من الاتصال", Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                // صياغة التوكن بدقة متناهية
                headers.put("Authorization", "Bearer " + currentToken);
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("Accept", "application/json");
                
                // سطر إضافي لتمثيل دور المتصفح وتفادي منع الطلبات البرمجية (حل لخطأ 405)
                headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)");
                return headers;
            }
        };

        queue.add(sendReq);
    }
}
