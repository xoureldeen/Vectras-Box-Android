package com.xoureldeen.vectrasbox;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.xoureldeen.vectrasbox.MainActivity.HardwareConfig;
import com.xoureldeen.vectrasbox.MainActivity.HardwareOptions;
import com.xoureldeen.vectrasbox.data.MachineStore;
import com.xoureldeen.vectrasbox.data.MachineStore.Machine;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MachineEditorActivity extends AppCompatActivity {
    public static final String EXTRA_MACHINE_ID = "machine_id";
    public static final String EXTRA_NAME = "prefill_name";
    public static final String EXTRA_CONFIG = "prefill_config";
    private static final int REQUEST_ICON = 501;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private MachineStore store;
    private String machineId;
    private Machine existing;
    private HardwareConfig hw;
    private Uri selectedIcon;
    private ImageView iconPreview;
    private MaterialButton saveButton;

    private TextInputEditText nameField, ramField;
    private Spinner machineSpinner, cpuSpinner, speedSpinner, videoSpinner, soundSpinner,
            midiSpinner, mouseSpinner, cdSpeedSpinner, diskSpinner;
    private CheckBox voodooCheck, timeSyncCheck, hddSoundsCheck;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        store = new MachineStore(this);
        machineId = getIntent().getStringExtra(EXTRA_MACHINE_ID);
        try {
            existing = machineId == null ? null : store.get(machineId);
            if (machineId != null && existing == null) throw new Exception("Machine not found.");
            hw = new HardwareConfig();
            if (existing != null) {
                hw.parseFromConfig(store.config(existing.id));
                hw.hddPath = existing.diskPath;
                hw.cdromPath = existing.isoPath;
                hw.memSizeMb = existing.ramMb;
            } else {
                String prefill = getIntent().getStringExtra(EXTRA_CONFIG);
                if (prefill != null) hw.parseFromConfig(prefill);
            }
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_machine_editor);
        View root = findViewById(R.id.editor_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.editor_toolbar);
        toolbar.setTitle(existing == null ? "Create machine" : "Edit " + existing.name);
        toolbar.setNavigationOnClickListener(v -> finish());
        saveButton = findViewById(R.id.editor_save);
        saveButton.setText(existing == null ? "Create" : "Save");
        saveButton.setOnClickListener(v -> save());
        buildForm(findViewById(R.id.editor_form));
    }

    private void buildForm(LinearLayout form) {
        section(form, "Machine artwork");
        iconPreview = new ImageView(this);
        iconPreview.setBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainer, Color.DKGRAY));
        showIcon(existing != null && existing.iconFile.isFile() ? Uri.fromFile(existing.iconFile) : null);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(-1, dp(180));
        artParams.topMargin = dp(8);
        iconPreview.setOnClickListener(v -> pickIcon());
        form.addView(iconPreview, artParams);

        section(form, "Identity");
        String initialName = existing != null ? existing.name : getIntent().getStringExtra(EXTRA_NAME);
        nameField = input(form, "Machine name", initialName == null ? "My Vectras Box PC" : initialName, false);

        section(form, "Motherboard and processor");
        machineSpinner = spinner(form, "Motherboard / architecture", HardwareOptions.MACHINE_LABELS,
                HardwareOptions.findMachineIndex(hw.machine));
        cpuSpinner = spinner(form, "Processor", HardwareOptions.CPU_LABELS,
                HardwareOptions.findCpuIndex(hw.cpuType));
        speedSpinner = spinner(form, "CPU speed", HardwareOptions.SPEED_LABELS,
                HardwareOptions.findSpeedIndex(hw.cpuSpeedHz));
        ramField = input(form, "Memory (MB)", String.valueOf(hw.memSizeMb), true);

        section(form, "Display and audio");
        videoSpinner = spinner(form, "Video chipset", HardwareOptions.VIDEO_LABELS,
                HardwareOptions.findVideoIndex(hw.videoCard));
        voodooCheck = check(form, "Enable 3dfx Voodoo", hw.hasVoodoo);
        soundSpinner = spinner(form, "Sound card", HardwareOptions.SOUND_LABELS,
                HardwareOptions.findSoundIndex(hw.soundCard));
        midiSpinner = spinner(form, "MIDI synthesizer", HardwareOptions.MIDI_LABELS,
                HardwareOptions.findMidiIndex(hw.midiDevice));

        section(form, "Input and system");
        mouseSpinner = spinner(form, "Mouse device", HardwareOptions.MOUSE_LABELS,
                HardwareOptions.findMouseIndex(hw.mouseType));
        timeSyncCheck = check(form, "Sync RTC time with phone", "local".equalsIgnoreCase(hw.timeSync));
        hddSoundsCheck = check(form, "Mechanical hard disk sounds", hw.hddSounds);
        cdSpeedSpinner = spinner(form, "CD-ROM speed", HardwareOptions.CD_SPEED_LABELS,
                HardwareOptions.findCdSpeedIndex(hw.cdromSpeed));

        if (existing == null) {
            section(form, "Storage");
            diskSpinner = spinner(form, "Create primary hard disk",
                    new String[]{"No hard disk", "1 GB", "2 GB", "4 GB", "8 GB"}, 3);
        } else {
            section(form, "Storage");
            detail(form, hw.hddPath.isEmpty() ? "No hard disk attached" : "Hard disk: " + new File(hw.hddPath).getName());
            detail(form, hw.cdromPath.isEmpty() ? "CD-ROM is empty" : "CD-ROM: " + new File(hw.cdromPath).getName());
            detail(form, "Use Machine options → VM Files & Storage to import or replace media.");
        }
    }

    private void pickIcon() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_ICON);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ICON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedIcon = data.getData();
            showIcon(selectedIcon);
        }
    }

    private void showIcon(@Nullable Uri uri) {
        if (uri != null) {
            iconPreview.setPadding(0, 0, 0, 0);
            iconPreview.setImageTintList(null);
            iconPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iconPreview.setImageURI(uri);
        } else {
            iconPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconPreview.setPadding(dp(64), dp(36), dp(64), dp(36));
            iconPreview.setImageResource(R.drawable.ic_computer);
            iconPreview.setImageTintList(ColorStateList.valueOf(color(androidx.appcompat.R.attr.colorPrimary, Color.CYAN)));
        }
    }

    private void save() {
        String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
        if (name.isEmpty()) { nameField.setError("Enter a machine name"); return; }
        int ram;
        try { ram = Integer.parseInt(String.valueOf(ramField.getText()).trim()); }
        catch (Exception error) { ramField.setError("Enter valid RAM"); return; }
        if (ram < 4 || ram > 2048) { ramField.setError("Use 4 to 2048 MB"); return; }

        hw.machine = HardwareOptions.MACHINE_VALUES[machineSpinner.getSelectedItemPosition()];
        hw.cpuType = HardwareOptions.CPU_VALUES[cpuSpinner.getSelectedItemPosition()];
        hw.cpuFamily = HardwareOptions.CPU_FAMILIES[cpuSpinner.getSelectedItemPosition()];
        hw.cpuSpeedHz = HardwareOptions.SPEED_VALUES[speedSpinner.getSelectedItemPosition()];
        hw.memSizeMb = ram;
        hw.videoCard = HardwareOptions.VIDEO_VALUES[videoSpinner.getSelectedItemPosition()];
        hw.hasVoodoo = voodooCheck.isChecked();
        hw.soundCard = HardwareOptions.SOUND_VALUES[soundSpinner.getSelectedItemPosition()];
        hw.midiDevice = HardwareOptions.MIDI_VALUES[midiSpinner.getSelectedItemPosition()];
        hw.mouseType = HardwareOptions.MOUSE_VALUES[mouseSpinner.getSelectedItemPosition()];
        hw.timeSync = timeSyncCheck.isChecked() ? "local" : "disabled";
        hw.hddSounds = hddSoundsCheck.isChecked();
        hw.cdromSpeed = HardwareOptions.CD_SPEED_VALUES[cdSpeedSpinner.getSelectedItemPosition()];

        saveButton.setEnabled(false);
        saveButton.setText("Saving…");
        final int diskGb = diskSpinner == null ? 0 : new int[]{0, 1, 2, 4, 8}[diskSpinner.getSelectedItemPosition()];
        worker.execute(() -> {
            try {
                Machine saved;
                if (existing == null) {
                    saved = store.create(name, hw.generateConfigText());
                    if (diskGb > 0) {
                        File disk = store.createDisk(name, diskGb);
                        store.update(saved.id, ram, disk.getAbsolutePath(), null);
                    }
                } else {
                    store.saveConfig(existing.id, hw.updateExistingConfig(store.config(existing.id)));
                    store.rename(existing.id, name);
                    store.update(existing.id, ram, hw.hddPath, hw.cdromPath);
                    saved = store.get(existing.id);
                }
                if (selectedIcon != null) {
                    store.setIcon(saved.id, selectedIcon);
                    saved = store.get(saved.id);
                }
                Machine result = saved;
                runOnUiThread(() -> {
                    Toast.makeText(this, result.name + " saved", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    saveButton.setText(existing == null ? "Create" : "Save");
                    new MaterialAlertDialogBuilder(this).setTitle("Could not save machine")
                            .setMessage(error.getMessage()).setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private TextInputEditText input(LinearLayout parent, String hint, String value, boolean number) {
        TextInputLayout box = new TextInputLayout(this);
        box.setHint(hint);
        TextInputEditText field = new TextInputEditText(box.getContext());
        field.setText(value);
        if (number) field.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(field, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = matchWrap(); params.topMargin = dp(8);
        parent.addView(box, params);
        return field;
    }

    private Spinner spinner(LinearLayout parent, String label, String[] values, int selected) {
        TextView title = new TextView(this); title.setText(label); title.setTextSize(13);
        LinearLayout.LayoutParams titleParams = matchWrap(); titleParams.topMargin = dp(12);
        parent.addView(title, titleParams);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(Math.max(0, Math.min(selected, values.length - 1)));
        parent.addView(spinner, matchWrap());
        return spinner;
    }

    private CheckBox check(LinearLayout parent, String text, boolean checked) {
        CheckBox check = new CheckBox(this); check.setText(text); check.setChecked(checked);
        parent.addView(check, matchWrap()); return check;
    }

    private void section(LinearLayout parent, String text) {
        TextView title = new TextView(this); title.setText(text); title.setTextSize(18);
        title.setTextColor(color(androidx.appcompat.R.attr.colorPrimary, Color.CYAN));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = matchWrap(); params.topMargin = dp(20); params.bottomMargin = dp(4);
        parent.addView(title, params);
    }

    private void detail(LinearLayout parent, String text) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(14);
        view.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY));
        LinearLayout.LayoutParams params = matchWrap(); params.topMargin = dp(5); parent.addView(view, params);
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private int color(int attr, int fallback) { return MaterialColors.getColor(this, attr, fallback); }

    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
