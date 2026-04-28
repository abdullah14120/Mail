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

        // ربط الواجهة
        etFrom = findViewById(R.id.etFrom);
        etTo = findViewById(R.id.etTo);
        etSubject = findViewById(R.id.etSubject);
        etBody = findViewById(R.id.etBody);
        tvFileName = findViewById(R.id.tvFileName);
        btnSend = findViewById(R.id.btnSend);
        btnAttach = findViewById(R.id.btnAttach);
        loader = findViewById(R.id.loader);

        // تهيئة قاعدة البيانات
        dbRef = FirebaseDatabase.getInstance().getReference("outbox");

        // توليد هوية مؤقتة عشوائية
        generateTempIdentity();

        // استدعاء المعالج الذكي للبيانات القادمة
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

    private void generateTempIdentity() {
        String tempUser = "ab" + (new Random().nextInt(900000) + 100000);
        etFrom.setText(tempUser + "@1secmail.com");
    }

    /**
     * المعالج الذكي: يقرأ البيانات من Intents الخارجية ويملأ الفراغات تلقائياً
     */
    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        String action = intent.getAction();
        String type = intent.getType();

        // 1. التعامل مع روابط mailto: (مثل الضغط على إيميل في المتصفح)
        if (Intent.ACTION_SENDTO.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null && "mailto".equals(uri.getScheme())) {
                String email = uri.getSchemeSpecificPart();
                etTo.setText(email);
            }
        } 
        
        // 2. التعامل مع "المشاركة" (ACTION_SEND) من تطبيقات أخرى
        else if (Intent.ACTION_SEND.equals(action) && type != null) {
            
            // إذا كان النص قادماً (مثل مشاركة رابط أو نص ملاحظة)
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                String sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                
                if (sharedText != null) etBody.setText(sharedText);
                if (sharedSubject != null) etSubject.setText(sharedSubject);
            }

            // إذا كان هناك ملف مرفق (Log, Zip, Image, الخ)
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                attachmentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (attachmentUri != null) {
                    tvFileName.setText("المرفق: " + attachmentUri.getLastPathSegment());
                }
            }
        }
        
        // 3. التعامل مع مشاركة عدة ملفات في آن واحد
        else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && intent.hasExtra(Intent.EXTRA_STREAM)) {
            // ملاحظة: للتطوير المستقبلي، يمكنك التعامل مع ArrayList<Uri> هنا
            Toast.makeText(this, "سيتم إرسال الملف الأول فقط من القائمة", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            attachmentUri = data.getData();
            if (attachmentUri != null) {
                tvFileName.setText("المرفق: " + attachmentUri.getLastPathSegment());
            }
        }
    }

    private void sendViaFirebaseQueue() {
        loader.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        String requestId = dbRef.push().getKey();
        if (requestId == null) return;

        Map<String, Object> mailData = new HashMap<>();
        mailData.put("to", etTo.getText().toString().trim());
        mailData.put("subject", etSubject.getText().toString().trim());
        mailData.put("body", etBody.getText().toString());
        mailData.put("from_info", etFrom.getText().toString());

        if (attachmentUri != null) {
            try {
                byte[] bytes = getBytesFromUri(attachmentUri);
                if (bytes != null) {
                    mailData.put("attachmentData", Base64.encodeToString(bytes, Base64.NO_WRAP));
                    mailData.put("attachmentName", attachmentUri.getLastPathSegment() != null ? 
                                 attachmentUri.getLastPathSegment() : "attachment_file");
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "خطأ في قراءة الملف", Toast.LENGTH_SHORT).show();
            }
        }

        dbRef.child(requestId).setValue(mailData).addOnCompleteListener(task -> {
            loader.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                Toast.makeText(this, "تم الإرسال بنجاح!", Toast.LENGTH_LONG).show();
                finish();
            } else {
                btnSend.setEnabled(true);
                Toast.makeText(this, "فشل: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private byte[] getBytesFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) return null;
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        inputStream.close();
        return byteBuffer.toByteArray();
    }
}
