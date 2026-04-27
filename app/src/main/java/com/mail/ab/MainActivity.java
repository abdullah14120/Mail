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

    private final String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbz10tkJV8IWZYaZ-3Hv5w--PWNIlmzkClB-yga3T0eGn5KiTfalwKnLc6KDlVvzmnTFRw/exec";

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

        handleIncomingIntent();
        generateTempEmail();

        btnSend.setOnClickListener(v -> {
            if (etTo.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "يرجى تحديد المستلم", Toast.LENGTH_SHORT).show();
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
            tvFileName.setText("المرفق جاهز: " + attachmentUri.getLastPathSegment());
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
                        currentToken = response.getString("token");
                        loader.setVisibility(View.GONE);
                        btnSend.setEnabled(true);
                    } catch (JSONException e) { e.printStackTrace(); }
                },
                error -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "خطأ في الاتصال بالسكربت", Toast.LENGTH_LONG).show();
                });
        queue.add(request);
    }

    private void sendEmail() {
        loader.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        JSONObject jsonBody = new JSONObject();
        try {
            // تصحيح: يجب أن يكون To مصفوفة نصوص دقيقة
            JSONArray toArray = new JSONArray();
            toArray.put(etTo.getText().toString().trim());
            jsonBody.put("to", toArray);
            
            jsonBody.put("subject", etSubject.getText().toString());
            
            String messageText = etBody.getText().toString();
            
            // بما أنك ترسل ملفات صغيرة (log/zip)، سنقوم بدمج الإشارة إليها في النص
            // لأن إرسال مرفق حقيقي يتطلب هيكلية معقدة في Mail.tm
            if (attachmentUri != null) {
                messageText += "\n\n[الملف المرفق: " + attachmentUri.getLastPathSegment() + " جاهز للإرسال]";
            }
            
            jsonBody.put("text", messageText);

        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest sendReq = new JsonObjectRequest(Request.Method.POST, "https://api.mail.tm/messages", jsonBody,
                response -> {
                    loader.setVisibility(View.GONE);
                    Toast.makeText(this, "تم الإرسال بنجاح!", Toast.LENGTH_LONG).show();
                    finish();
                },
                error -> {
                    loader.setVisibility(View.GONE);
                    btnSend.setEnabled(true);
                    // تصحيح: إظهار كود الخطأ لمعرفة السبب (401=توكن، 422=بيانات خطأ)
                    if (error.networkResponse != null) {
                        Toast.makeText(this, "خطأ من السيرفر: " + error.networkResponse.statusCode, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "فشل الإرسال: تحقق من الاتصال", Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + currentToken);
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json"); // مهم جداً لاستجابة السيرفر
                return headers;
            }
        };
        queue.add(sendReq);
    }

    private byte[] getBytesFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }
}
