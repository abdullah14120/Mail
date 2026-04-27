package com.mail.ab; // استبدل هذا باسم حزمتك

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText etFrom, etTo, etSubject, etBody;
    private Button btnSend;
    private ProgressBar loader;
    private String currentToken = "";
    private RequestQueue queue;

    // استبدل هذا بالرابط الذي نشرته في Google Apps Script
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
        btnSend = findViewById(R.id.btnSend);
        loader = findViewById(R.id.loader);

        queue = Volley.newRequestQueue(this);

        // 1. استقبال البيانات إذا كان الطلب قادماً من تطبيق آخر
        handleIncomingIntent();

        // 2. جلب إيميل وتوكن جديد فور فتح التطبيق
        generateTempEmail();

        // 3. زر الإرسال
        btnSend.setOnClickListener(v -> {
            String to = etTo.getText().toString();
            String subject = etSubject.getText().toString();
            String body = etBody.getText().toString();

            if (!to.isEmpty() && !currentToken.isEmpty()) {
                sendEmail(to, subject, body);
            } else {
                Toast.makeText(this, "يرجى الانتظار حتى تجهيز البريد أو إكمال البيانات", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_SENDTO.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null && "mailto".equals(uri.getScheme())) {
                etTo.setText(uri.getSchemeSpecificPart());
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_EMAIL)) {
                String[] recipients = intent.getStringArrayExtra(Intent.EXTRA_EMAIL);
                if (recipients != null) etTo.setText(recipients[0]);
            }
            etSubject.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT));
            etBody.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
        }
    }

    private void generateTempEmail() {
        loader.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, SCRIPT_URL, null,
                response -> {
                    try {
                        etFrom.setText(response.getString("email"));
                        currentToken = response.getString("token");
                        loader.setVisibility(View.GONE);
                        btnSend.setEnabled(true);
                        Toast.makeText(this, "تم تجهيز بريد مؤقت جديد", Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "فشل في الاتصال بالسكربت", Toast.LENGTH_LONG).show();
                });
        queue.add(request);
    }

    private void sendEmail(String to, String subject, String body) {
        loader.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        String url = "https://api.mail.tm/messages";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("to", new JSONArray().put(to));
            jsonBody.put("subject", subject);
            jsonBody.put("text", body);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest sendReq = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                response -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "تم الإرسال بنجاح!", Toast.LENGTH_LONG).show();
                    finish(); // العودة للتطبيق الأصلي
                },
                error -> {
                    loader.setVisibility(View.GONE);
                    btnSend.setEnabled(true);
                    Toast.makeText(this, "خطأ في الإرسال: " + error.toString(), Toast.LENGTH_LONG).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + currentToken);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };
        queue.add(sendReq);
    }
}
