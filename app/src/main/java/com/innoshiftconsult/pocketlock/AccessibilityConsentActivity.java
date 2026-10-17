package com.innoshiftconsult.pocketlock;

import android.app.Activity;
import android.os.Bundle;

public class AccessibilityConsentActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accessibility_consent);
        findViewById(R.id.accessibilityConsentCancel).setOnClickListener(view -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        findViewById(R.id.accessibilityConsentAccept).setOnClickListener(view -> {
            setResult(RESULT_OK);
            finish();
        });
    }
}
