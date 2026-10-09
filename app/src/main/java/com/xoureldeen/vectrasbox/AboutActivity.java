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

public final class AboutActivity extends AppCompatActivity {
    private static final String PROJECT_URL = "https://github.com/xoureldeen/Vectras-Box-Android";
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
        findViewById(R.id.about_privacy).setOnClickListener(view ->
                LegalTextDialogs.showAsset(this, getString(R.string.privacy_policy), "legal/PRIVACYANDPOLICY.md", ""));
        findViewById(R.id.about_terms).setOnClickListener(view ->
                LegalTextDialogs.showAsset(this, getString(R.string.terms_of_service), "legal/TERMSANDCONDITIONS.md", ""));
        findViewById(R.id.about_source).setOnClickListener(view -> openUrl(PROJECT_URL));
        findViewById(R.id.about_licenses).setOnClickListener(view ->
                startActivity(new Intent(this, OpenSourceLicensesActivity.class)));
    }

    private String readVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? getString(R.string.version_unknown) : info.versionName;
        } catch (Exception ignored) {
            return getString(R.string.version_unknown);
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.could_not_open_link, Toast.LENGTH_LONG).show();
        }
    }
}
