package com.xoureldeen.vectrasbox;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.xoureldeen.vectrasbox.data.MachineStore;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SetupActivity extends AppCompatActivity {
    private static final int REQUEST_ROMS_ZIP = 201;
    private static final int REQUEST_ASSETS_ZIP = 202;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private MachineStore store;
    private TextView romsStatus;
    private TextView assetsStatus;
    private MaterialButton importRoms;
    private MaterialButton importAssets;
    private MaterialButton continueButton;
    private boolean busy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new MachineStore(this);
        if (store.isSetupComplete()) {
            openHome();
            return;
        }

        setContentView(R.layout.activity_setup);
        View root = findViewById(R.id.setup_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        romsStatus = findViewById(R.id.setup_roms_status);
        assetsStatus = findViewById(R.id.setup_assets_status);
        importRoms = findViewById(R.id.setup_import_roms);
        importAssets = findViewById(R.id.setup_import_assets);
        continueButton = findViewById(R.id.setup_continue);

        importRoms.setOnClickListener(view -> pickZip(REQUEST_ROMS_ZIP));
        importAssets.setOnClickListener(view -> pickZip(REQUEST_ASSETS_ZIP));
        continueButton.setOnClickListener(view -> {
            if (store.isSetupComplete()) openHome();
        });
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null && romsStatus != null && !busy) refreshStatus();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) worker.shutdownNow();
        super.onDestroy();
    }

    private void pickZip(int requestCode) {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception error) {
            showError(new IOException("No document picker is available on this device.", error));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_ROMS_ZIP) {
            importZip(uri, false);
        } else if (requestCode == REQUEST_ASSETS_ZIP) {
            importZip(uri, true);
        }
    }

    private void importZip(Uri uri, boolean assets) {
        if (busy) return;
        busy = true;
        setButtonsEnabled(false);

        View body = getLayoutInflater().inflate(R.layout.dialog_import_progress, null, false);
        TextView status = body.findViewById(R.id.import_status);
        LinearProgressIndicator progressBar = body.findViewById(R.id.import_progress);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(assets ? R.string.setup_importing_assets : R.string.setup_importing_roms)
                .setView(body)
                .setCancelable(false)
                .create();
        dialog.show();

        worker.execute(() -> {
            try {
                long[] lastUpdate = {0};
                int imported = assets
                        ? store.importAssetsZip(uri, (copied, total) -> updateProgress(
                                status, progressBar, copied, total, lastUpdate))
                        : store.importRomsZip(uri, (copied, total) -> updateProgress(
                                status, progressBar, copied, total, lastUpdate));
                runOnUiThread(() -> {
                    busy = false;
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    refreshStatus();
                    Toast.makeText(this, getString(assets ? R.string.setup_assets_result
                            : R.string.setup_roms_result, imported), Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    busy = false;
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    refreshStatus();
                    showError(error);
                });
            }
        });
    }

    private void updateProgress(TextView status, LinearProgressIndicator progressBar,
                                long copied, long total, long[] lastUpdate) {
        long now = SystemClock.uptimeMillis();
        if (now - lastUpdate[0] < 150 && copied != total) return;
        lastUpdate[0] = now;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (total > 0) {
                int percent = (int) Math.min(100, copied * 100L / total);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(percent);
                if (percent <= 50) {
                    status.setText(getString(R.string.setup_checking_percent, percent * 2));
                } else {
                    status.setText(getString(R.string.setup_extracting_percent, (percent - 50) * 2));
                }
            } else {
                status.setText(getString(R.string.setup_checking_bytes,
                        Formatter.formatFileSize(this, copied)));
            }
        });
    }

    private void refreshStatus() {
        boolean romsReady = store.hasRoms();
        boolean assetsReady = store.hasAssets();
        romsStatus.setText(romsReady ? R.string.setup_roms_ready : R.string.setup_roms_required);
        assetsStatus.setText(assetsReady ? R.string.setup_assets_ready : R.string.setup_assets_required);
        importRoms.setText(romsReady ? R.string.setup_add_roms : R.string.setup_import_roms);
        importAssets.setText(assetsReady ? R.string.setup_add_assets : R.string.setup_import_assets);
        continueButton.setEnabled(romsReady && assetsReady && !busy);
        setButtonsEnabled(!busy);
    }

    private void setButtonsEnabled(boolean enabled) {
        if (importRoms != null) importRoms.setEnabled(enabled);
        if (importAssets != null) importAssets.setEnabled(enabled);
        if (!enabled && continueButton != null) continueButton.setEnabled(false);
    }

    private void showError(Exception error) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setup_import_failed)
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton(R.string.setup_try_again, null)
                .show();
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
