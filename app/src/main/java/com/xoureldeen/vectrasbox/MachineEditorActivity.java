package com.xoureldeen.vectrasbox;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.text.InputType;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.xoureldeen.vectrasbox.MainActivity.HardwareConfig;
import com.xoureldeen.vectrasbox.MainActivity.HardwareOptions;
import com.xoureldeen.vectrasbox.data.MachineConfiguration;
import com.xoureldeen.vectrasbox.data.MachineStore;
import com.xoureldeen.vectrasbox.data.MachineStore.Machine;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MachineEditorActivity extends AppCompatActivity {
    public static final String EXTRA_MACHINE_ID = "machine_id";
    public static final String EXTRA_NAME = "prefill_name";
    public static final String EXTRA_CONFIG = "prefill_config";
    private static final int REQUEST_ICON = 501, REQUEST_DISK = 502, REQUEST_ISO = 503, REQUEST_FLOPPY = 504;
    private static final int REQUEST_CONFIG = 505;
    private static final int DISK_NEW = 0, DISK_EXISTING = 1, DISK_NONE = 2;
    private static final String[] FLOPPY_TYPES = {"35_2hd", "35_2dd", "525_2hd", "525_2dd", "525_1dd", "35_2ed", "none"};

    private MachineStore store;
    private MachineEditorViewModel model;
    private Machine existing;
    private HardwareConfig hw;
    private String baseConfig = "", formBaseline;
    private Uri selectedIcon;
    private ShapeableImageView iconPreview;
    private MaterialButton saveButton;
    private LinearLayout form, newDiskOptions, existingDiskOptions;
    private LinearProgressIndicator progress;
    private MaterialTextView progressText;
    private TextInputEditText nameField, ramField;
    private TextInputLayout nameBox, ramBox;
    private Choice machineChoice, cpuChoice, speedChoice, videoChoice, soundChoice,
            midiChoice, mouseChoice, cdSpeedChoice, diskSizeChoice, clockChoice, floppyChoice, networkChoice, networkCardChoice;
    private MaterialSwitch voodooSwitch, hddSoundsSwitch, dynarecSwitch, softfloatSwitch,
            floppyProtectSwitch, floppyTurboSwitch, floppyBpbSwitch;
    private MaterialButtonToggleGroup diskSourceButtons;
    private int diskSource = DISK_NEW;
    private String diskSize = "4";
    private final MediaSelection disk = new MediaSelection(), iso = new MediaSelection(), floppy = new MediaSelection();

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        store = new MachineStore(this);
        model = new ViewModelProvider(this).get(MachineEditorViewModel.class);
        try {
            String id = getIntent().getStringExtra(EXTRA_MACHINE_ID);
            existing = id == null ? null : store.get(id);
            if (id != null && existing == null) throw new Exception(getString(R.string.editor_machine_missing));
            baseConfig = existing != null ? store.config(id) : getIntent().getStringExtra(EXTRA_CONFIG);
            if (baseConfig == null) baseConfig = "";
            if (state != null) baseConfig = state.getString("draft_config", baseConfig);
            hw = new HardwareConfig();
            hw.parseFromConfig(baseConfig);
            if (existing != null && state == null) {
                hw.hddPath = existing.diskPath;
                hw.cdromPath = existing.isoPath;
                hw.memSizeMb = existing.ramMb;
            }
            disk.path = hw.hddPath;
            iso.path = hw.cdromPath;
            floppy.path = hw.floppyPath;
            diskSource = disk.path.isEmpty() ? DISK_NEW : DISK_EXISTING;
            if (state != null) {
                selectedIcon = uri(state.getString("icon_uri"));
                diskSource = state.getInt("disk_source", diskSource);
                diskSize = state.getString("disk_size", "4");
                disk.restore(state, "disk");
                iso.restore(state, "iso");
                floppy.restore(state, "floppy");
            }
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_machine_editor);
        View root = findViewById(R.id.editor_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        MaterialToolbar toolbar = findViewById(R.id.editor_toolbar);
        toolbar.setTitle(existing == null ? getString(R.string.editor_create_title)
                : getString(R.string.editor_edit_title, existing.name));
        toolbar.setNavigationOnClickListener(v -> leave());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
        saveButton = findViewById(R.id.editor_save);
        saveButton.setText(existing == null ? R.string.editor_create : R.string.editor_save);
        saveButton.setIconResource(existing == null ? R.drawable.ic_add : R.drawable.ic_edit);
        saveButton.setOnClickListener(v -> save());
        progress = findViewById(R.id.editor_progress);
        progressText = findViewById(R.id.editor_progress_text);
        form = findViewById(R.id.editor_form);
        String name = state != null ? state.getString("draft_name") : existing != null
                ? existing.name : getIntent().getStringExtra(EXTRA_NAME);
        buildForm(name == null ? getString(R.string.editor_default_name) : name,
                state == null ? String.valueOf(hw.memSizeMb) : state.getString("draft_ram", String.valueOf(hw.memSizeMb)));
        model.state().observe(this, this::renderSaveState);
    }

    private void buildForm(String name, String ram) {
        form.removeAllViews();
        LinearLayout artwork = section(R.string.editor_artwork);
        iconPreview = new ShapeableImageView(this);
        iconPreview.setShapeAppearanceModel(iconPreview.getShapeAppearanceModel().toBuilder().setAllCornerSizes(dp(16)).build());
        iconPreview.setBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerHighest, Color.DKGRAY));
        iconPreview.setContentDescription(getString(R.string.editor_choose_artwork));
        iconPreview.setOnClickListener(v -> pick(REQUEST_ICON, "image/*"));
        artwork.addView(iconPreview, new LinearLayout.LayoutParams(-1, dp(156)));
        showIcon(selectedIcon != null ? selectedIcon : existing != null && existing.iconFile.isFile() ? Uri.fromFile(existing.iconFile) : null);
        button(artwork, R.string.editor_choose_artwork, R.drawable.ic_edit, () -> pick(REQUEST_ICON, "image/*"));

        LinearLayout identity = section(R.string.editor_identity);
        nameField = input(identity, R.string.editor_name, name, false);
        nameField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameBox = (TextInputLayout) nameField.getParent().getParent();
        nameBox.setCounterMaxLength(80);
        nameBox.setCounterEnabled(true);

        LinearLayout processor = section(R.string.editor_processor);
        machineChoice = choice(processor, R.string.editor_motherboard, HardwareOptions.MACHINE_LABELS, HardwareOptions.MACHINE_VALUES, hw.machine);
        cpuChoice = choice(processor, R.string.editor_cpu, HardwareOptions.CPU_LABELS, HardwareOptions.CPU_VALUES, hw.cpuType);
        speedChoice = choice(processor, R.string.editor_cpu_speed, HardwareOptions.SPEED_LABELS, strings(HardwareOptions.SPEED_VALUES), String.valueOf(hw.cpuSpeedHz));
        ramField = input(processor, R.string.editor_memory, ram, true);
        ramBox = (TextInputLayout) ramField.getParent().getParent();
        ramBox.setHelperText(getString(R.string.editor_memory_hint));
        dynarecSwitch = toggle(processor, R.string.editor_dynarec, hw.cpuDynarec);
        softfloatSwitch = toggle(processor, R.string.editor_softfloat, hw.fpuSoftfloat);

        LinearLayout display = section(R.string.editor_display_audio);
        videoChoice = choice(display, R.string.editor_video, HardwareOptions.VIDEO_LABELS, HardwareOptions.VIDEO_VALUES, hw.videoCard);
        voodooSwitch = toggle(display, R.string.editor_voodoo, hw.hasVoodoo);
        soundChoice = choice(display, R.string.editor_sound, HardwareOptions.SOUND_LABELS, HardwareOptions.SOUND_VALUES, hw.soundCard);
        midiChoice = choice(display, R.string.editor_midi,
                new String[]{getString(R.string.editor_disabled), "Roland MT-32 (old)", "Roland MT-32 (new)", "Roland CM-32L", "Roland CM-32LN"},
                new String[]{"none", "mt32", "mt32_new", "cm32l", "cm32ln"}, hw.midiDevice);
        detail(display, R.string.editor_midi_note);

        LinearLayout system = section(R.string.editor_system);
        mouseChoice = choice(system, R.string.editor_mouse, HardwareOptions.MOUSE_LABELS, HardwareOptions.MOUSE_VALUES, hw.mouseType);
        clockChoice = choice(system, R.string.editor_clock, getResources().getStringArray(R.array.editor_clock_labels),
                new String[]{"local", "utc", "disabled"}, hw.timeSync);
        hddSoundsSwitch = toggle(system, R.string.editor_disk_sounds, hw.hddSounds);

        LinearLayout network = section(R.string.editor_network);
        networkChoice = choice(network, R.string.editor_network_mode,
                new String[]{getString(R.string.editor_disabled), "NAT (SLiRP)"},
                new String[]{"none", "slirp"}, hw.networkType);
        networkCardChoice = choice(network, R.string.editor_network_card,
                new String[]{getString(R.string.editor_disabled), "Realtek RTL8139C+ (PCI)", "Realtek RTL8029AS (PCI)", "NE2000 (ISA)"},
                new String[]{"none", "rtl8139c+", "ne2kpci", "ne2k"}, hw.networkCard);
        detail(network, R.string.editor_network_hint);

        LinearLayout storage = section(R.string.editor_hard_disk);
        if (existing == null) {
            diskSourceButtons = new MaterialButtonToggleGroup(this);
            diskSourceButtons.setSingleSelection(true);
            diskSourceButtons.setSelectionRequired(true);
            int[] ids = {R.id.editor_disk_new, R.id.editor_disk_existing, R.id.editor_disk_none};
            int[] labels = {R.string.editor_disk_new, R.string.editor_disk_existing, R.string.editor_disk_none};
            for (int i = 0; i < ids.length; i++) {
                MaterialButton option = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                option.setId(ids[i]);
                option.setText(labels[i]);
                option.setTextSize(12);
                option.setPadding(dp(4), 0, dp(4), 0);
                option.setMinWidth(0);
                option.setMinimumWidth(0);
                option.setMaxLines(2);
                diskSourceButtons.addView(option, new LinearLayout.LayoutParams(0, dp(64), 1));
            }
            storage.addView(diskSourceButtons, matchWrap());
            newDiskOptions = new LinearLayout(this);
            newDiskOptions.setOrientation(LinearLayout.VERTICAL);
            storage.addView(newDiskOptions, matchWrap());
            diskSizeChoice = choice(newDiskOptions, R.string.editor_disk_size, new String[]{"1 GB", "2 GB", "4 GB", "8 GB"},
                    new String[]{"1", "2", "4", "8"}, diskSize);
            existingDiskOptions = new LinearLayout(this);
            existingDiskOptions.setOrientation(LinearLayout.VERTICAL);
            storage.addView(existingDiskOptions, matchWrap());
            mediaSummary(existingDiskOptions, disk, R.string.editor_disk_not_selected);
            button(existingDiskOptions, R.string.editor_choose_disk, R.drawable.ic_library, () -> pick(REQUEST_DISK, "*/*"));
            button(existingDiskOptions, R.string.editor_disk_library, R.drawable.ic_library, this::chooseLibraryDisk);
            detail(existingDiskOptions, R.string.editor_disk_hint);
            diskSourceButtons.check(ids[diskSource]);
            diskSourceButtons.addOnButtonCheckedListener((group, checkedId, checked) -> {
                if (checked) {
                    diskSource = checkedId == ids[0] ? DISK_NEW : checkedId == ids[1] ? DISK_EXISTING : DISK_NONE;
                    updateDiskSource();
                }
            });
            updateDiskSource();
        } else {
            mediaSummary(storage, disk, R.string.editor_disk_not_selected);
            detail(storage, R.string.editor_edit_media_note);
        }

        LinearLayout optical = section(R.string.editor_iso);
        mediaSummary(optical, iso, R.string.editor_iso_empty);
        if (existing == null) {
            button(optical, R.string.editor_choose_iso, R.drawable.ic_library, () -> pick(REQUEST_ISO, "*/*"));
            button(optical, R.string.editor_remove_iso, R.drawable.ic_delete, () -> clearMedia(iso, R.string.editor_iso_empty));
        }
        cdSpeedChoice = choice(optical, R.string.editor_cd_speed, HardwareOptions.CD_SPEED_LABELS,
                strings(HardwareOptions.CD_SPEED_VALUES), String.valueOf(hw.cdromSpeed));

        LinearLayout removable = section(R.string.editor_floppy);
        floppyChoice = choice(removable, R.string.editor_floppy_type, getResources().getStringArray(R.array.editor_floppy_labels), FLOPPY_TYPES, hw.floppyType);
        mediaSummary(removable, floppy, R.string.editor_floppy_empty);
        if (existing == null) {
            button(removable, R.string.editor_choose_floppy, R.drawable.ic_library, () -> pick(REQUEST_FLOPPY, "*/*"));
            button(removable, R.string.editor_remove_floppy, R.drawable.ic_delete, () -> clearMedia(floppy, R.string.editor_floppy_empty));
            detail(removable, R.string.editor_floppy_hint);
        }
        floppyProtectSwitch = toggle(removable, R.string.editor_floppy_protect, hw.floppyWriteProtect);
        floppyTurboSwitch = toggle(removable, R.string.editor_floppy_turbo, hw.floppyTurbo);
        floppyBpbSwitch = toggle(removable, R.string.editor_floppy_bpb, hw.floppyCheckBpb);

        LinearLayout advanced = section(R.string.editor_advanced);
        detail(advanced, R.string.editor_config_hint);
        button(advanced, R.string.editor_edit_config, R.drawable.ic_edit, this::editConfiguration);
        button(advanced, R.string.editor_import_config, R.drawable.ic_library, () -> pick(REQUEST_CONFIG, "*/*"));
        detail(advanced, R.string.editor_network_note);
        if (existing == null) detail(advanced, R.string.editor_import_note);
        formBaseline = hw.generateConfigText();
        setEnabled(form, !model.isSaving());
    }

    private void updateDiskSource() {
        newDiskOptions.setVisibility(diskSource == DISK_NEW ? View.VISIBLE : View.GONE);
        existingDiskOptions.setVisibility(diskSource == DISK_EXISTING ? View.VISIBLE : View.GONE);
    }

    private void pick(int request, String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, request);
    }

    @Override protected void onActivityResult(int request, int result, @Nullable Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri selected = data.getData();
        try {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                getContentResolver().takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        if (request == REQUEST_CONFIG) {
            model.loadConfiguration(selected);
        } else if (request == REQUEST_ICON) {
            selectedIcon = selected;
            showIcon(selected);
        } else {
            MediaSelection media = request == REQUEST_DISK ? disk : request == REQUEST_ISO ? iso : request == REQUEST_FLOPPY ? floppy : null;
            if (media == null) return;
            media.uri = selected;
            media.path = "";
            media.name = displayName(selected);
            media.summary.setText(media.name);
            media.summary.setTextColor(color(com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        }
    }

    private String displayName(Uri selected) {
        try (Cursor cursor = getContentResolver().query(selected, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
        } catch (RuntimeException ignored) { }
        return getString(R.string.editor_selected_file);
    }

    private void chooseLibraryDisk() {
        List<File> files = store.media(true);
        if (files.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.editor_disk_library)
                    .setMessage(R.string.editor_library_empty).setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++)
            labels[i] = files.get(i).getName() + " · " + Formatter.formatFileSize(this, files.get(i).length());
        new MaterialAlertDialogBuilder(this).setTitle(R.string.editor_disk_library).setItems(labels, (dialog, which) -> {
            disk.uri = null;
            disk.path = files.get(which).getAbsolutePath();
            disk.name = files.get(which).getName();
            disk.summary.setText(disk.name);
            disk.summary.setTextColor(color(com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void clearMedia(MediaSelection media, int emptyText) {
        media.uri = null;
        media.path = "";
        media.name = "";
        media.summary.setText(emptyText);
    }

    private void showIcon(@Nullable Uri uri) {
        try {
            if (uri != null) {
                iconPreview.setPadding(0, 0, 0, 0);
                iconPreview.setImageTintList(null);
                iconPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iconPreview.setImageURI(uri);
                return;
            }
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.editor_artwork_error, Toast.LENGTH_SHORT).show();
        }
        iconPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconPreview.setPadding(dp(48), dp(32), dp(48), dp(32));
        iconPreview.setImageResource(R.drawable.ic_computer);
        iconPreview.setImageTintList(ColorStateList.valueOf(color(androidx.appcompat.R.attr.colorPrimary, Color.CYAN)));
    }

    private void readHardware() {
        hw.machine = machineChoice.value();
        hw.cpuType = cpuChoice.value();
        if (cpuChoice.selected < HardwareOptions.CPU_FAMILIES.length) hw.cpuFamily = HardwareOptions.CPU_FAMILIES[cpuChoice.selected];
        hw.cpuSpeedHz = Long.parseLong(speedChoice.value());
        try { hw.memSizeMb = Integer.parseInt(String.valueOf(ramField.getText()).trim()); } catch (NumberFormatException ignored) { }
        hw.videoCard = videoChoice.value();
        hw.hasVoodoo = voodooSwitch.isChecked();
        hw.soundCard = soundChoice.value();
        hw.midiDevice = midiChoice.value();
        hw.networkType = networkChoice.value();
        hw.networkCard = networkCardChoice.value();
        hw.mouseType = mouseChoice.value();
        hw.timeSync = clockChoice.value();
        hw.hddSounds = hddSoundsSwitch.isChecked();
        hw.cpuDynarec = dynarecSwitch.isChecked();
        hw.fpuSoftfloat = softfloatSwitch.isChecked();
        hw.cdromSpeed = Integer.parseInt(cdSpeedChoice.value());
        hw.floppyType = floppyChoice.value();
        hw.floppyWriteProtect = floppyProtectSwitch.isChecked();
        hw.floppyTurbo = floppyTurboSwitch.isChecked();
        hw.floppyCheckBpb = floppyBpbSwitch.isChecked();
        hw.hddPath = existing != null || diskSource == DISK_EXISTING ? disk.path : "";
        hw.cdromPath = iso.path;
        hw.floppyPath = floppy.path;
        if (diskSizeChoice != null) diskSize = diskSizeChoice.value();
    }

    private String configDraft() {
        readHardware();
        return MachineConfiguration.mergeForm(baseConfig, formBaseline, hw.generateConfigText());
    }

    private void editConfiguration() {
        String draft = configDraft();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        detail(content, R.string.editor_config_dialog_hint);
        TextInputLayout box = new TextInputLayout(this);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setHint("86box.cfg");
        TextInputEditText field = new TextInputEditText(box.getContext());
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setTypeface(Typeface.MONOSPACE);
        field.setTextSize(12);
        field.setMinLines(8);
        field.setMaxLines(16);
        field.setHorizontallyScrolling(true);
        field.setTextDirection(View.TEXT_DIRECTION_LTR);
        field.setText(draft);
        box.addView(field, matchWrap());
        content.addView(box, matchWrap());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.editor_edit_config).setView(content)
                .setPositiveButton(R.string.editor_apply, null).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String config = field.getText() == null ? "" : field.getText().toString();
            try {
                MachineStore.validateConfig(config);
                applyConfiguration(config);
                dialog.dismiss();
            } catch (Exception error) { box.setError(error.getMessage()); }
        }));
        dialog.show();
    }

    private void applyConfiguration(String config) {
        String name = String.valueOf(nameField.getText());
        baseConfig = config;
        hw = new HardwareConfig();
        hw.parseFromConfig(baseConfig);
        if (disk.uri == null) {
            disk.path = hw.hddPath;
            disk.name = "";
            if (!disk.path.isEmpty()) diskSource = DISK_EXISTING;
            else if (diskSource == DISK_EXISTING) diskSource = DISK_NONE;
        }
        if (iso.uri == null) { iso.path = hw.cdromPath; iso.name = ""; }
        if (floppy.uri == null) { floppy.path = hw.floppyPath; floppy.name = ""; }
        buildForm(name, String.valueOf(hw.memSizeMb));
    }

    private void save() {
        nameBox.setError(null);
        ramBox.setError(null);
        String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
        if (name.isEmpty() || name.length() > 80 || name.indexOf('\n') >= 0
                || name.indexOf('\r') >= 0 || name.indexOf('\0') >= 0) {
            nameBox.setError(getString(R.string.editor_name_error)); return;
        }
        int ram;
        try { ram = Integer.parseInt(String.valueOf(ramField.getText()).trim()); }
        catch (NumberFormatException error) { ramBox.setError(getString(R.string.editor_memory_hint)); return; }
        if (ram < 4 || ram > 2048) { ramBox.setError(getString(R.string.editor_memory_hint)); return; }
        if (existing == null && diskSource == DISK_EXISTING && disk.uri == null && disk.path.isEmpty()) {
            disk.summary.setText(R.string.editor_disk_required);
            disk.summary.setTextColor(color(androidx.appcompat.R.attr.colorError, Color.RED));
            return;
        }
        if ("none".equals(floppyChoice.value()) && (floppy.uri != null || !floppy.path.isEmpty())) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.editor_floppy_type_required)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        String config = configDraft();
        model.save(existing == null ? null : existing.id, name, config,
                existing == null && diskSource == DISK_NEW ? Integer.parseInt(diskSize) : 0,
                existing == null && diskSource == DISK_EXISTING ? disk.uri : null,
                existing == null ? iso.uri : null, existing == null ? floppy.uri : null, selectedIcon);
    }

    private void renderSaveState(MachineEditorViewModel.SaveState state) {
        setEnabled(form, !state.busy);
        saveButton.setEnabled(!state.busy);
        saveButton.setText(state.busy ? R.string.editor_saving : existing == null ? R.string.editor_create : R.string.editor_save);
        progressText.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        if (state.busy) {
            boolean indeterminate = state.total <= 0;
            if (progress.isIndeterminate() != indeterminate) {
                progress.setVisibility(View.INVISIBLE);
                progress.setIndeterminate(indeterminate);
            }
            if (state.total > 0) {
                int percent = (int) Math.min(100, state.copied * 100 / state.total);
                progress.setProgressCompat(percent, true);
                progressText.setText(getString(R.string.editor_copying_percent, percent));
            } else progressText.setText(R.string.editor_saving_files);
        }
        progress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        if (!state.busy && state.configuration != null) {
            model.dismissError();
            applyConfiguration(state.configuration);
        } else if (!state.busy && state.error != null) {
            model.dismissError();
            new MaterialAlertDialogBuilder(this).setTitle(R.string.editor_save_failed)
                    .setMessage(state.error).setPositiveButton(android.R.string.ok, null).show();
        } else if (!state.busy && state.machine != null) {
            Toast.makeText(this, getString(R.string.editor_saved, state.machine.name), Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }
    }

    private void leave() {
        if (model.isSaving()) Toast.makeText(this, R.string.editor_wait, Toast.LENGTH_SHORT).show();
        else finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (hw == null || nameField == null) return;
        state.putString("draft_config", configDraft());
        state.putString("draft_name", String.valueOf(nameField.getText()));
        state.putString("draft_ram", String.valueOf(ramField.getText()));
        state.putInt("disk_source", diskSource);
        state.putString("disk_size", diskSize);
        state.putString("icon_uri", selectedIcon == null ? null : selectedIcon.toString());
        disk.save(state, "disk"); iso.save(state, "iso"); floppy.save(state, "floppy");
    }

    private LinearLayout section(int title) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(24));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow, Color.DKGRAY));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        MaterialTextView heading = new MaterialTextView(this);
        heading.setText(title);
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        heading.setTextColor(color(androidx.appcompat.R.attr.colorPrimary, Color.CYAN));
        LinearLayout.LayoutParams headingParams = matchWrap();
        headingParams.bottomMargin = dp(12);
        body.addView(heading, headingParams);
        card.addView(body, matchWrap());
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(16);
        form.addView(card, params);
        return body;
    }

    private TextInputEditText input(LinearLayout parent, int hint, String value, boolean number) {
        TextInputLayout box = new TextInputLayout(this);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setHint(getString(hint));
        TextInputEditText field = new TextInputEditText(box.getContext());
        field.setSingleLine(true);
        field.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        field.setText(value);
        box.addView(field, matchWrap());
        addField(parent, box);
        return field;
    }

    private Choice choice(LinearLayout parent, int hint, String[] labels, String[] values, String value) {
        List<String> display = new ArrayList<>(Arrays.asList(labels));
        List<String> choices = new ArrayList<>(Arrays.asList(values));
        int selected = choices.indexOf(value);
        if (selected < 0) {
            selected = choices.size();
            choices.add(value);
            display.add(getString(R.string.editor_custom_value, value));
        }
        TextInputLayout box = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        box.setHint(getString(hint));
        MaterialAutoCompleteTextView view = new MaterialAutoCompleteTextView(box.getContext());
        view.setInputType(InputType.TYPE_NULL);
        view.setKeyListener(null);
        view.setSimpleItems(display.toArray(new String[0]));
        view.setText(display.get(selected), false);
        Choice choice = new Choice(choices, selected);
        view.setOnItemClickListener((adapter, item, position, id) -> choice.selected = position);
        box.addView(view, matchWrap());
        addField(parent, box);
        return choice;
    }

    private MaterialSwitch toggle(LinearLayout parent, int label, boolean checked) {
        MaterialSwitch view = new MaterialSwitch(this);
        view.setText(label);
        view.setChecked(checked);
        view.setMinHeight(dp(56));
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        parent.addView(view, matchWrap());
        return view;
    }

    private void button(LinearLayout parent, int label, int icon, Runnable action) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setIconResource(icon);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        parent.addView(button, params);
    }

    private void addField(LinearLayout parent, View view) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(12);
        parent.addView(view, params);
    }

    private void detail(LinearLayout parent, int text) {
        MaterialTextView view = new MaterialTextView(this);
        view.setText(text);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        view.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY));
        LinearLayout.LayoutParams params = matchWrap(); params.topMargin = dp(8); params.bottomMargin = dp(4);
        parent.addView(view, params);
    }

    private void mediaSummary(LinearLayout parent, MediaSelection media, int empty) {
        media.summary = new MaterialTextView(this);
        media.summary.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        media.summary.setTextColor(color(com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        media.summary.setText(!media.name.isEmpty() ? media.name : !media.path.isEmpty() ? new File(media.path).getName() : getString(empty));
        LinearLayout.LayoutParams params = matchWrap(); params.topMargin = dp(8); params.bottomMargin = dp(8);
        parent.addView(media.summary, params);
    }

    private static void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEnabled(group.getChildAt(i), enabled);
        }
    }

    private static String[] strings(long[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = String.valueOf(values[i]);
        return result;
    }
    private static String[] strings(int[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = String.valueOf(values[i]);
        return result;
    }
    private static Uri uri(String value) { return value == null ? null : Uri.parse(value); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private int color(int attr, int fallback) { return MaterialColors.getColor(this, attr, fallback); }

    private static final class Choice {
        final List<String> values;
        int selected;
        Choice(List<String> values, int selected) { this.values = values; this.selected = selected; }
        String value() { return values.get(selected); }
    }

    private static final class MediaSelection {
        Uri uri;
        String path = "", name = "";
        MaterialTextView summary;
        void restore(Bundle state, String key) {
            uri = MachineEditorActivity.uri(state.getString(key + "_uri"));
            path = state.getString(key + "_path", path);
            name = state.getString(key + "_name", "");
        }
        void save(Bundle state, String key) {
            state.putString(key + "_uri", uri == null ? null : uri.toString());
            state.putString(key + "_path", path);
            state.putString(key + "_name", name);
        }
    }
}
