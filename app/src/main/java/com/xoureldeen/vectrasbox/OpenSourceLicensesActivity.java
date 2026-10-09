package com.xoureldeen.vectrasbox;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

import java.io.IOException;
import java.util.Arrays;

public final class OpenSourceLicensesActivity extends AppCompatActivity {
    private static final String[][] COMPONENTS = {
            {"Vectras Box", "GNU GPL v3.0", "https://github.com/xoureldeen/Vectras-Box-Android", "gpl-3.0.txt"},
            {"PCBox / 86Box", "GNU GPL v2.0 or later; file-specific notices apply", "https://github.com/PCBox/PCBox", "gpl-2.0.txt"},
            {"SDL 2", "zlib license", "https://github.com/libsdl-org/SDL", "sdl-2.txt"},
            {"libslirp 4.9.5", "BSD / MIT, file-specific notices", "https://gitlab.freedesktop.org/slirp/libslirp", "libslirp-source-attributions.txt"},
            {"Munt MT-32 / CM-32L", "GNU LGPL v2.1 or later", "https://github.com/munt/munt", "libsndfile-lgpl-2.1.txt"},
            {"zlib 1.3.2", "zlib license", "https://github.com/madler/zlib", "zlib.txt"},
            {"libpng 1.6.53", "libpng license", "https://github.com/pnggroup/libpng", "libpng.txt"},
            {"FreeType 2.13.3", "GNU GPL option", "https://gitlab.freedesktop.org/freetype/freetype", "freetype-gpl-2.0.txt"},
            {"libsndfile 1.2.2", "GNU LGPL v2.1 or later", "https://github.com/libsndfile/libsndfile", "libsndfile-lgpl-2.1.txt"},
            {"Material Components / AndroidX", "Apache License 2.0", "https://developer.android.com/jetpack/androidx", "apache-2.0.txt"},
            {"Google Material Symbols", "Apache License 2.0", "https://github.com/google/material-design-icons", "apache-2.0.txt"},
            {"Font Awesome GitHub icon", "CC BY 4.0", "https://github.com/FortAwesome/Font-Awesome", "fontawesome-5.txt"}
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_source_licenses);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.licenses_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        MaterialToolbar toolbar = findViewById(R.id.licenses_toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.drawer_close);
        toolbar.setNavigationOnClickListener(view -> finish());
        LinearLayout list = findViewById(R.id.licenses_list);
        for (int i = 0; i < COMPONENTS.length; i++) {
            String[] entry = COMPONENTS[i];
            addRow(list, entry[0], entry[1], i == 0, false, () ->
                    LegalTextDialogs.showAsset(this, entry[0], "licenses/" + entry[3],
                            entry[1] + "\n" + entry[2] + "\n\n"));
        }
        addRow(list, getString(R.string.component_notices), getString(R.string.component_notices_description),
                false, true, () -> LegalTextDialogs.showAsset(this, getString(R.string.component_notices),
                        "licenses/component-notices.txt", ""));
        LinearLayout filesList = findViewById(R.id.license_files_list);
        try {
            String[] files = getAssets().list("licenses");
            if (files == null) throw new IOException("Missing license assets");
            Arrays.sort(files);
            for (int i = 0; i < files.length; i++) {
                String file = files[i];
                addRow(filesList, file, getString(R.string.full_license_texts), i == 0, i == files.length - 1,
                        () -> LegalTextDialogs.showAsset(this, file, "licenses/" + file, ""));
            }
        } catch (IOException error) {
            Toast.makeText(this, R.string.unable_to_load_legal_text, Toast.LENGTH_LONG).show();
        }
    }

    private void addRow(LinearLayout list, String name, String license, boolean first, boolean last, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, first ? 0 : dp(2), 0, 0);
        row.setLayoutParams(params);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        float top = dp(first ? 18 : 4), bottom = dp(last ? 18 : 4);
        shape.setCornerRadii(new float[] {top, top, top, top, bottom, bottom, bottom, bottom});
        int ripple = MaterialColors.getColor(row, androidx.appcompat.R.attr.colorControlHighlight);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), shape, null));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());
        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setPadding(0, 0, 0, dp(8));
        row.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(license);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setAlpha(0.7f);
        row.addView(subtitle);
        list.addView(row);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
