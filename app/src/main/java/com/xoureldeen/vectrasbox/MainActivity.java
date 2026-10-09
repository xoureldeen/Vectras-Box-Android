package com.xoureldeen.vectrasbox;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.xoureldeen.vectrasbox.data.MachineStore;
import com.xoureldeen.vectrasbox.data.MachineStore.Machine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_ISO = 101;
    private static final int REQUEST_PICK_DISK = 102;
    private static final int REQUEST_PICK_ROMS = 103;
    private static final int REQUEST_EXPORT_FILE = 104;
    private static final int REQUEST_PICK_VM_FILE = 105;
    private static final int REQUEST_PICK_CFG_FOR_CREATE = 106;
    private static final int REQUEST_PICK_NVR_ZIP_OR_FILE = 107;
    private static final int REQUEST_PICK_NVR_TREE = 108;
    private static final int REQUEST_PICK_HDD_FOR_CFG = 109;
    private static final int REQUEST_PICK_ISO_FOR_CFG = 110;
    private static final int REQUEST_PICK_MACHINE_ICON = 111;
    private static final int REQUEST_PICK_ASSETS = 112;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private MachineStore store;
    private DrawerLayout drawerLayout;
    private LinearLayout contentContainer;
    private SearchBar searchBar;
    private SearchView searchView;
    private GridLayout searchResultsLayout;
    private View searchResultsContainer;
    private View searchEmptyView;
    private ExtendedFloatingActionButton createMachineButton;
    private boolean isBusy = false;
    private String pendingExportPath = null;
    private String activeVmIdForFilePick = null;
    private String activeVmIdForNvr = null;
    private String searchQuery = "";
    private Consumer<Uri> pendingIconPicker;

    private HardwareConfig stagedImportedConfig = null;
    private String stagedImportedMachineName = "Imported PC";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new MachineStore(this);
        if (!store.isSetupComplete()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_home);

        View root = findViewById(R.id.home_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        contentContainer = findViewById(R.id.home_content);
        createMachineButton = findViewById(R.id.create_machine);
        createMachineButton.setOnClickListener(v -> showCreateMachineDialog(null, null));

        MaterialToolbar toolbar = findViewById(R.id.home_toolbar);
        searchBar = findViewById(R.id.home_search_bar);
        searchView = findViewById(R.id.home_search_view);
        searchResultsLayout = findViewById(R.id.home_search_results);
        searchResultsContainer = findViewById(R.id.home_search_results_container);
        searchEmptyView = findViewById(R.id.home_search_empty);
        searchResultsLayout.setColumnCount(getMachineGridColumns());
        searchResultsLayout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        searchResultsLayout.setUseDefaultMargins(false);
        searchView.setupWithSearchBar(searchBar);
        searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                renderSearchResults();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        drawerLayout = findViewById(R.id.home_drawer);
        NavigationView navigation = findViewById(R.id.home_navigation);
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        TextView drawerVersion = navigation.getHeaderView(0).findViewById(R.id.drawer_version);
        drawerVersion.setText(getString(R.string.app_version_format, readAppVersionName()));
        navigation.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_about) {
                startActivity(new Intent(this, AboutActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchView.isShowing()) {
                    searchView.hide();
                } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        searchBar.getMenu().add("Import ROMs ZIP").setShowAsAction(0);
        searchBar.getMenu().add("Import assets ZIP").setShowAsAction(0);
        searchBar.getMenu().add("Help").setShowAsAction(0);
        searchBar.setOnMenuItemClickListener(item -> {
            if ("Import ROMs ZIP".contentEquals(item.getTitle())) {
                launchMediaPicker(REQUEST_PICK_ROMS);
            } else if ("Import assets ZIP".contentEquals(item.getTitle())) {
                launchMediaPicker(REQUEST_PICK_ASSETS);
            } else {
                showUserGuideDialog();
            }
            return true;
        });

        if (savedInstanceState != null) {
            pendingExportPath = savedInstanceState.getString("export_path");
            activeVmIdForFilePick = savedInstanceState.getString("active_vm_id");
            activeVmIdForNvr = savedInstanceState.getString("active_vm_nvr");
            searchQuery = savedInstanceState.getString("search_query", "");
        }

        if (!searchQuery.isEmpty()) {
            searchView.getEditText().setText(searchQuery);
            searchView.getEditText().setSelection(searchQuery.length());
        }

        refreshScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (contentContainer != null && !isBusy) {
            refreshScreen();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("export_path", pendingExportPath);
        outState.putString("active_vm_id", activeVmIdForFilePick);
        outState.putString("active_vm_nvr", activeVmIdForNvr);
        outState.putString("search_query", searchQuery);
        super.onSaveInstanceState(outState);
    }

    private String readAppVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "1.0.0" : info.versionName;
        } catch (Exception ignored) {
            return "1.0.0";
        }
    }

    private void refreshScreen() {
        contentContainer.removeAllViews();
        createMachineButton.setVisibility(View.VISIBLE);

        try {
            renderMachinesTab();
            renderSearchResults();
        } catch (Exception error) {
            renderErrorView("Failed to load section", error);
        }
    }

    private void renderMachinesTab() throws IOException {
        if (isEmulatorRunning()) {
            MaterialCardView runningCard = createCard(contentContainer);
            LinearLayout layout = createCardContent(runningCard);
            addCardTitle(layout, "Machine running");
            addCardText(layout, "Tap resume to return to the display.");
            addFilledButton(layout, "Resume", this::resumeActiveMachine);
        }

        GridLayout machinesListLayout = new GridLayout(this);
        machinesListLayout.setColumnCount(getMachineGridColumns());
        machinesListLayout.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        machinesListLayout.setUseDefaultMargins(false);
        contentContainer.addView(machinesListLayout, new LinearLayout.LayoutParams(-1, -2));

        List<Machine> machines = store.list();
        renderMachineCards(machinesListLayout, machines, "", true);
    }

    private void renderSearchResults() {
        if (searchResultsLayout == null || store == null) return;
        try {
            int matches = renderMachineCards(searchResultsLayout, store.list(), searchQuery, false);
            searchEmptyView.setVisibility(matches == 0 ? View.VISIBLE : View.GONE);
            searchResultsContainer.setVisibility(matches == 0 ? View.GONE : View.VISIBLE);
        } catch (IOException error) {
            searchEmptyView.setVisibility(View.GONE);
            searchResultsContainer.setVisibility(View.VISIBLE);
            searchResultsLayout.removeAllViews();
            TextView message = new TextView(this);
            message.setText("Unable to search machines");
            message.setPadding(dp(24), dp(48), dp(24), dp(48));
            searchResultsLayout.addView(message);
        }
    }

    private int renderMachineCards(GridLayout listLayout, List<Machine> machines, String query,
                                   boolean showInlineEmpty) {
        listLayout.removeAllViews();
        int matchingCount = 0;

        for (Machine machine : machines) {
            if (!machine.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                continue;
            }
            matchingCount++;

            HardwareConfig hw = loadHardwareConfig(machine);

            MaterialCardView card = (MaterialCardView) getLayoutInflater()
                    .inflate(R.layout.item_machine, listLayout, false);
            listLayout.addView(card, createMachineCardLayoutParams(listLayout));
            card.setOnClickListener(view -> launchMachine(machine.id));

            ImageView machineIcon = card.findViewById(R.id.machine_icon);
            if (machine.iconFile.isFile()) {
                machineIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                machineIcon.setImageURI(Uri.fromFile(machine.iconFile));
            } else {
                machineIcon.setImageResource(R.drawable.ic_computer_180dp_with_padding);
            }

            ((TextView) card.findViewById(R.id.machine_name)).setText(machine.name);
            ((TextView) card.findViewById(R.id.machine_arch))
                    .setText("x86 • " + hw.memSizeMb + " MB RAM");

            ImageButton more = card.findViewById(R.id.machine_options);
            more.setOnClickListener(view -> showMachineQuickActions(machine.id));
        }

        if (matchingCount == 0 && showInlineEmpty) {
            LinearLayout emptyContent = new LinearLayout(this);
            emptyContent.setOrientation(LinearLayout.VERTICAL);
            emptyContent.setGravity(Gravity.CENTER);
            emptyContent.setPadding(dp(24), dp(56), dp(24), dp(56));
            GridLayout.LayoutParams emptyParams = new GridLayout.LayoutParams();
            emptyParams.width = 0;
            emptyParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
            emptyParams.columnSpec = GridLayout.spec(0, listLayout.getColumnCount(), 1f);
            listLayout.addView(emptyContent, emptyParams);
            addCardTitle(emptyContent, machines.isEmpty() ? "No machines yet" : "No matches");
            addCardText(emptyContent, machines.isEmpty()
                    ? "Create a PC or import an existing 86box.cfg."
                    : "Try a different machine name.");
        }
        return matchingCount;
    }

    private int getMachineGridColumns() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 840) return 4;
        if (widthDp >= 600) return 3;
        return 2;
    }

    private ImageView addMachineIconPicker(LinearLayout form, @Nullable File currentIcon,
                                            Consumer<Uri> onSelected) {
        addSectionDivider(form, "Machine artwork");

        ImageView preview = new ImageView(this);
        preview.setBackgroundColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainer, Color.DKGRAY));
        if (currentIcon != null && currentIcon.isFile()) {
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setImageURI(Uri.fromFile(currentIcon));
        } else {
            preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            preview.setPadding(dp(52), dp(32), dp(52), dp(32));
            preview.setImageResource(R.drawable.ic_computer);
            preview.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(this,
                    androidx.appcompat.R.attr.colorPrimary, Color.CYAN)));
        }
        MaterialButton choose = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        choose.setText(currentIcon != null && currentIcon.isFile() ? "Change machine image" : "Choose machine image");
        choose.setIconResource(android.R.drawable.ic_menu_gallery);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, -2);
        form.addView(choose, chooseParams);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(112));
        previewParams.topMargin = dp(8);
        form.addView(preview, previewParams);
        choose.setOnClickListener(v -> launchMachineIconPicker(uri -> {
            preview.setPadding(0, 0, 0, 0);
            preview.setImageTintList(null);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setImageURI(uri);
            onSelected.accept(uri);
            choose.setText("Change machine image");
        }));
        addCardDetail(form, "Shown as the large cover on the Home grid. PNG, JPG and WEBP are supported.");
        return preview;
    }

    private void launchMachineIconPicker(Consumer<Uri> callback) {
        pendingIconPicker = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_MACHINE_ICON);
        } catch (Exception error) {
            pendingIconPicker = null;
            showErrorDialog(new IOException("No image picker is available on this device."));
        }
    }

    private void showCreateMachineDialog(@Nullable String prefilledName, @Nullable HardwareConfig prefilledHw) {
        if (!ensureEmulatorStopped()) return;

        if (!isFinishing()) {
            Intent editor = new Intent(this, MachineEditorActivity.class);
            if (prefilledName != null) editor.putExtra(MachineEditorActivity.EXTRA_NAME, prefilledName);
            if (prefilledHw != null) editor.putExtra(MachineEditorActivity.EXTRA_CONFIG, prefilledHw.generateConfigText());
            startActivity(editor);
            return;
        }

        HardwareConfig hw = (prefilledHw != null) ? prefilledHw : new HardwareConfig();
        String initialName = (prefilledName != null && !prefilledName.isEmpty()) ? prefilledName : "My Vectras Box PC";

        LinearLayout form = createFormLayout();

        final Uri[] chosenIcon = {null};
        addMachineIconPicker(form, null, uri -> chosenIcon[0] = uri);

        MaterialButton importCfgBtn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        importCfgBtn.setText("Import from 86box.cfg");
        importCfgBtn.setIconResource(android.R.drawable.ic_menu_upload);
        importCfgBtn.setOnClickListener(v -> launchPickCfgForCreate());
        form.addView(importCfgBtn, new LinearLayout.LayoutParams(-1, -2));

        addSectionDivider(form, "General & Architecture");
        TextInputEditText nameField = addInputField(form, "Machine Name", initialName);

        Spinner mbSpinner = addLabeledSpinner(form, "Motherboard / Architecture",
                HardwareOptions.MACHINE_LABELS, HardwareOptions.findMachineIndex(hw.machine));

        addSectionDivider(form, "Processor & Memory");
        Spinner cpuSpinner = addLabeledSpinner(form, "Processor (CPU)",
                HardwareOptions.CPU_LABELS, HardwareOptions.findCpuIndex(hw.cpuType));

        Spinner speedSpinner = addLabeledSpinner(form, "CPU Speed",
                HardwareOptions.SPEED_LABELS, HardwareOptions.findSpeedIndex(hw.cpuSpeedHz));

        Spinner ramSpinner = addLabeledSpinner(form, "Memory (RAM)",
                HardwareOptions.RAM_LABELS, HardwareOptions.findRamIndex(hw.memSizeMb));

        addSectionDivider(form, "Display & Graphics (PCBox 7.0-dev)");
        Spinner videoSpinner = addLabeledSpinner(form, "Video Chipset",
                HardwareOptions.VIDEO_LABELS, HardwareOptions.findVideoIndex(hw.videoCard));

        CheckBox voodooCheck = new CheckBox(this);
        voodooCheck.setText("Enable 3dfx Voodoo Graphics Accelerator");
        voodooCheck.setChecked(hw.hasVoodoo);
        form.addView(voodooCheck);

        addSectionDivider(form, "Audio & MIDI (PCBox 7.0-dev)");
        Spinner soundSpinner = addLabeledSpinner(form, "Sound Card",
                HardwareOptions.SOUND_LABELS, HardwareOptions.findSoundIndex(hw.soundCard));

        Spinner midiSpinner = addLabeledSpinner(form, "MIDI Synthesizer",
                HardwareOptions.MIDI_LABELS, HardwareOptions.findMidiIndex(hw.midiDevice));

        addSectionDivider(form, "Input & System");
        Spinner mouseSpinner = addLabeledSpinner(form, "Mouse Device",
                HardwareOptions.MOUSE_LABELS, HardwareOptions.findMouseIndex(hw.mouseType));

        CheckBox timeSyncCheck = new CheckBox(this);
        timeSyncCheck.setText("Sync RTC Time with Phone (PCBox 7.0-dev)");
        timeSyncCheck.setChecked("local".equalsIgnoreCase(hw.timeSync));
        form.addView(timeSyncCheck);

        addSectionDivider(form, "Storage Devices (PCBox 7.0-dev)");
        CheckBox hddSoundsCheck = new CheckBox(this);
        hddSoundsCheck.setText("Emulate Mechanical Hard Disk Sounds");
        hddSoundsCheck.setChecked(hw.hddSounds);
        form.addView(hddSoundsCheck);

        Spinner cdSpeedSpinner = addLabeledSpinner(form, "CD-ROM Speed",
                HardwareOptions.CD_SPEED_LABELS, HardwareOptions.findCdSpeedIndex(hw.cdromSpeed));

        int defaultDiskOption = 3;
        String[] diskOptions;
        if (!hw.hddPath.isEmpty()) {
            diskOptions = new String[]{"Imported: " + getFileName(hw.hddPath), "No hard disk", "New 1 GB Disk", "New 2 GB Disk", "New 4 GB Disk", "New 8 GB Disk", "New 16 GB Disk"};
            defaultDiskOption = 0;
        } else {
            diskOptions = new String[]{"No hard disk", "New 1 GB Disk", "New 2 GB Disk", "New 4 GB Disk", "New 8 GB Disk", "New 16 GB Disk"};
        }
        Spinner diskSpinner = addLabeledSpinner(form, "Primary Hard Disk", diskOptions, defaultDiskOption);

        if (!hw.cdromPath.isEmpty()) {
            addCardDetail(form, "Attached CD-ROM: " + getFileName(hw.cdromPath));
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Create Virtual Machine")
                .setView(wrapInScrollView(form))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create Machine", null)
                .create();

        dialog.setOnShowListener(d -> {
            MaterialButton createBtn = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            createBtn.setOnClickListener(v -> {
                String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
                if (name.isEmpty()) {
                    nameField.setError("Please enter a machine name");
                    return;
                }

                hw.machine = HardwareOptions.MACHINE_VALUES[mbSpinner.getSelectedItemPosition()];
                hw.cpuType = HardwareOptions.CPU_VALUES[cpuSpinner.getSelectedItemPosition()];
                hw.cpuFamily = HardwareOptions.CPU_FAMILIES[cpuSpinner.getSelectedItemPosition()];
                hw.cpuSpeedHz = HardwareOptions.SPEED_VALUES[speedSpinner.getSelectedItemPosition()];
                hw.memSizeMb = HardwareOptions.RAM_VALUES[ramSpinner.getSelectedItemPosition()];
                hw.videoCard = HardwareOptions.VIDEO_VALUES[videoSpinner.getSelectedItemPosition()];
                hw.hasVoodoo = voodooCheck.isChecked();
                hw.soundCard = HardwareOptions.SOUND_VALUES[soundSpinner.getSelectedItemPosition()];
                hw.midiDevice = HardwareOptions.MIDI_VALUES[midiSpinner.getSelectedItemPosition()];
                hw.mouseType = HardwareOptions.MOUSE_VALUES[mouseSpinner.getSelectedItemPosition()];
                hw.timeSync = timeSyncCheck.isChecked() ? "local" : "disabled";
                hw.hddSounds = hddSoundsCheck.isChecked();
                hw.cdromSpeed = HardwareOptions.CD_SPEED_VALUES[cdSpeedSpinner.getSelectedItemPosition()];

                int diskGb = 0;
                int selectedDiskPos = diskSpinner.getSelectedItemPosition();
                if (!hw.hddPath.isEmpty()) {
                    if (selectedDiskPos == 1) hw.hddPath = "";
                    else if (selectedDiskPos == 2) diskGb = 1;
                    else if (selectedDiskPos == 3) diskGb = 2;
                    else if (selectedDiskPos == 4) diskGb = 4;
                    else if (selectedDiskPos == 5) diskGb = 8;
                    else if (selectedDiskPos == 6) diskGb = 16;
                } else {
                    if (selectedDiskPos == 1) diskGb = 1;
                    else if (selectedDiskPos == 2) diskGb = 2;
                    else if (selectedDiskPos == 3) diskGb = 4;
                    else if (selectedDiskPos == 4) diskGb = 8;
                    else if (selectedDiskPos == 5) diskGb = 16;
                }

                final int finalDiskGb = diskGb;
                dialog.dismiss();

                runBackgroundTask("Creating Virtual Machine", progress -> {
                    Machine created = createMachineInternal(name, hw, finalDiskGb);
                    if (chosenIcon[0] != null) {
                        store.setIcon(created.id, chosenIcon[0]);
                        created = store.get(created.id);
                    }
                    return created;
                }, machine -> {
                    showToast("Machine '" + machine.name + "' created successfully");
                    showHardwareEditorDialog(machine.id);
                });
            });
        });

        dialog.show();
    }

    private void showHardwareEditorDialog(String machineId) {
        if (!ensureEmulatorStopped()) return;

        if (!isFinishing()) {
            Intent editor = new Intent(this, MachineEditorActivity.class);
            editor.putExtra(MachineEditorActivity.EXTRA_MACHINE_ID, machineId);
            startActivity(editor);
            return;
        }

        try {
            Machine machine = store.get(machineId);
            if (machine == null) {
                showErrorDialog(new IOException("Machine not found"));
                return;
            }

            HardwareConfig hw = loadHardwareConfig(machine);

            LinearLayout form = createFormLayout();

            final Uri[] chosenIcon = {null};
            addMachineIconPicker(form, machine.iconFile.isFile() ? machine.iconFile : null,
                    uri -> chosenIcon[0] = uri);

            addSectionDivider(form, "General & Architecture");
            TextInputEditText nameField = addInputField(form, "Machine Name", machine.name);

            int selectedMb = HardwareOptions.findMachineIndex(hw.machine);
            Spinner mbSpinner = addLabeledSpinner(form, "Motherboard / Architecture",
                    HardwareOptions.MACHINE_LABELS, selectedMb);

            addSectionDivider(form, "Processor & Memory");
            int selectedCpu = HardwareOptions.findCpuIndex(hw.cpuType);
            Spinner cpuSpinner = addLabeledSpinner(form, "Processor (CPU)",
                    HardwareOptions.CPU_LABELS, selectedCpu);

            int selectedSpeed = HardwareOptions.findSpeedIndex(hw.cpuSpeedHz);
            Spinner speedSpinner = addLabeledSpinner(form, "CPU Speed",
                    HardwareOptions.SPEED_LABELS, selectedSpeed);

            int selectedRam = HardwareOptions.findRamIndex(hw.memSizeMb);
            Spinner ramSpinner = addLabeledSpinner(form, "Memory (RAM)",
                    HardwareOptions.RAM_LABELS, selectedRam);

            TextInputEditText customRamField = addInputField(form, "Custom RAM (MB)", String.valueOf(hw.memSizeMb));
            customRamField.setInputType(InputType.TYPE_CLASS_NUMBER);

            addSectionDivider(form, "Display & Graphics (PCBox 7.0-dev)");
            int selectedVideo = HardwareOptions.findVideoIndex(hw.videoCard);
            Spinner videoSpinner = addLabeledSpinner(form, "Video Chipset",
                    HardwareOptions.VIDEO_LABELS, selectedVideo);

            CheckBox voodooCheck = new CheckBox(this);
            voodooCheck.setText("Enable 3dfx Voodoo Graphics Accelerator");
            voodooCheck.setChecked(hw.hasVoodoo);
            form.addView(voodooCheck);

            addSectionDivider(form, "Audio & MIDI (PCBox 7.0-dev)");
            int selectedSound = HardwareOptions.findSoundIndex(hw.soundCard);
            Spinner soundSpinner = addLabeledSpinner(form, "Sound Card",
                    HardwareOptions.SOUND_LABELS, selectedSound);

            int selectedMidi = HardwareOptions.findMidiIndex(hw.midiDevice);
            Spinner midiSpinner = addLabeledSpinner(form, "MIDI Synthesizer",
                    HardwareOptions.MIDI_LABELS, selectedMidi);

            addSectionDivider(form, "Input & System");
            int selectedMouse = HardwareOptions.findMouseIndex(hw.mouseType);
            Spinner mouseSpinner = addLabeledSpinner(form, "Mouse Device",
                    HardwareOptions.MOUSE_LABELS, selectedMouse);

            CheckBox timeSyncCheck = new CheckBox(this);
            timeSyncCheck.setText("Sync RTC Time with Phone (PCBox 7.0-dev)");
            timeSyncCheck.setChecked("local".equalsIgnoreCase(hw.timeSync));
            form.addView(timeSyncCheck);

            addSectionDivider(form, "Storage Devices (PCBox 7.0-dev)");
            CheckBox hddSoundsCheck = new CheckBox(this);
            hddSoundsCheck.setText("Emulate Mechanical Hard Disk Sounds");
            hddSoundsCheck.setChecked(hw.hddSounds);
            form.addView(hddSoundsCheck);

            int selectedCdSpeed = HardwareOptions.findCdSpeedIndex(hw.cdromSpeed);
            Spinner cdSpeedSpinner = addLabeledSpinner(form, "CD-ROM Speed",
                    HardwareOptions.CD_SPEED_LABELS, selectedCdSpeed);

            List<String> diskPaths = getAvailableDiskPaths(machine);
            List<String> diskDisplayNames = formatPathDisplayNames(diskPaths, "No Hard Disk Attached");
            int selectedDiskIndex = Math.max(0, diskPaths.indexOf(hw.hddPath));
            Spinner diskSpinner = addLabeledSpinner(form, "Primary Hard Disk (Drive C:)",
                    diskDisplayNames.toArray(new String[0]), selectedDiskIndex);

            List<String> isoPaths = getAvailableIsoPaths(machine);
            List<String> isoDisplayNames = formatPathDisplayNames(isoPaths, "Empty / Ejected");
            int selectedIsoIndex = Math.max(0, isoPaths.indexOf(hw.cdromPath));
            Spinner isoSpinner = addLabeledSpinner(form, "CD-ROM Image (Drive D:)",
                    isoDisplayNames.toArray(new String[0]), selectedIsoIndex);

            List<String> floppyPaths = getAvailableDiskPaths(machine);
            List<String> floppyDisplayNames = formatPathDisplayNames(floppyPaths, "Empty / Ejected");
            int selectedFloppyIndex = Math.max(0, floppyPaths.indexOf(hw.floppyPath));
            Spinner floppySpinner = addLabeledSpinner(form, "Floppy Disk (Drive A:)",
                    floppyDisplayNames.toArray(new String[0]), selectedFloppyIndex);

            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("Edit Hardware: " + machine.name)
                    .setView(wrapInScrollView(form))
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("VM Files", (d, w) -> showVmFilesManagerDialog(machineId))
                    .setPositiveButton("Save Hardware", null)
                    .create();

            dialog.setOnShowListener(d -> {
                MaterialButton saveBtn = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                saveBtn.setOnClickListener(v -> {
                    String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameField.setError("Name cannot be empty");
                        return;
                    }

                    int ramVal = hw.memSizeMb;
                    try {
                        String customRamStr = customRamField.getText() == null ? "" : customRamField.getText().toString().trim();
                        if (!customRamStr.isEmpty()) {
                            ramVal = Integer.parseInt(customRamStr);
                        } else {
                            ramVal = HardwareOptions.RAM_VALUES[ramSpinner.getSelectedItemPosition()];
                        }
                        if (ramVal < 4 || ramVal > 2048) {
                            customRamField.setError("RAM must be between 4 and 2048 MB");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        customRamField.setError("Invalid RAM number");
                        return;
                    }

                    hw.machine = HardwareOptions.MACHINE_VALUES[mbSpinner.getSelectedItemPosition()];
                    hw.cpuType = HardwareOptions.CPU_VALUES[cpuSpinner.getSelectedItemPosition()];
                    hw.cpuFamily = HardwareOptions.CPU_FAMILIES[cpuSpinner.getSelectedItemPosition()];
                    hw.cpuSpeedHz = HardwareOptions.SPEED_VALUES[speedSpinner.getSelectedItemPosition()];
                    hw.memSizeMb = ramVal;
                    hw.videoCard = HardwareOptions.VIDEO_VALUES[videoSpinner.getSelectedItemPosition()];
                    hw.hasVoodoo = voodooCheck.isChecked();
                    hw.soundCard = HardwareOptions.SOUND_VALUES[soundSpinner.getSelectedItemPosition()];
                    hw.midiDevice = HardwareOptions.MIDI_VALUES[midiSpinner.getSelectedItemPosition()];
                    hw.mouseType = HardwareOptions.MOUSE_VALUES[mouseSpinner.getSelectedItemPosition()];
                    hw.timeSync = timeSyncCheck.isChecked() ? "local" : "disabled";
                    hw.hddSounds = hddSoundsCheck.isChecked();
                    hw.cdromSpeed = HardwareOptions.CD_SPEED_VALUES[cdSpeedSpinner.getSelectedItemPosition()];

                    int diskPos = diskSpinner.getSelectedItemPosition();
                    hw.hddPath = (diskPos >= 0 && diskPos < diskPaths.size()) ? diskPaths.get(diskPos) : "";

                    int isoPos = isoSpinner.getSelectedItemPosition();
                    hw.cdromPath = (isoPos >= 0 && isoPos < isoPaths.size()) ? isoPaths.get(isoPos) : "";

                    int floppyPos = floppySpinner.getSelectedItemPosition();
                    hw.floppyPath = (floppyPos >= 0 && floppyPos < floppyPaths.size()) ? floppyPaths.get(floppyPos) : "";

                    dialog.dismiss();

                    runBackgroundTask("Saving Hardware Configuration", progress -> {
                        saveHardwareConfig(machineId, name, hw);
                        if (chosenIcon[0] != null) store.setIcon(machineId, chosenIcon[0]);
                        return machineId;
                    }, id -> {
                        showToast("Hardware updated for " + name);
                        refreshScreen();
                    });
                });
            });

            dialog.show();

        } catch (Exception error) {
            showErrorDialog(error);
        }
    }

    private void launchPickCfgForCreate() {
        if (!ensureEmulatorStopped()) return;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivityForResult(intent, REQUEST_PICK_CFG_FOR_CREATE);
        } catch (Exception e) {
            showErrorDialog(new IOException("No document picker available"));
        }
    }

    private void handleImportedCfgUri(Uri uri) {
        runBackgroundTask("Reading 86box.cfg", progress -> {
            StringBuilder sb = new StringBuilder();
            try (InputStream in = getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }

            String cfgContent = sb.toString();
            HardwareConfig hw = new HardwareConfig();
            hw.parseFromConfig(cfgContent);

            String machineName = "Imported Vectras Box PC";
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0) {
                        String rawName = cursor.getString(nameIdx);
                        if (rawName != null && rawName.toLowerCase(Locale.ROOT).endsWith(".cfg")) {
                            rawName = rawName.substring(0, rawName.length() - 4);
                        }
                        if (rawName != null && !rawName.trim().isEmpty()) {
                            machineName = rawName.trim();
                        }
                    }
                }
            } catch (Exception ignored) {}

            stagedImportedConfig = hw;
            stagedImportedMachineName = machineName;
            return hw;
        }, hw -> {
            boolean hasHddInCfg = hw.hddPath != null && !hw.hddPath.isEmpty();
            boolean hasIsoInCfg = hw.cdromPath != null && !hw.cdromPath.isEmpty();

            if (hasHddInCfg || hasIsoInCfg) {
                showMediaResolutionDialog(hasHddInCfg, hasIsoInCfg);
            } else {
                showCreateMachineDialog(stagedImportedMachineName, stagedImportedConfig);
            }
        });
    }

    private void showMediaResolutionDialog(boolean hasHdd, boolean hasIso) {
        StringBuilder msg = new StringBuilder("The imported 86box.cfg references the following disk images from another system:\n\n");
        if (hasHdd) {
            msg.append("• Hard Disk: ").append(getFileName(stagedImportedConfig.hddPath)).append("\n");
        }
        if (hasIso) {
            msg.append("• CD-ROM ISO: ").append(getFileName(stagedImportedConfig.cdromPath)).append("\n");
        }
        msg.append("\nWould you like to select the replacement file(s) from your device now?");

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Media in Configuration")
                .setMessage(msg.toString())
                .setCancelable(false);

        if (hasHdd) {
            builder.setPositiveButton("Select Hard Disk", (dialog, which) -> {
                launchPickHddForImportedCfg();
            });
            builder.setNegativeButton(hasIso ? "Select CD-ROM ISO" : "Skip / Later", (dialog, which) -> {
                if (hasIso) {
                    launchPickIsoForImportedCfg();
                } else {
                    showCreateMachineDialog(stagedImportedMachineName, stagedImportedConfig);
                }
            });
            if (hasIso) {
                builder.setNeutralButton("Skip", (dialog, which) -> {
                    showCreateMachineDialog(stagedImportedMachineName, stagedImportedConfig);
                });
            }
        } else {
            builder.setPositiveButton("Select CD-ROM ISO", (dialog, which) -> {
                launchPickIsoForImportedCfg();
            });
            builder.setNegativeButton("Skip / Later", (dialog, which) -> {
                showCreateMachineDialog(stagedImportedMachineName, stagedImportedConfig);
            });
        }

        builder.show();
    }

    private void launchPickHddForImportedCfg() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_HDD_FOR_CFG);
        } catch (Exception e) {
            showErrorDialog(e);
        }
    }

    private void launchPickIsoForImportedCfg() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_ISO_FOR_CFG);
        } catch (Exception e) {
            showErrorDialog(e);
        }
    }

    private void showVmFilesManagerDialog(String machineId) {
        if (!ensureEmulatorStopped()) return;

        try {
            Machine machine = store.get(machineId);
            if (machine == null) {
                showErrorDialog(new IOException("Machine not found"));
                return;
            }

            File vmDir = machine.directory;
            if (!vmDir.exists()) vmDir.mkdirs();
            File nvrDir = new File(vmDir, "nvr");
            if (!nvrDir.exists()) nvrDir.mkdirs();

            LinearLayout container = createFormLayout();

            addCardTitle(container, "VM Folder: " + machine.name);
            addCardDetail(container, "Path: " + vmDir.getAbsolutePath());

            LinearLayout topActions = createHorizontalContainer(container);
            addFilledButton(topActions, "Copy File to VM", () -> launchFilePickerForVm(machineId));
            addOutlinedButton(topActions, "Import NVR Folder", () -> showImportNvrChooserDialog(machineId));

            File[] nvrFiles = nvrDir.listFiles();
            int nvrCount = (nvrFiles != null) ? nvrFiles.length : 0;
            addCardSubtitle(container, "NVR (BIOS/CMOS): " + nvrCount + " file(s) present");

            addSectionDivider(container, "Files in VM Directory");

            File[] files = vmDir.listFiles();
            if (files == null || files.length == 0) {
                addCardText(container, "No files in VM directory yet.");
            } else {
                Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));

                for (File file : files) {
                    if (file.getName().startsWith(".")) continue;

                    MaterialCardView fileCard = createCard(container);
                    LinearLayout fileRow = createCardContent(fileCard);

                    TextView fileNameText = new TextView(this);
                    fileNameText.setText(file.getName() + (file.isDirectory() ? " [DIR]" : ""));
                    fileNameText.setTypeface(null, Typeface.BOLD);
                    fileNameText.setTextSize(15);
                    fileRow.addView(fileNameText);

                    if (file.isFile()) {
                        addCardDetail(fileRow, Formatter.formatFileSize(this, file.length()));
                    } else if (file.isDirectory() && "nvr".equalsIgnoreCase(file.getName())) {
                        addCardDetail(fileRow, "CMOS & BIOS settings directory (" + nvrCount + " files)");
                    }

                    LinearLayout fileActions = createHorizontalContainer(fileRow);

                    if (file.isFile()) {
                        String lower = file.getName().toLowerCase(Locale.ROOT);
                        if (lower.endsWith(".img") || lower.endsWith(".ima") || lower.endsWith(".raw")) {
                            addOutlinedButton(fileActions, "Use as HDD", () -> setVmHardDisk(machineId, file.getAbsolutePath()));
                            addOutlinedButton(fileActions, "Use as Floppy", () -> setVmFloppy(machineId, file.getAbsolutePath()));
                        } else if (lower.endsWith(".iso") || lower.endsWith(".cue")) {
                            addOutlinedButton(fileActions, "Mount CD", () -> setVmCdrom(machineId, file.getAbsolutePath()));
                        }

                        addOutlinedButton(fileActions, "Export", () -> requestFileExport(file));

                        if (!file.getName().equals("86box.cfg")) {
                            addOutlinedButton(fileActions, "Delete", () -> confirmDeleteVmFile(machineId, file));
                        }
                    }
                }
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("VM Directory Manager")
                    .setView(wrapInScrollView(container))
                    .setPositiveButton("Done", null)
                    .show();

        } catch (Exception error) {
            showErrorDialog(error);
        }
    }

    private void showImportNvrChooserDialog(String machineId) {
        if (!ensureEmulatorStopped()) return;

        activeVmIdForNvr = machineId;

        String[] options = new String[]{
                "Select NVR Directory / Folder (SAF Tree)",
                "Import NVR ZIP Archive or .nvr File"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Import NVR / CMOS Data")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchPickNvrTree();
                    } else {
                        launchPickNvrZipOrFile();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchPickNvrTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_NVR_TREE);
        } catch (Exception e) {
            showErrorDialog(new IOException("Document tree picker not supported on this device"));
        }
    }

    private void launchPickNvrZipOrFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_NVR_ZIP_OR_FILE);
        } catch (Exception e) {
            showErrorDialog(e);
        }
    }

    private void importNvrTreeInternal(String machineId, Uri treeUri) throws IOException {
        Machine machine = store.get(machineId);
        if (machine == null) throw new IOException("Machine not found");

        File nvrDir = new File(machine.directory, "nvr");
        if (!nvrDir.exists()) nvrDir.mkdirs();

        String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);

        int count = 0;
        try (Cursor cursor = getContentResolver().query(childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String docId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        Uri subChildren = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
                        try (Cursor subCursor = getContentResolver().query(subChildren,
                                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                                null, null, null)) {
                            if (subCursor != null) {
                                while (subCursor.moveToNext()) {
                                    String subDocId = subCursor.getString(0);
                                    String subName = subCursor.getString(1);
                                    copyDocumentUriToFile(treeUri, subDocId, new File(nvrDir, subName));
                                    count++;
                                }
                            }
                        }
                    } else {
                        copyDocumentUriToFile(treeUri, docId, new File(nvrDir, name));
                        count++;
                    }
                }
            }
        }

        final int finalCount = count;
        runOnUiThread(() -> {
            showToast("Imported " + finalCount + " NVR files into VM");
            showVmFilesManagerDialog(machineId);
        });
    }

    private void copyDocumentUriToFile(Uri treeUri, String docId, File targetFile) throws IOException {
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = getContentResolver().openInputStream(docUri);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            if (in == null) return;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }
    }

    private void importNvrZipOrFileInternal(String machineId, Uri uri) throws IOException {
        Machine machine = store.get(machineId);
        if (machine == null) throw new IOException("Machine not found");

        File nvrDir = new File(machine.directory, "nvr");
        if (!nvrDir.exists()) nvrDir.mkdirs();

        String displayName = "nvr_data";
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx);
            }
        } catch (Exception ignored) {}

        int count = 0;
        if (displayName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                byte[] buffer = new byte[64 * 1024];
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                    String simpleName = (lastSlash >= 0) ? name.substring(lastSlash + 1) : name;
                    if (simpleName.isEmpty()) continue;

                    File target = new File(nvrDir, simpleName);
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        out.getFD().sync();
                    }
                    count++;
                    zip.closeEntry();
                }
            }
        } else {
            File target = new File(nvrDir, displayName);
            byte[] buffer = new byte[64 * 1024];
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(target)) {
                if (in != null) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.getFD().sync();
                    count++;
                }
            }
        }

        final int finalCount = count;
        runOnUiThread(() -> {
            showToast("Imported " + finalCount + " NVR files into VM");
            showVmFilesManagerDialog(machineId);
        });
    }

    private void launchFilePickerForVm(String machineId) {
        if (!ensureEmulatorStopped()) return;

        activeVmIdForFilePick = machineId;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivityForResult(intent, REQUEST_PICK_VM_FILE);
        } catch (Exception e) {
            showErrorDialog(new IOException("No file picker app found on this device"));
        }
    }

    private void confirmDeleteVmFile(String machineId, File file) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete File?")
                .setMessage("Are you sure you want to delete '" + file.getName() + "' from this VM's folder?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (file.delete()) {
                        showToast("Deleted " + file.getName());
                        showVmFilesManagerDialog(machineId);
                    } else {
                        showToast("Could not delete file");
                    }
                })
                .show();
    }

    private void setVmHardDisk(String machineId, String path) {
        runBackgroundTask("Attaching Hard Disk", progress -> {
            Machine machine = store.get(machineId);
            HardwareConfig hw = loadHardwareConfig(machine);
            hw.hddPath = path;
            saveHardwareConfig(machineId, machine.name, hw);
            return machine.name;
        }, name -> {
            showToast("Set as primary hard disk for " + name);
            refreshScreen();
        });
    }

    private void setVmCdrom(String machineId, String path) {
        runBackgroundTask("Inserting CD-ROM", progress -> {
            Machine machine = store.get(machineId);
            HardwareConfig hw = loadHardwareConfig(machine);
            hw.cdromPath = path;
            saveHardwareConfig(machineId, machine.name, hw);
            return machine.name;
        }, name -> {
            showToast("CD-ROM inserted for " + name);
            refreshScreen();
        });
    }

    private void setVmFloppy(String machineId, String path) {
        runBackgroundTask("Inserting Floppy", progress -> {
            Machine machine = store.get(machineId);
            HardwareConfig hw = loadHardwareConfig(machine);
            hw.floppyPath = path;
            saveHardwareConfig(machineId, machine.name, hw);
            return machine.name;
        }, name -> {
            showToast("Floppy disk inserted for " + name);
            refreshScreen();
        });
    }

    private void showMachineQuickActions(String machineId) {
        try {
            Machine machine = store.get(machineId);
            if (machine == null) return;

            BottomSheetDialog sheet = new BottomSheetDialog(this);
            View content = getLayoutInflater().inflate(R.layout.dialog_machine_actions, null);
            ((TextView) content.findViewById(R.id.machine_actions_title)).setText(machine.name);

            content.findViewById(R.id.machine_action_edit).setOnClickListener(view -> {
                sheet.dismiss();
                showHardwareEditorDialog(machineId);
            });
            content.findViewById(R.id.machine_action_more).setOnClickListener(view -> {
                sheet.dismiss();
                showMachineOptionsMenu(machineId);
            });
            content.findViewById(R.id.machine_action_delete).setOnClickListener(view -> {
                sheet.dismiss();
                confirmDeleteMachine(machineId);
            });

            sheet.setContentView(content);
            sheet.show();
        } catch (Exception error) {
            showErrorDialog(error);
        }
    }

    private void showMachineOptionsMenu(String machineId) {
        String[] options = new String[]{
                "Edit Hardware (PCBox 7.0-dev)",
                "VM Files & Storage",
                "Import NVR (BIOS/CMOS Data)",
                "Clone / Duplicate Machine",
                "Rename Machine",
                "Raw 86box.cfg Editor",
                "Export Hard Disk",
                "Export Configuration",
                "Delete Machine"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Machine Options")
                .setItems(options, (dialog, which) -> {
                    try {
                        switch (which) {
                            case 0:
                                showHardwareEditorDialog(machineId);
                                break;
                            case 1:
                                showVmFilesManagerDialog(machineId);
                                break;
                            case 2:
                                showImportNvrChooserDialog(machineId);
                                break;
                            case 3:
                                cloneMachine(machineId);
                                break;
                            case 4:
                                showRenameMachineDialog(machineId);
                                break;
                            case 5:
                                showRawConfigEditor(machineId);
                                break;
                            case 6: {
                                Machine machine = store.get(machineId);
                                if (machine == null || machine.diskPath.isEmpty()) {
                                    throw new IOException("No hard disk attached to this machine");
                                }
                                requestFileExport(new File(machine.diskPath));
                                break;
                            }
                            case 7: {
                                Machine machine = store.get(machineId);
                                if (machine != null && machine.configFile.isFile()) {
                                    requestFileExport(machine.configFile);
                                }
                                break;
                            }
                            case 8:
                                confirmDeleteMachine(machineId);
                                break;
                        }
                    } catch (Exception error) {
                        showErrorDialog(error);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showRenameMachineDialog(String machineId) throws IOException {
        Machine machine = store.get(machineId);
        if (machine == null) return;

        LinearLayout form = createFormLayout();
        TextInputEditText input = addInputField(form, "New Name", machine.name);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename Machine")
                .setView(wrapInScrollView(form))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = input.getText() == null ? "" : input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        showToast("Name cannot be empty");
                        return;
                    }
                    runBackgroundTask("Renaming Machine", p -> {
                        store.rename(machineId, newName);
                        return newName;
                    }, res -> {
                        showToast("Renamed to " + res);
                        refreshScreen();
                    });
                })
                .show();
    }

    private void cloneMachine(String machineId) {
        if (!ensureEmulatorStopped()) return;

        runBackgroundTask("Cloning Machine", progress -> {
            Machine source = store.get(machineId);
            if (source == null) throw new IOException("Machine does not exist");

            String newId = "vm-" + UUID.randomUUID().toString();
            File newDir = new File(store.root(), "machines/" + newId);
            newDir.mkdirs();

            String config = store.config(machineId);
            File newCfg = new File(newDir, "86box.cfg");
            writeTextToFile(newCfg, config);

            String cloneName = source.name + " (Copy)";
            writeTextToFile(new File(newDir, ".vectras-name"), cloneName);

            if (source.iconFile.isFile()) {
                copySingleFile(source.iconFile, new File(newDir, ".vectras-icon"));
            }

            File sourceNvr = new File(source.directory, "nvr");
            if (sourceNvr.isDirectory()) {
                File targetNvr = new File(newDir, "nvr");
                targetNvr.mkdirs();
                File[] nvrFiles = sourceNvr.listFiles();
                if (nvrFiles != null) {
                    for (File f : nvrFiles) {
                        copySingleFile(f, new File(targetNvr, f.getName()));
                    }
                }
            }

            return cloneName;
        }, name -> {
            showToast("Cloned: " + name);
            refreshScreen();
        });
    }

    private void confirmDeleteMachine(String machineId) {
        if (!ensureEmulatorStopped()) return;

        try {
            Machine machine = store.get(machineId);
            if (machine == null) return;

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Virtual Machine?")
                    .setMessage("Are you sure you want to delete '" + machine.name + "'? Its VM folder, configuration, NVR, and local VM files will be permanently deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete Permanently", (dialog, which) -> {
                        runBackgroundTask("Deleting Machine", p -> {
                            deleteDirectoryRecursive(machine.directory);
                            return machine.name;
                        }, name -> {
                            showToast("Deleted " + name);
                            refreshScreen();
                        });
                    })
                    .show();
        } catch (Exception error) {
            showErrorDialog(error);
        }
    }

    private void showRawConfigEditor(String machineId) {
        if (!ensureEmulatorStopped()) return;

        try {
            LinearLayout form = createFormLayout();
            addCardDetail(form, "Advanced PCBox configuration (86box.cfg). Changes take effect on next machine start.");

            TextInputEditText configInput = addInputField(form, "86box.cfg", store.config(machineId));
            configInput.setSingleLine(false);
            configInput.setMinLines(14);
            configInput.setTypeface(Typeface.MONOSPACE);
            configInput.setTextSize(12);

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Configuration File")
                    .setView(wrapInScrollView(form))
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String text = configInput.getText() == null ? "" : configInput.getText().toString();
                        runBackgroundTask("Saving Configuration", p -> {
                            store.saveConfig(machineId, text);
                            return machineId;
                        }, res -> {
                            showToast("Configuration saved");
                            refreshScreen();
                        });
                    })
                    .show();
        } catch (Exception error) {
            showErrorDialog(error);
        }
    }

    private void renderMediaTab() {
        addHeading(contentContainer, "Media Library", "Universal CD-ROM and hard disk images available to any machine.");

        LinearLayout actionsRow = createHorizontalContainer(contentContainer);
        addFilledButton(actionsRow, "Import ISO", () -> launchMediaPicker(REQUEST_PICK_ISO));
        addFilledButton(actionsRow, "Import Disk", () -> launchMediaPicker(REQUEST_PICK_DISK));

        addOutlinedButton(contentContainer, "Create Empty Hard Disk", this::showCreateHardDiskDialog);

        addSectionDivider(contentContainer, "CD / DVD Images (ISO)");
        renderMediaItems(false);

        addSectionDivider(contentContainer, "Hard Disk Images (IMG / RAW)");
        renderMediaItems(true);

        addCardDetail(contentContainer, "Tip: You can also copy files directly into an individual VM's directory from the Machines tab via 'VM Files'.");
    }

    private void renderMediaItems(boolean isDisk) {
        List<File> files = store.media(isDisk);
        if (files.isEmpty()) {
            addCardText(contentContainer, "No " + (isDisk ? "disk" : "ISO") + " images in library yet.");
            return;
        }

        for (File file : files) {
            MaterialCardView card = createCard(contentContainer);
            LinearLayout layout = createCardContent(card);

            addCardTitle(layout, file.getName());
            addCardDetail(layout, Formatter.formatFileSize(this, file.length()));

            LinearLayout actions = createHorizontalContainer(layout);
            addOutlinedButton(actions, "Attach to Machine", () -> showAttachMediaDialog(file, isDisk));
            addOutlinedButton(actions, "Export", () -> requestFileExport(file));
        }
    }

    private void showCreateHardDiskDialog() {
        if (!ensureEmulatorStopped()) return;

        LinearLayout form = createFormLayout();
        TextInputEditText nameField = addInputField(form, "Disk Image Name", "hard-disk");
        Spinner sizeSpinner = addLabeledSpinner(form, "Disk Size",
                new String[]{"512 MB", "1 GB", "2 GB", "4 GB", "8 GB", "16 GB"}, 2);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Create Blank Hard Disk")
                .setView(wrapInScrollView(form))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
                    if (name.isEmpty()) {
                        showToast("Please enter a disk name");
                        return;
                    }
                    int pos = sizeSpinner.getSelectedItemPosition();
                    int sizeGb = (pos == 0) ? 1 : (pos == 1 ? 1 : (pos == 2 ? 2 : (pos == 3 ? 4 : (pos == 4 ? 8 : 16))));

                    runBackgroundTask("Creating Hard Disk", progress -> {
                        return store.createDisk(name, sizeGb);
                    }, file -> {
                        showToast("Created disk: " + file.getName());
                        refreshScreen();
                    });
                })
                .show();
    }

    private void showAttachMediaDialog(File mediaFile, boolean isDisk) {
        if (!ensureEmulatorStopped()) return;

        try {
            List<Machine> machines = store.list();
            if (machines.isEmpty()) {
                showToast("Please create a virtual machine first");
                return;
            }

            String[] names = new String[machines.size()];
            for (int i = 0; i < machines.size(); i++) {
                names[i] = machines.get(i).name;
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Attach to Machine")
                    .setItems(names, (dialog, which) -> {
                        Machine target = machines.get(which);
                        runBackgroundTask("Attaching Media", progress -> {
                            HardwareConfig hw = loadHardwareConfig(target);
                            if (isDisk) {
                                hw.hddPath = mediaFile.getAbsolutePath();
                            } else {
                                hw.cdromPath = mediaFile.getAbsolutePath();
                            }
                            saveHardwareConfig(target.id, target.name, hw);
                            return target.name;
                        }, name -> {
                            showToast("Attached to " + name);
                            refreshScreen();
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        } catch (Exception error) {
            showErrorDialog(error);
        }
    }

    private void renderToolsTab() {
        addHeading(contentContainer, "Tools & Repository", "Manage PCBox ROM sets, phone storage, and emulator options.");

        MaterialCardView romsCard = createCard(contentContainer);
        LinearLayout romsLayout = createCardContent(romsCard);
        addCardTitle(romsLayout, "PCBox ROMs Repository");

        File romsDir = store.romsDir();
        int romCount = countFilesInDirectory(romsDir);
        String romStatus = (romCount > 0)
                ? "Repository ready (" + romCount + " files/folders installed)"
                : "No ROM files installed yet";
        addCardSubtitle(romsLayout, romStatus);
        addCardText(romsLayout, "PCBox requires system BIOS and peripheral ROMs. Import an official PCBox ROM set ZIP archive here.");
        addFilledButton(romsLayout, "Import PCBox ROMs ZIP", () -> launchMediaPicker(REQUEST_PICK_ROMS));

        MaterialCardView assetsCard = createCard(contentContainer);
        LinearLayout assetsLayout = createCardContent(assetsCard);
        addCardTitle(assetsLayout, "PCBox Assets Repository");
        int assetCount = countFilesInDirectory(store.assetsDir());
        addCardSubtitle(assetsLayout, assetCount > 0
                ? "Repository ready (" + assetCount + " files/folders installed)"
                : "No asset files installed yet");
        addCardText(assetsLayout, "Fonts and device sounds from the official PCBox assets archive.");
        addFilledButton(assetsLayout, "Import PCBox Assets ZIP", () -> launchMediaPicker(REQUEST_PICK_ASSETS));

        MaterialCardView storageCard = createCard(contentContainer);
        LinearLayout storageLayout = createCardContent(storageCard);
        addCardTitle(storageLayout, "Internal App Storage");
        long usable = store.root().getUsableSpace();
        addCardSubtitle(storageLayout, Formatter.formatFileSize(this, usable) + " free on phone");
        addCardText(storageLayout, "Virtual machine hard disks, ISOs, and ROMs are stored in app private data.");
        addOutlinedButton(storageLayout, "Create Hard Disk", this::showCreateHardDiskDialog);

        MaterialCardView guideCard = createCard(contentContainer);
        LinearLayout guideLayout = createCardContent(guideCard);
        addCardTitle(guideLayout, "Vectras Box Guide & On-Screen Controls");
        addCardText(guideLayout, "Learn how to import configs, mount NVR data, use hard disk sounds, and navigate on-screen controls.");
        addOutlinedButton(guideLayout, "View User Guide", this::showUserGuideDialog);
    }

    private void showUserGuideDialog() {
        String guideText = "1. Virtual Machine Setup (PCBox 7.0-dev):\n"
                + "Create a machine and configure its CPU, motherboard, RAM, video chipsets, sound card, and hard disk sounds directly in the UI.\n\n"
                + "2. Importing .cfg & Media:\n"
                + "- In 'Create Machine', tap 'Import from 86box.cfg'. If your config references hard disk or ISO images from a PC, the app prompts you to pick replacement files.\n\n"
                + "3. NVR / CMOS BIOS Settings:\n"
                + "- Use 'VM Files' -> 'Import NVR Folder' to import an existing nvr folder or ZIP containing CMOS configuration.\n\n"
                + "4. Controls:\n"
                + "- One-finger tap = Left Click\n"
                + "- Two-finger tap = Right Click\n"
                + "- Two-finger swipe = Mouse Wheel Scroll\n"
                + "- Drag on screen = Mouse movement\n"
                + "- Keyboard button = Open software keyboard\n"
                + "- Edit button = Reposition control buttons on screen\n\n"
                + "5. Safe Shutdown:\n"
                + "Always shut down the guest OS from its Start menu before stopping the emulator.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Vectras Box User Guide")
                .setMessage(guideText)
                .setPositiveButton("Got It", null)
                .show();
    }

    private void launchMachine(String machineId) {
        if (isBusy) return;
        if (isEmulatorRunning()) {
            resumeActiveMachine();
            return;
        }

        runBackgroundTask("Preparing Machine", progress -> {
            store.prepareLaunch(machineId);
            return machineId;
        }, readyId -> {
            getSharedPreferences("launcher", MODE_PRIVATE).edit()
                    .putString("active_machine", readyId).apply();
            Intent intent = new Intent(this, VectrasSDLActivity.class);
            intent.putExtra(VectrasSDLActivity.EXTRA_MACHINE_ID, readyId);
            startActivity(intent);
        });
    }

    private boolean isEmulatorRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return false;
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) {
            String emulatorProcess = getPackageName() + ":emulator";
            for (ActivityManager.RunningAppProcessInfo info : processes) {
                if (emulatorProcess.equals(info.processName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void resumeActiveMachine() {
        String activeId = getSharedPreferences("launcher", MODE_PRIVATE)
                .getString("active_machine", "default");
        Intent intent = new Intent(this, VectrasSDLActivity.class);
        intent.putExtra(VectrasSDLActivity.EXTRA_MACHINE_ID, activeId);
        startActivity(intent);
    }

    private boolean ensureEmulatorStopped() {
        if (isBusy) return false;
        if (!isEmulatorRunning()) return true;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Machine is Running")
                .setMessage("Please stop the active machine from the emulator menu before modifying settings or moving disks.")
                .setPositiveButton("Resume Machine", (d, w) -> resumeActiveMachine())
                .setNegativeButton("Cancel", null)
                .show();
        return false;
    }

    private void launchMediaPicker(int requestCode) {
        if (!ensureEmulatorStopped()) return;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            showErrorDialog(new IOException("No document picker available on this device"));
        }
    }

    private void requestFileExport(File file) {
        if (!ensureEmulatorStopped()) return;
        if (!file.isFile()) {
            showErrorDialog(new IOException("File not found: " + file.getName()));
            return;
        }

        pendingExportPath = file.getAbsolutePath();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, file.getName());

        try {
            startActivityForResult(intent, REQUEST_EXPORT_FILE);
        } catch (Exception e) {
            showErrorDialog(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQUEST_PICK_MACHINE_ICON) {
            Consumer<Uri> callback = pendingIconPicker;
            pendingIconPicker = null;
            if (callback != null) callback.accept(uri);
            return;
        }

        if (!ensureEmulatorStopped()) return;

        if (requestCode == REQUEST_PICK_VM_FILE && activeVmIdForFilePick != null) {
            String targetVmId = activeVmIdForFilePick;
            activeVmIdForFilePick = null;

            runBackgroundTask("Copying File to VM", progress -> {
                return copyUriToVmDirectory(targetVmId, uri, progress);
            }, copiedFile -> {
                showToast("Copied " + copiedFile.getName() + " to VM folder");
                showVmFilesManagerDialog(targetVmId);
                refreshScreen();
            });

        } else if (requestCode == REQUEST_PICK_CFG_FOR_CREATE) {
            handleImportedCfgUri(uri);

        } else if (requestCode == REQUEST_PICK_HDD_FOR_CFG && stagedImportedConfig != null) {
            runBackgroundTask("Importing Hard Disk Image", progress -> {
                return store.importMedia(uri, true, progress);
            }, diskFile -> {
                stagedImportedConfig.hddPath = diskFile.getAbsolutePath();
                showToast("Selected hard disk: " + diskFile.getName());
                if (stagedImportedConfig.cdromPath != null && !stagedImportedConfig.cdromPath.isEmpty() && !new File(stagedImportedConfig.cdromPath).isFile()) {
                    showMediaResolutionDialog(false, true);
                } else {
                    showCreateMachineDialog(stagedImportedMachineName, stagedImportedConfig);
                }
            });

        } else if (requestCode == REQUEST_PICK_ISO_FOR_CFG && stagedImportedConfig != null) {
            runBackgroundTask("Importing CD-ROM ISO", progress -> {
                return store.importMedia(uri, false, progress);
            }, isoFile -> {
                stagedImportedConfig.cdromPath = isoFile.getAbsolutePath();
                showToast("Selected CD-ROM ISO: " + isoFile.getName());
                showCreateMachineDialog(stagedImportedMachineName, stagedImportedConfig);
            });

        } else if (requestCode == REQUEST_PICK_NVR_TREE && activeVmIdForNvr != null) {
            String targetVmId = activeVmIdForNvr;
            activeVmIdForNvr = null;
            runBackgroundTask("Importing NVR Folder", progress -> {
                importNvrTreeInternal(targetVmId, uri);
                return targetVmId;
            }, id -> {
                refreshScreen();
            });

        } else if (requestCode == REQUEST_PICK_NVR_ZIP_OR_FILE && activeVmIdForNvr != null) {
            String targetVmId = activeVmIdForNvr;
            activeVmIdForNvr = null;
            runBackgroundTask("Importing NVR Data", progress -> {
                importNvrZipOrFileInternal(targetVmId, uri);
                return targetVmId;
            }, id -> {
                refreshScreen();
            });

        } else if (requestCode == REQUEST_PICK_ROMS) {
            runBackgroundTask("Importing ROMs Archive", progress -> {
                return store.importRomsZip(uri, progress);
            }, count -> {
                showToast("Imported " + count + " ROM files");
                refreshScreen();
            });

        } else if (requestCode == REQUEST_PICK_ASSETS) {
            runBackgroundTask("Importing Assets Archive", progress -> {
                return store.importAssetsZip(uri, progress);
            }, count -> {
                showToast("Imported " + count + " asset files");
                refreshScreen();
            });

        } else if (requestCode == REQUEST_PICK_DISK || requestCode == REQUEST_PICK_ISO) {
            boolean isDisk = (requestCode == REQUEST_PICK_DISK);
            runBackgroundTask("Importing " + (isDisk ? "Disk" : "ISO"), progress -> {
                return store.importMedia(uri, isDisk, progress);
            }, file -> {
                showToast("Imported: " + file.getName());
                refreshScreen();
            });

        } else if (requestCode == REQUEST_EXPORT_FILE && pendingExportPath != null) {
            File source = new File(pendingExportPath);
            pendingExportPath = null;
            runBackgroundTask("Exporting Backup", progress -> {
                store.exportFile(source, uri, progress);
                return source.getName();
            }, name -> {
                showToast("Exported: " + name);
            });
        }
    }

    private interface TaskJob<T> {
        T execute(MachineStore.Progress progress) throws Exception;
    }

    private interface TaskSuccess<T> {
        void onSuccess(T result);
    }

    private <T> void runBackgroundTask(String title, TaskJob<T> job, TaskSuccess<T> onSuccess) {
        if (isBusy) return;
        isBusy = true;

        LinearLayout body = createFormLayout();
        TextView statusText = new TextView(this);
        statusText.setText("Please keep the app open...");
        statusText.setTextSize(14);
        body.addView(statusText);

        LinearProgressIndicator progressBar = new LinearProgressIndicator(this);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(8));
        progressParams.topMargin = dp(12);
        body.addView(progressBar, progressParams);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(body)
                .setCancelable(false)
                .create();
        dialog.show();

        worker.execute(() -> {
            try {
                long[] lastUpdate = {0};
                T result = job.execute((copied, total) -> {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastUpdate[0] < 200 && copied != total) return;
                    lastUpdate[0] = now;

                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        if (total > 0) {
                            progressBar.setIndeterminate(false);
                            progressBar.setProgress((int) Math.min(100, (copied * 100L) / total));
                            statusText.setText(Formatter.formatFileSize(this, copied) + " / " + Formatter.formatFileSize(this, total));
                        } else {
                            statusText.setText(Formatter.formatFileSize(this, copied) + " copied");
                        }
                    });
                });

                runOnUiThread(() -> {
                    isBusy = false;
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    if (onSuccess != null) onSuccess.onSuccess(result);
                });

            } catch (Exception error) {
                runOnUiThread(() -> {
                    isBusy = false;
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    showErrorDialog(error);
                });
            }
        });
    }

    private Machine createMachineInternal(String name, HardwareConfig hw, int diskGb) throws IOException {
        String id = "vm-" + UUID.randomUUID().toString();
        File vmDir = new File(store.root(), "machines/" + id);
        if (!vmDir.exists() && !vmDir.mkdirs()) {
            throw new IOException("Cannot create VM directory: " + vmDir.getName());
        }

        File createdDisk = null;
        try {
            if (diskGb > 0) {
                createdDisk = store.createDisk(name, diskGb);
                hw.hddPath = createdDisk.getAbsolutePath();
            }

            String cfgText = hw.generateConfigText();
            File cfgFile = new File(vmDir, "86box.cfg");
            writeTextToFile(cfgFile, cfgText);

            File nameFile = new File(vmDir, ".vectras-name");
            writeTextToFile(nameFile, name.trim());

            File nvrDir = new File(vmDir, "nvr");
            if (!nvrDir.exists()) nvrDir.mkdirs();

            return store.get(id);
        } catch (IOException e) {
            if (createdDisk != null) createdDisk.delete();
            deleteDirectoryRecursive(vmDir);
            throw e;
        }
    }

    private HardwareConfig loadHardwareConfig(Machine machine) {
        HardwareConfig hw = new HardwareConfig();
        try {
            String configText = store.config(machine.id);
            hw.parseFromConfig(configText);
            if (machine.diskPath != null && !machine.diskPath.isEmpty()) hw.hddPath = machine.diskPath;
            if (machine.isoPath != null && !machine.isoPath.isEmpty()) hw.cdromPath = machine.isoPath;
            if (machine.ramMb > 0) hw.memSizeMb = machine.ramMb;
        } catch (Exception ignored) {}
        return hw;
    }

    private void saveHardwareConfig(String machineId, String machineName, HardwareConfig hw) throws IOException {
        Machine machine = store.get(machineId);
        if (machine == null) throw new IOException("Machine not found");

        String existingConfig = "";
        try {
            existingConfig = store.config(machineId);
        } catch (Exception ignored) {}

        String updatedConfig = hw.updateExistingConfig(existingConfig);
        store.saveConfig(machineId, updatedConfig);
        store.rename(machineId, machineName);

        try {
            store.update(machineId, hw.memSizeMb, hw.hddPath, hw.cdromPath);
        } catch (Exception ignored) {}
    }

    private File copyUriToVmDirectory(String machineId, Uri uri, MachineStore.Progress progress) throws IOException {
        Machine machine = store.get(machineId);
        if (machine == null) throw new IOException("Target VM not found");

        File vmDir = machine.directory;
        if (!vmDir.exists()) vmDir.mkdirs();

        String displayName = "imported_file";
        long totalSize = -1;

        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) displayName = cursor.getString(nameIdx);
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) totalSize = cursor.getLong(sizeIdx);
            }
        } catch (Exception ignored) {}

        displayName = displayName.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (displayName.isEmpty()) displayName = "file_" + System.currentTimeMillis();

        File targetFile = new File(vmDir, displayName);
        int counter = 1;
        while (targetFile.exists()) {
            int dot = displayName.lastIndexOf('.');
            String base = (dot > 0) ? displayName.substring(0, dot) : displayName;
            String ext = (dot > 0) ? displayName.substring(dot) : "";
            targetFile = new File(vmDir, base + "_" + counter + ext);
            counter++;
        }

        byte[] buffer = new byte[128 * 1024];
        long copied = 0;
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            if (in == null) throw new IOException("Cannot open input stream for selected file");
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                copied += read;
                if (progress != null) progress.onProgress(copied, totalSize);
            }
            out.getFD().sync();
        }

        return targetFile;
    }

    private List<String> getAvailableDiskPaths(Machine machine) {
        List<String> paths = new ArrayList<>();
        paths.add("");

        if (machine != null && machine.directory.isDirectory()) {
            File[] vmFiles = machine.directory.listFiles();
            if (vmFiles != null) {
                for (File f : vmFiles) {
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".img") || name.endsWith(".ima") || name.endsWith(".raw")) {
                        paths.add(f.getAbsolutePath());
                    }
                }
            }
        }

        for (File f : store.media(true)) {
            if (!paths.contains(f.getAbsolutePath())) {
                paths.add(f.getAbsolutePath());
            }
        }

        if (machine != null && machine.diskPath != null && !machine.diskPath.isEmpty() && !paths.contains(machine.diskPath)) {
            paths.add(machine.diskPath);
        }

        return paths;
    }

    private List<String> getAvailableIsoPaths(Machine machine) {
        List<String> paths = new ArrayList<>();
        paths.add("");

        if (machine != null && machine.directory.isDirectory()) {
            File[] vmFiles = machine.directory.listFiles();
            if (vmFiles != null) {
                for (File f : vmFiles) {
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".iso") || name.endsWith(".cue")) {
                        paths.add(f.getAbsolutePath());
                    }
                }
            }
        }

        for (File f : store.media(false)) {
            if (!paths.contains(f.getAbsolutePath())) {
                paths.add(f.getAbsolutePath());
            }
        }

        if (machine != null && machine.isoPath != null && !machine.isoPath.isEmpty() && !paths.contains(machine.isoPath)) {
            paths.add(machine.isoPath);
        }

        return paths;
    }

    private List<String> formatPathDisplayNames(List<String> paths, String emptyLabel) {
        List<String> names = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.isEmpty()) {
                names.add(emptyLabel);
            } else {
                File f = new File(path);
                names.add(f.getName() + " (" + Formatter.formatFileSize(this, f.length()) + ")");
            }
        }
        return names;
    }

    private int countFilesInDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] list = dir.listFiles();
        return list == null ? 0 : list.length;
    }

    private static String getFileName(String path) {
        return (path == null || path.isEmpty()) ? "" : new File(path).getName();
    }

    private static void writeTextToFile(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    private static void copySingleFile(File source, File dest) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new java.io.FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }
    }

    private static void deleteDirectoryRecursive(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteDirectoryRecursive(child);
                else child.delete();
            }
        }
        dir.delete();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout createFormLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(16));
        return layout;
    }

    private ScrollView wrapInScrollView(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(child);
        return scroll;
    }

    private LinearLayout createVerticalContainer(LinearLayout parent) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        parent.addView(layout, new LinearLayout.LayoutParams(-1, -2));
        return layout;
    }

    private LinearLayout createHorizontalContainer(LinearLayout parent) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        parent.addView(layout, params);
        return layout;
    }

    private MaterialCardView createCard(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, Color.DKGRAY));
        card.setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.argb(255, 30, 30, 30)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, dp(6));
        parent.addView(card, params);
        return card;
    }

    private GridLayout.LayoutParams createMachineCardLayoutParams(GridLayout parent) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        int columns = Math.max(1, parent.getColumnCount());
        int availableDp = getResources().getConfiguration().screenWidthDp - 32 - (columns * 8);
        params.width = dp(Math.max(144, availableDp / columns));
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1);
        params.setMargins(dp(4), dp(8), dp(4), dp(4));
        return params;
    }

    private LinearLayout createCardContent(MaterialCardView card) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(layout);
        return layout;
    }

    private void addHeading(LinearLayout parent, String title, String subtitle) {
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(26);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        parent.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = new TextView(this);
            subView.setText(subtitle);
            subView.setTextSize(14);
            subView.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY));
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
            subParams.bottomMargin = dp(12);
            parent.addView(subView, subParams);
        }
    }

    private void addSectionDivider(LinearLayout parent, String title) {
        TextView divider = new TextView(this);
        divider.setText(title);
        divider.setTextSize(17);
        divider.setTypeface(null, Typeface.BOLD);
        divider.setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.CYAN));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(16), 0, dp(6));
        parent.addView(divider, params);
    }

    private void addCardTitle(LinearLayout parent, String title) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(20);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        parent.addView(view);
    }

    private void addCardSubtitle(LinearLayout parent, String subtitle) {
        TextView view = new TextView(this);
        view.setText(subtitle);
        view.setTextSize(14);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.CYAN));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        parent.addView(view, params);
    }

    private void addCardText(LinearLayout parent, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(view, params);
    }

    private void addCardDetail(LinearLayout parent, String detail) {
        TextView view = new TextView(this);
        view.setText(detail);
        view.setTextSize(12);
        view.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        parent.addView(view, params);
    }

    private void addFilledButton(LinearLayout parent, String text, Runnable action) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params;
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(3), dp(4), dp(3), dp(4));
        } else {
            params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(4), 0, dp(4));
        }
        parent.addView(btn, params);
    }

    private void addOutlinedButton(LinearLayout parent, String text, Runnable action) {
        MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params;
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(3), dp(4), dp(3), dp(4));
        } else {
            params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(4), 0, dp(4));
        }
        parent.addView(btn, params);
    }

    private TextInputEditText addInputField(LinearLayout parent, String hint, String initialValue) {
        TextInputLayout wrapper = new TextInputLayout(this);
        wrapper.setHint(hint);
        wrapper.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText input = new TextInputEditText(wrapper.getContext());
        input.setText(initialValue);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(10));
        parent.addView(wrapper, params);
        return input;
    }

    private Spinner addLabeledSpinner(LinearLayout parent, String label, String[] options, int selected) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY));
        parent.addView(labelView);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selected >= 0 && selected < options.length) {
            spinner.setSelection(selected);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.bottomMargin = dp(12);
        parent.addView(spinner, params);
        return spinner;
    }

    private void renderErrorView(String title, Exception error) {
        addHeading(contentContainer, title, error.getMessage());
        addOutlinedButton(contentContainer, "Retry", this::refreshScreen);
    }

    private void showErrorDialog(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static final class HardwareOptions {
        public static final String[] MACHINE_LABELS = {
                "Gigabyte GA-686BX (Slot 1 • Intel 440BX)",
                "ASUS P3V4X (Slot 1 • VIA Apollo Pro 133A)",
                "MSI MS-6117 (Slot 1 • Intel 440LX/BX)",
                "ASUS TXP4-X (Socket 7 • Intel 430TX)",
                "ASUS P/I-P55TVP4 (Socket 7 • Intel 430VX)",
                "Shuttle HOT-557 (Socket 7 • Intel 430TX)",
                "EPoX EP-MVP3G2 (Super Socket 7 • VIA MVP3)",
                "Intel AP440FX (Socket 8 • Pentium Pro)",
                "Acer M3A (Socket 7 • ALi Aladdin IV)",
                "Packard Bell PB640 (Socket 3 • 486)",
                "Shuttle HOT-433 (Socket 3 • 486)",
                "Intel Classic R/R Plus (Socket 2 • 486)",
                "AMI 386 Clone (Socket 132 • 386DX)",
                "IBM PS/2 Model 30-286 (286)",
                "IBM PC/AT 5170 (286)"
        };

        public static final String[] MACHINE_VALUES = {
                "686bx",
                "p35v",
                "ms6117",
                "txp4x",
                "p55tvp4",
                "hot557",
                "ep_mvp3g2",
                "ap440fx",
                "acerm3a",
                "pb640",
                "hot433",
                "classic_r",
                "ami386",
                "ps2_m30_286",
                "ibm_at"
        };

        public static final String[] CPU_LABELS = {
                "Intel Pentium III (Coppermine)",
                "Intel Pentium III (Katmai)",
                "Intel Pentium II (Deschutes)",
                "Intel Pentium II (Klamath)",
                "Intel Celeron (Mendocino)",
                "Intel Pentium Pro",
                "Intel Pentium MMX",
                "Intel Pentium Classic",
                "AMD K6-2",
                "AMD K6",
                "Intel 486DX4",
                "Intel 486DX2",
                "Intel 486DX",
                "AMD Am486DX4",
                "Intel 386DX"
        };

        public static final String[] CPU_VALUES = {
                "pentium3_coppermine",
                "pentium3_katmai",
                "pentium2_deschutes",
                "pentium2_klamath",
                "celeron_mendocino",
                "pentiumpro",
                "pentium_mmx",
                "pentium",
                "k6_2",
                "k6",
                "i486dx4",
                "i486dx2",
                "i486dx",
                "am486dx4",
                "i386dx"
        };

        public static final String[] CPU_FAMILIES = {
                "pentium3",
                "pentium3",
                "pentium2",
                "pentium2",
                "pentium2",
                "pentiumpro",
                "socket7",
                "socket7",
                "k6",
                "k6",
                "i486",
                "i486",
                "i486",
                "i486",
                "i386"
        };

        public static final String[] SPEED_LABELS = {
                "25 MHz", "33 MHz", "50 MHz", "66 MHz", "75 MHz", "100 MHz",
                "120 MHz", "133 MHz", "150 MHz", "166 MHz", "200 MHz", "233 MHz",
                "266 MHz", "300 MHz", "333 MHz", "350 MHz", "400 MHz", "450 MHz",
                "500 MHz", "550 MHz", "600 MHz", "700 MHz", "800 MHz"
        };

        public static final long[] SPEED_VALUES = {
                25000000L, 33333333L, 50000000L, 66666666L, 75000000L, 100000000L,
                120000000L, 133333333L, 150000000L, 166666666L, 200000000L, 233333333L,
                266666666L, 300000000L, 333333333L, 350000000L, 400000000L, 450000000L,
                500000000L, 550000000L, 600000000L, 700000000L, 800000000L
        };

        public static final String[] RAM_LABELS = {
                "16 MB", "32 MB", "64 MB", "128 MB", "192 MB", "256 MB",
                "384 MB", "512 MB", "768 MB", "1024 MB (1 GB)"
        };

        public static final int[] RAM_VALUES = {
                16, 32, 64, 128, 192, 256, 384, 512, 768, 1024
        };

        public static final String[] VIDEO_LABELS = {
                "S3 ViRGE/DX (PCI) [Recommended]",
                "S3 ViRGE/VX (PCI)",
                "S3 Trio64 (PCI)",
                "S3 Trio3D/2X (PCI)",
                "3dfx Voodoo Banshee (PCI)",
                "3dfx Voodoo 3 2000 (PCI)",
                "Cirrus Logic CL-GD5434 (PCI)",
                "Cirrus Logic CL-GD5429 (ISA)",
                "ATI Mach64 GX (PCI)",
                "Tseng Labs ET4000/W32p (PCI)",
                "Tseng Labs ET4000AX (ISA)",
                "Standard VGA (ISA)"
        };

        public static final String[] VIDEO_VALUES = {
                "virge_dx_pci",
                "virge_vx_pci",
                "s3_trio64_pci",
                "trio3d2x",
                "voodoo_banshee_migrated_pci",
                "voodoo3_2k_pci",
                "cl_gd5434_pci",
                "cl_gd5429_isa",
                "mach64gx_pci",
                "et4000w32p_migrated_pci",
                "et4000ax",
                "vga"
        };

        public static final String[] SOUND_LABELS = {
                "Creative Sound Blaster 16 [Recommended]",
                "Creative Sound Blaster Pro",
                "Creative Sound Blaster 2.0",
                "Creative Sound Blaster AWE32",
                "Creative Sound Blaster AWE64",
                "Pro Audio Spectrum 16",
                "ESS AudioDrive ES1868",
                "Aztech Sound Galaxy Pro 16",
                "AdLib",
                "None (Disabled)"
        };

        public static final String[] SOUND_VALUES = {
                "sb16",
                "sbpro",
                "sb2",
                "awe32",
                "awe64",
                "pas16",
                "ess1868",
                "soundgalaxy16",
                "adlib",
                "none"
        };

        public static final String[] MIDI_LABELS = {
                "None / Default",
                "Roland SC-55 Synthesizer (PCBox 7.0-dev)",
                "FluidSynth Software Synth"
        };

        public static final String[] MIDI_VALUES = {
                "none",
                "sc55",
                "fluidsynth"
        };

        public static final String[] CD_SPEED_LABELS = {
                "32X [Recommended]",
                "48X",
                "24X",
                "16X",
                "8X",
                "4X",
                "1X"
        };

        public static final int[] CD_SPEED_VALUES = {
                32, 48, 24, 16, 8, 4, 1
        };

        public static final String[] MOUSE_LABELS = {
                "PS/2 Mouse",
                "Serial Mouse (COM1)",
                "None"
        };

        public static final String[] MOUSE_VALUES = {
                "ps2",
                "serial",
                "none"
        };

        public static int findMachineIndex(String val) {
            for (int i = 0; i < MACHINE_VALUES.length; i++) {
                if (MACHINE_VALUES[i].equalsIgnoreCase(val)) return i;
            }
            return 0;
        }

        public static int findCpuIndex(String val) {
            for (int i = 0; i < CPU_VALUES.length; i++) {
                if (CPU_VALUES[i].equalsIgnoreCase(val)) return i;
            }
            return 2;
        }

        public static int findSpeedIndex(long val) {
            int closest = 0;
            long minDiff = Long.MAX_VALUE;
            for (int i = 0; i < SPEED_VALUES.length; i++) {
                long diff = Math.abs(SPEED_VALUES[i] - val);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = i;
                }
            }
            return closest;
        }

        public static int findRamIndex(int val) {
            int closest = 0;
            int minDiff = Integer.MAX_VALUE;
            for (int i = 0; i < RAM_VALUES.length; i++) {
                int diff = Math.abs(RAM_VALUES[i] - val);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = i;
                }
            }
            return closest;
        }

        public static int findVideoIndex(String val) {
            for (int i = 0; i < VIDEO_VALUES.length; i++) {
                if (VIDEO_VALUES[i].equalsIgnoreCase(val)) return i;
            }
            return 0;
        }

        public static int findSoundIndex(String val) {
            for (int i = 0; i < SOUND_VALUES.length; i++) {
                if (SOUND_VALUES[i].equalsIgnoreCase(val)) return i;
            }
            return 0;
        }

        public static int findMidiIndex(String val) {
            for (int i = 0; i < MIDI_VALUES.length; i++) {
                if (MIDI_VALUES[i].equalsIgnoreCase(val)) return i;
            }
            return 0;
        }

        public static int findCdSpeedIndex(int val) {
            for (int i = 0; i < CD_SPEED_VALUES.length; i++) {
                if (CD_SPEED_VALUES[i] == val) return i;
            }
            return 0;
        }

        public static int findMouseIndex(String val) {
            for (int i = 0; i < MOUSE_VALUES.length; i++) {
                if (MOUSE_VALUES[i].equalsIgnoreCase(val)) return i;
            }
            return 0;
        }
    }

    public static final class HardwareConfig {
        public String machine = "686bx";
        public String cpuFamily = "pentium2";
        public String cpuType = "pentium2_deschutes";
        public long cpuSpeedHz = 400000000L;
        public String cpuMulti = "4.0x";
        public int memSizeMb = 128;
        public String videoCard = "virge_dx_pci";
        public boolean hasVoodoo = false;
        public String soundCard = "sb16";
        public String midiDevice = "none";
        public String networkCard = "none";
        public String networkType = "none";
        public String mouseType = "ps2";

        public boolean hddSounds = true;
        public int cdromSpeed = 32;
        public String timeSync = "local";

        public String hddPath = "";
        public String cdromPath = "";
        public String floppyPath = "";
        public String floppyType = "35_2hd";
        public boolean floppyWriteProtect = false;
        public boolean floppyTurbo = false;
        public boolean floppyCheckBpb = true;
        public boolean cpuDynarec = true;
        public boolean fpuSoftfloat = false;

        public String getDisplayMachineName() {
            int idx = HardwareOptions.findMachineIndex(machine);
            return HardwareOptions.MACHINE_LABELS[idx];
        }

        public String getDisplayCpuSummary() {
            int idx = HardwareOptions.findCpuIndex(cpuType);
            String name = HardwareOptions.CPU_LABELS[idx];
            long mhz = Math.round(cpuSpeedHz / 1000000.0);
            return name + " " + mhz + " MHz";
        }

        public String getDisplayVideoName() {
            int idx = HardwareOptions.findVideoIndex(videoCard);
            return HardwareOptions.VIDEO_LABELS[idx] + (hasVoodoo ? " + Voodoo" : "");
        }

        public String getDisplaySoundName() {
            int idx = HardwareOptions.findSoundIndex(soundCard);
            String base = HardwareOptions.SOUND_LABELS[idx];
            if (!"none".equalsIgnoreCase(midiDevice) && midiDevice != null) {
                base += " (" + midiDevice.toUpperCase(Locale.ROOT) + ")";
            }
            return base;
        }

        public void parseFromConfig(String configText) {
            if (configText == null || configText.isEmpty()) return;
            String currentSection = "";

            for (String rawLine : configText.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).trim();
                } else if (!line.startsWith(";") && !line.startsWith("#") && line.contains("=")) {
                    int eq = line.indexOf('=');
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();

                    if ("Machine".equalsIgnoreCase(currentSection)) {
                        if ("machine".equalsIgnoreCase(key)) machine = val;
                        else if ("cpu_family".equalsIgnoreCase(key)) cpuFamily = val;
                        else if ("cpu_type".equalsIgnoreCase(key)) cpuType = val;
                        else if ("cpu_speed".equalsIgnoreCase(key)) {
                            try { cpuSpeedHz = Long.parseLong(val); } catch (NumberFormatException ignored) {}
                        } else if ("cpu_multi".equalsIgnoreCase(key)) cpuMulti = val;
                        else if ("mem_size".equalsIgnoreCase(key)) {
                            try { memSizeMb = Integer.parseInt(val) / 1024; } catch (NumberFormatException ignored) {}
                        } else if ("time_sync".equalsIgnoreCase(key)) {
                            timeSync = val;
                        } else if ("cpu_use_dynarec".equalsIgnoreCase(key)) cpuDynarec = "1".equals(val);
                        else if ("fpu_softfloat".equalsIgnoreCase(key)) fpuSoftfloat = "1".equals(val);
                    } else if ("Video".equalsIgnoreCase(currentSection)) {
                        if ("gfxcard".equalsIgnoreCase(key)) videoCard = val;
                        else if ("voodoo".equalsIgnoreCase(key)) hasVoodoo = "1".equals(val);
                    } else if ("Sound".equalsIgnoreCase(currentSection)) {
                        if ("sndcard".equalsIgnoreCase(key)) soundCard = val;
                        else if ("midi_device".equalsIgnoreCase(key)) midiDevice = val;
                    } else if ("Network".equalsIgnoreCase(currentSection)) {
                        if ("net_01_card".equalsIgnoreCase(key)) networkCard = val;
                        else if ("net_01_net_type".equalsIgnoreCase(key)) networkType = val;
                    } else if ("Input devices".equalsIgnoreCase(currentSection)) {
                        if ("mouse_type".equalsIgnoreCase(key)) mouseType = val;
                    } else if ("Hard disks".equalsIgnoreCase(currentSection)) {
                        if ("hdd_01_fn".equalsIgnoreCase(key)) hddPath = val;
                        else if ("hdd_sounds".equalsIgnoreCase(key)) hddSounds = "1".equals(val);
                    } else if ("Floppy and CD-ROM drives".equalsIgnoreCase(currentSection)) {
                        if ("cdrom_01_image_path".equalsIgnoreCase(key)) cdromPath = val;
                        else if ("cdrom_01_speed".equalsIgnoreCase(key)) {
                            try { cdromSpeed = Integer.parseInt(val); } catch (NumberFormatException ignored) {}
                        } else if ("fdd_01_fn".equalsIgnoreCase(key)) floppyPath = val;
                        else if ("fdd_01_type".equalsIgnoreCase(key)) floppyType = val;
                        else if ("fdd_01_writeprot".equalsIgnoreCase(key)) floppyWriteProtect = "1".equals(val);
                        else if ("fdd_01_turbo".equalsIgnoreCase(key)) floppyTurbo = "1".equals(val);
                        else if ("fdd_01_check_bpb".equalsIgnoreCase(key)) floppyCheckBpb = "1".equals(val);
                    }
                }
            }
        }

        public String generateConfigText() {
            StringBuilder sb = new StringBuilder();
            sb.append("[General]\n");
            sb.append("vid_renderer = qt\n\n");

            sb.append("[Machine]\n");
            sb.append("machine = ").append(machine).append("\n");
            if (cpuFamily != null && !cpuFamily.isEmpty()) sb.append("cpu_family = ").append(cpuFamily).append("\n");
            if (cpuType != null && !cpuType.isEmpty()) sb.append("cpu_type = ").append(cpuType).append("\n");
            if (cpuSpeedHz > 0) sb.append("cpu_speed = ").append(cpuSpeedHz).append("\n");
            if (cpuMulti != null && !cpuMulti.isEmpty()) sb.append("cpu_multi = ").append(cpuMulti).append("\n");
            sb.append("cpu_use_dynarec = ").append(cpuDynarec ? "1" : "0").append("\n");
            sb.append("fpu_softfloat = ").append(fpuSoftfloat ? "1" : "0").append("\n");
            sb.append("mem_size = ").append(memSizeMb * 1024).append("\n");
            if (timeSync != null && !timeSync.isEmpty()) sb.append("time_sync = ").append(timeSync).append("\n");
            sb.append("\n");

            sb.append("[Video]\n");
            sb.append("gfxcard = ").append(videoCard).append("\n");
            if (hasVoodoo) sb.append("voodoo = 1\n");
            sb.append("\n");

            sb.append("[Input devices]\n");
            sb.append("mouse_type = ").append(mouseType).append("\n\n");

            sb.append("[Network]\n");
            sb.append("net_01_card = ").append(networkCard).append("\n");
            sb.append("net_01_net_type = ").append(networkType).append("\n\n");

            sb.append("[Sound]\n");
            sb.append("sndcard = ").append(soundCard).append("\n");
            if (midiDevice != null && !"none".equalsIgnoreCase(midiDevice)) {
                sb.append("midi_device = ").append(midiDevice).append("\n");
            }
            sb.append("\n");

            sb.append("[Storage controllers]\n");
            sb.append("hdc = ide\n\n");

            sb.append("[Hard disks]\n");
            if (hddPath != null && !hddPath.isEmpty()) {
                sb.append("hdd_01_fn = ").append(hddPath).append("\n");
                String geometry = calculateDiskGeometry(hddPath);
                sb.append("hdd_01_parameters = ").append(geometry).append("\n");
                sb.append("hdd_01_ide_channel = 0:0\n");
                sb.append("hdd_01_speed = 1997_5400rpm\n");
            }
            if (hddSounds) {
                sb.append("hdd_sounds = 1\n");
            }
            sb.append("\n");

            sb.append("[Floppy and CD-ROM drives]\n");
            sb.append("fdd_01_type = ").append(floppyType).append("\n");
            sb.append("fdd_01_writeprot = ").append(floppyWriteProtect ? "1" : "0").append("\n");
            sb.append("fdd_01_turbo = ").append(floppyTurbo ? "1" : "0").append("\n");
            sb.append("fdd_01_check_bpb = ").append(floppyCheckBpb ? "1" : "0").append("\n");
            if (floppyPath != null && !floppyPath.isEmpty()) {
                sb.append("fdd_01_fn = ").append(floppyPath).append("\n");
            }
            sb.append("cdrom_01_parameters = 1, atapi\n");
            sb.append("cdrom_01_ide_channel = 1:0\n");
            sb.append("cdrom_01_image_path = ").append(cdromPath != null ? cdromPath : "").append("\n");
            if (cdromSpeed > 0) {
                sb.append("cdrom_01_speed = ").append(cdromSpeed).append("\n");
            }

            return sb.toString();
        }

        public String updateExistingConfig(String existing) {
            if (existing == null || existing.trim().isEmpty()) {
                return generateConfigText();
            }

            IniEditor ini = new IniEditor(existing);
            ini.set("Machine", "machine", machine);
            if (cpuFamily != null && !cpuFamily.isEmpty()) ini.set("Machine", "cpu_family", cpuFamily);
            if (cpuType != null && !cpuType.isEmpty()) ini.set("Machine", "cpu_type", cpuType);
            if (cpuSpeedHz > 0) ini.set("Machine", "cpu_speed", String.valueOf(cpuSpeedHz));
            ini.set("Machine", "cpu_use_dynarec", cpuDynarec ? "1" : "0");
            ini.set("Machine", "fpu_softfloat", fpuSoftfloat ? "1" : "0");
            ini.set("Machine", "mem_size", String.valueOf(memSizeMb * 1024));
            if (timeSync != null) ini.set("Machine", "time_sync", timeSync);

            ini.set("Video", "gfxcard", videoCard);
            if (hasVoodoo) ini.set("Video", "voodoo", "1");
            else ini.remove("Video", "voodoo");

            ini.set("Input devices", "mouse_type", mouseType);
            ini.set("Network", "net_01_card", networkCard);
            ini.set("Network", "net_01_net_type", networkType);
            ini.set("Sound", "sndcard", soundCard);
            if (midiDevice != null && !"none".equalsIgnoreCase(midiDevice)) {
                ini.set("Sound", "midi_device", midiDevice);
            } else {
                ini.remove("Sound", "midi_device");
            }

            if (hddPath != null && !hddPath.isEmpty()) {
                ini.set("Hard disks", "hdd_01_fn", hddPath);
                String geometry = calculateDiskGeometry(hddPath);
                ini.set("Hard disks", "hdd_01_parameters", geometry);
                ini.set("Hard disks", "hdd_01_ide_channel", "0:0");
                ini.set("Hard disks", "hdd_01_speed", "1997_5400rpm");
            } else {
                ini.remove("Hard disks", "hdd_01_fn");
                ini.set("Hard disks", "hdd_01_parameters", "0, 0, 0, 0, none");
            }

            if (hddSounds) ini.set("Hard disks", "hdd_sounds", "1");
            else ini.remove("Hard disks", "hdd_sounds");

            if (cdromPath != null && !cdromPath.isEmpty()) {
                ini.set("Floppy and CD-ROM drives", "cdrom_01_image_path", cdromPath);
                ini.set("Floppy and CD-ROM drives", "cdrom_01_parameters", "1, atapi");
                ini.set("Floppy and CD-ROM drives", "cdrom_01_ide_channel", "1:0");
            } else {
                ini.set("Floppy and CD-ROM drives", "cdrom_01_image_path", "");
            }

            if (cdromSpeed > 0) ini.set("Floppy and CD-ROM drives", "cdrom_01_speed", String.valueOf(cdromSpeed));

            if (floppyPath != null && !floppyPath.isEmpty()) {
                ini.set("Floppy and CD-ROM drives", "fdd_01_fn", floppyPath);
            } else {
                ini.remove("Floppy and CD-ROM drives", "fdd_01_fn");
            }
            ini.set("Floppy and CD-ROM drives", "fdd_01_type", floppyType);
            ini.set("Floppy and CD-ROM drives", "fdd_01_writeprot", floppyWriteProtect ? "1" : "0");
            ini.set("Floppy and CD-ROM drives", "fdd_01_turbo", floppyTurbo ? "1" : "0");
            ini.set("Floppy and CD-ROM drives", "fdd_01_check_bpb", floppyCheckBpb ? "1" : "0");

            return ini.toString();
        }

        private static String calculateDiskGeometry(String path) {
            File file = new File(path);
            if (!file.isFile() || file.length() <= 0) {
                return "63, 16, 2080, 0, ide";
            }
            long sectors = file.length() / 512;
            int[][] preferred = {{63, 16}, {63, 255}, {32, 16}, {63, 32}, {32, 64}, {63, 64}};
            for (int[] pair : preferred) {
                int spt = pair[0];
                int heads = pair[pair.length - 1];
                long perCyl = (long) spt * heads;
                long cyl = sectors / perCyl;
                if (sectors % perCyl == 0 && cyl >= 1 && cyl <= 266305) {
                    return spt + ", " + heads + ", " + cyl + ", 0, ide";
                }
            }
            for (int heads = 16; heads >= 1; heads--) {
                for (int spt = 63; spt >= 1; spt--) {
                    long perCyl = (long) spt * heads;
                    long cyl = sectors / perCyl;
                    if (sectors % perCyl == 0 && cyl >= 1 && cyl <= 266305) {
                        return spt + ", " + heads + ", " + cyl + ", 0, ide";
                    }
                }
            }
            return "63, 16, 2080, 0, ide";
        }
    }

    public static final class IniEditor {
        private final List<String> lines;

        public IniEditor(String text) {
            lines = new ArrayList<>(Arrays.asList(text.replace("\r\n", "\n").split("\n", -1)));
        }

        public void set(String section, String key, String value) {
            remove(section, key);
            int insertIndex = -1;
            boolean inSection = false;

            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    if (inSection) break;
                    inSection = trimmed.equalsIgnoreCase("[" + section + "]");
                }
                if (inSection) insertIndex = i + 1;
            }

            if (insertIndex < 0) {
                lines.add("");
                lines.add("[" + section + "]");
                insertIndex = lines.size();
            }

            lines.add(insertIndex, key + " = " + value);
        }

        public void remove(String section, String key) {
            String currentSection = "";
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    currentSection = trimmed.substring(1, trimmed.length() - 1).trim();
                } else if (!trimmed.startsWith(";") && !trimmed.startsWith("#")) {
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        String currentKey = trimmed.substring(0, eq).trim();
                        if (currentSection.equalsIgnoreCase(section) && currentKey.equalsIgnoreCase(key)) {
                            lines.remove(i--);
                        }
                    }
                }
            }
        }

        @Override
        public String toString() {
            return TextUtils.join("\n", lines);
        }
    }
}
