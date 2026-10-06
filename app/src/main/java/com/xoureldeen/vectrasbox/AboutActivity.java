package com.xoureldeen.vectrasbox;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AboutActivity extends AppCompatActivity {
    private static final String PROJECT_URL = "https://github.com/xoureldeen/Vectras-Box-Android";
    private static final String PRIVACY_URL = "https://github.com/xoureldeen/Vectras-Box-Android/blob/main/PRIVACYANDPOLICY.md";
    private static final String TERMS_URL = "https://github.com/xoureldeen/Vectras-Box-Android/blob/main/TERMSANDCONDITIONS.md";
    private static final String TELEGRAM_URL = "https://t.me/vectras_box";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.about_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.about_toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.drawer_close);
        toolbar.setNavigationOnClickListener(view -> finish());

        TextView version = findViewById(R.id.about_version);
        version.setText(getString(R.string.app_version_format, readVersionName()));

        findViewById(R.id.about_github).setOnClickListener(view -> openUrl(PROJECT_URL));
        findViewById(R.id.about_telegram).setOnClickListener(view -> openUrl(TELEGRAM_URL));
        findViewById(R.id.about_privacy).setOnClickListener(view -> openUrl(PRIVACY_URL));
        findViewById(R.id.about_terms).setOnClickListener(view -> openUrl(TERMS_URL));
        findViewById(R.id.about_source).setOnClickListener(view -> openUrl(PROJECT_URL));
        findViewById(R.id.about_licenses).setOnClickListener(view -> showLicenses());
    }

    private String readVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "1.0.0" : info.versionName;
        } catch (Exception ignored) {
            return "1.0.0";
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.could_not_open_link, Toast.LENGTH_LONG).show();
        }
    }

    private void showLicenses() {
        String notices;
        try {
            notices = readRawText(R.raw.open_source_notices);
        } catch (IOException error) {
            notices = error.getMessage();
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.open_source_licenses)
                .setMessage(notices)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String readRawText(int resourceId) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream input = getResources().openRawResource(resourceId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (text.length() > 0) text.append('\n');
                text.append(line);
            }
        }
        return text.toString();
    }
}
