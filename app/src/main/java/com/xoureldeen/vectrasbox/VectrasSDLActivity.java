package com.xoureldeen.vectrasbox;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.PopupMenu;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.xoureldeen.vectrasbox.controls.EditableControlsLayout;
import com.xoureldeen.vectrasbox.data.MachineStore;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VectrasSDLActivity extends SDLActivity {
    public static final String EXTRA_MACHINE_ID = "machine_id";
    private static final int PICK_ISO = 1001;
    private final Set<Integer> heldKeys = new HashSet<>();
    private final ExecutorService mediaWorker = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private MachineStore machineStore;
    private Closeable runtimeLock;
    private String[] launchArguments = new String[0];
    private String runningMachineId;
    private boolean commandPending;
    private boolean importing;

    private EditableControlsLayout editableControls;
    private View controlsToolbar;
    private MaterialButton controlsToggle;
    private MaterialButton editControlsButton;

    @Override
    protected SDLSurface createSDLSurface(Context context) {
        return new EmulatorSurface(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String launchError = null;
        try {
            machineStore = new MachineStore(this);
            runtimeLock = machineStore.acquireRuntimeLock();
            String machineId = getIntent().getStringExtra(EXTRA_MACHINE_ID);
            if (machineId == null) machineId = "default";
            runningMachineId = machineId;
            machineStore.prepareLaunch(machineId);
            File machineDirectory = machineStore.get(machineId).directory;
            File assets = new File(machineStore.root(), "assets");
            assets.mkdirs();
            launchArguments = new String[] {
                    "--rompath", machineStore.romsDir().getAbsolutePath(),
                    "--assetpath", assets.getAbsolutePath(),
                    "--vmpath", machineDirectory.getAbsolutePath(),
                    "--fullscreen"
            };
        } catch (Exception error) {
            launchError = error.getMessage();
        }
        super.onCreate(savedInstanceState);
        if (launchError != null) {
            Toast.makeText(this, "Cannot start this machine: " + launchError, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (mLayout != null) {
            installControls();
        }
    }

    @Override
    protected String[] getArguments() {
        return launchArguments.clone();
    }

    private void installControls() {
        View overlay = LayoutInflater.from(this).inflate(R.layout.activity_vectras_sdl, mLayout, false);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mLayout.addView(overlay, params);

        editableControls = overlay.findViewById(R.id.editable_controls);
        controlsToolbar = overlay.findViewById(R.id.controls_toolbar);
        controlsToggle = overlay.findViewById(R.id.controls_toggle);
        editControlsButton = overlay.findViewById(R.id.edit_controls_button);

        wireHold(overlay, R.id.up_button, KeyEvent.KEYCODE_DPAD_UP);
        wireHold(overlay, R.id.down_button, KeyEvent.KEYCODE_DPAD_DOWN);
        wireHold(overlay, R.id.left_button, KeyEvent.KEYCODE_DPAD_LEFT);
        wireHold(overlay, R.id.right_button, KeyEvent.KEYCODE_DPAD_RIGHT);
        wireHold(overlay, R.id.enter_button, KeyEvent.KEYCODE_ENTER);
        wireHold(overlay, R.id.ctrl_button, KeyEvent.KEYCODE_CTRL_LEFT);
        wireHold(overlay, R.id.tab_button, KeyEvent.KEYCODE_TAB);
        wireHold(overlay, R.id.space_button, KeyEvent.KEYCODE_SPACE);
        wireHold(overlay, R.id.e_button, KeyEvent.KEYCODE_E);
        wireHold(overlay, R.id.q_button, KeyEvent.KEYCODE_Q);
        wireHold(overlay, R.id.r_button, KeyEvent.KEYCODE_R);
        wireHold(overlay, R.id.x_button, KeyEvent.KEYCODE_X);
        wireHold(overlay, R.id.del_button, KeyEvent.KEYCODE_FORWARD_DEL);

        wireHold(overlay, R.id.esc_button, KeyEvent.KEYCODE_ESCAPE);
        wireHold(overlay, R.id.f8_button, KeyEvent.KEYCODE_F8);
        wireHold(overlay, R.id.f10_button, KeyEvent.KEYCODE_F10);
        wireHold(overlay, R.id.c_button, KeyEvent.KEYCODE_C);
        wireHold(overlay, R.id.d_button, KeyEvent.KEYCODE_D);
        wireHold(overlay, R.id.pgup_button, KeyEvent.KEYCODE_PAGE_UP);
        wireHold(overlay, R.id.pgdn_button, KeyEvent.KEYCODE_PAGE_DOWN);
        wireHold(overlay, R.id.shift_button, KeyEvent.KEYCODE_SHIFT_LEFT);
        wireHold(overlay, R.id.alt_button, KeyEvent.KEYCODE_ALT_LEFT);

        overlay.findViewById(R.id.hide_controls_button).setOnClickListener(view -> {
            haptic(view);
            setControlsVisible(false);
        });
        controlsToggle.setOnClickListener(view -> {
            haptic(view);
            setControlsVisible(true);
        });
        editControlsButton.setOnClickListener(view -> toggleEditMode());
        editControlsButton.setOnLongClickListener(view -> {
            haptic(view);
            showResetDialog();
            return true;
        });

        View keyboardButton = overlay.findViewById(R.id.keyboard_button);
        keyboardButton.setOnClickListener(view -> {
            haptic(view);
            SDLActivity.showTextInput(0, 0, 1, 1);
        });
        keyboardButton.setOnLongClickListener(view -> {
            haptic(view);
            releaseAllKeys();
            pressKey(KeyEvent.KEYCODE_F10);
            focusEmulator();
            return true;
        });
        View menuButton = overlay.findViewById(R.id.menu_button);
        menuButton.setOnClickListener(view -> {
            haptic(view);
            releaseAllKeys();
            showMachineMenu(view);
        });
        menuButton.setOnLongClickListener(view -> {
            haptic(view);
            releaseAllKeys();
            pressKey(KeyEvent.KEYCODE_ESCAPE);
            focusEmulator();
            return true;
        });
        overlay.findViewById(R.id.cad_button).setOnClickListener(view -> {
            haptic(view);
            releaseAllKeys();
            keyDown(KeyEvent.KEYCODE_CTRL_LEFT);
            keyDown(KeyEvent.KEYCODE_ALT_LEFT);
            pressKey(KeyEvent.KEYCODE_FORWARD_DEL);
            keyUp(KeyEvent.KEYCODE_ALT_LEFT);
            keyUp(KeyEvent.KEYCODE_CTRL_LEFT);
            focusEmulator();
        });
    }

    private void wireHold(View root, int viewId, int keyCode) {
        View button = root.findViewById(viewId);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    haptic(view);
                    view.setPressed(true);
                    view.animate().cancel();
                    view.animate().scaleX(0.82f).scaleY(0.82f).setDuration(70).start();
                    keyDown(keyCode);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    view.animate().cancel();
                    view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                    keyUp(keyCode);
                    focusEmulator();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void toggleEditMode() {
        if (editableControls == null) {
            return;
        }
        boolean editing = !editableControls.isEditMode();
        releaseAllKeys();
        editableControls.setEditMode(editing);
        editControlsButton.setText(editing ? R.string.finish_editing : R.string.edit_controls);
        Toast.makeText(this,
                editing ? R.string.edit_controls_hint : R.string.controls_saved,
                Toast.LENGTH_SHORT).show();
    }

    private void showResetDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_controls)
                .setMessage(R.string.reset_controls_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset_controls, (dialog, which) -> {
                    releaseAllKeys();
                    editableControls.setEditMode(false);
                    editableControls.resetSavedPositions();
                    editControlsButton.setText(R.string.edit_controls);
                    Toast.makeText(this, R.string.controls_reset, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void setControlsVisible(boolean visible) {
        releaseAllKeys();
        if (editableControls != null) {
            editableControls.setEditMode(false);
            editableControls.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (editControlsButton != null) {
            editControlsButton.setText(R.string.edit_controls);
        }
        if (controlsToolbar != null) {
            controlsToolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (controlsToggle != null) {
            controlsToggle.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
        focusEmulator();
    }

    private void pressKey(int keyCode) {
        keyDown(keyCode);
        keyUp(keyCode);
    }

    private void keyDown(int keyCode) {
        if (heldKeys.add(keyCode)) {
            SDLActivity.onNativeKeyDown(keyCode);
        }
    }

    private void keyUp(int keyCode) {
        if (heldKeys.remove(keyCode)) {
            SDLActivity.onNativeKeyUp(keyCode);
        }
    }

    private void releaseAllKeys() {
        Integer[] keys = heldKeys.toArray(new Integer[0]);
        for (int keyCode : keys) {
            SDLActivity.onNativeKeyUp(keyCode);
        }
        heldKeys.clear();
    }

    private void focusEmulator() {
        if (mSurface != null) {
            mSurface.requestFocus();
        }
    }

    private void haptic(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    protected void onPause() {
        releaseAllKeys();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_ISO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importIso(uri);
        }
    }

    private void showMachineMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Insert ISO from phone…");
        popup.getMenu().add(0, 2, 0, "Insert saved ISO…");
        popup.getMenu().add(0, 3, 0, "Eject CD-ROM");
        popup.getMenu().add(0, 4, 0, "Special keys…");
        popup.getMenu().add(0, 5, 0, "PCBox settings");
        popup.getMenu().add(0, 6, 0, "Restart machine…");
        popup.getMenu().add(0, 7, 0, "Stop and return to library…");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (!importing) {
                        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        picker.addCategory(Intent.CATEGORY_OPENABLE);
                        picker.setType("*/*");
                        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivityForResult(picker, PICK_ISO);
                    }
                    return true;
                case 2:
                    showIsoLibrary();
                    return true;
                case 3:
                    sendCommand(NativeMachine.EJECT_CD, null, "CD-ROM ejected. The ISO is still in your library.");
                    return true;
                case 4:
                    showSpecialKeys();
                    return true;
                case 5:
                    keyDown(KeyEvent.KEYCODE_CTRL_RIGHT);
                    pressKey(KeyEvent.KEYCODE_F11);
                    keyUp(KeyEvent.KEYCODE_CTRL_RIGHT);
                    focusEmulator();
                    return true;
                case 6:
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Restart machine?")
                            .setMessage("This is a hardware reset. Save your work in your Machine first.")
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton("Restart", (dialog, which) ->
                                    sendCommand(NativeMachine.RESET, null, "Machine restarted."))
                            .show();
                    return true;
                case 7:
                    showStopDialog();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void showIsoLibrary() {
        try {
            List<File> files = machineStore.media(false);
            if (files.isEmpty()) {
                toast("No saved ISO yet. Choose Insert ISO from phone.");
                return;
            }
            String[] names = new String[files.size()];
            for (int i = 0; i < files.size(); i++) names[i] = files.get(i).getName();
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Insert CD-ROM")
                    .setItems(names, (dialog, which) -> mountIso(files.get(which)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception error) {
            toast("Cannot read the ISO library: " + error.getMessage());
        }
    }

    private void importIso(Uri uri) {
        if (importing) return;
        importing = true;
        androidx.appcompat.app.AlertDialog progress = new MaterialAlertDialogBuilder(this)
                .setTitle("Importing ISO")
                .setMessage("Copying to your phone's VM library…")
                .setCancelable(false)
                .create();
        progress.show();
        mediaWorker.execute(() -> {
            try {
                final long[] lastUpdate = {0};
                File iso = machineStore.importMedia(uri, false, (copied, total) -> {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - lastUpdate[0] < 250) return;
                    lastUpdate[0] = now;
                    String amount = total > 0 ? (copied * 100 / total) + "%"
                            : (copied / (1024 * 1024)) + " MB";
                    runOnUiThread(() -> {
                        if (!isDestroyed()) progress.setMessage("Copying ISO: " + amount);
                    });
                });
                runOnUiThread(() -> {
                    importing = false;
                    if (isDestroyed()) return;
                    progress.dismiss();
                    mountIso(iso);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    importing = false;
                    if (isDestroyed()) return;
                    progress.dismiss();
                    toast("ISO import failed: " + error.getMessage());
                });
            }
        });
    }

    private void mountIso(File iso) {
        sendCommand(NativeMachine.MOUNT_CD, iso.getAbsolutePath(), "CD-ROM inserted: " + iso.getName());
    }

    private void sendCommand(int command, String path, String success) {
        releaseAllKeys();
        if (commandPending || !NativeMachine.request(command, path)) {
            toast("The machine is starting or finishing another operation. Try again shortly.");
            return;
        }
        commandPending = true;
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (isDestroyed() || isFinishing()) return;
                int result = NativeMachine.result();
                if (result == 0) {
                    uiHandler.postDelayed(this, 150);
                    return;
                }
                commandPending = false;
                if (result > 0) {
                    if (command != NativeMachine.STOP) toast(success);
                } else {
                    toast(result == -2 ? "This machine has no enabled CD-ROM drive. Configure it in PCBox settings."
                            : "The operation could not be completed. Check the selected image.");
                }
                focusEmulator();
            }
        }, 150);
    }

    private void showSpecialKeys() {
        String[] names = {"Esc", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
                "Delete", "Backspace", "Insert", "Home", "End", "Page Up", "Page Down", "Windows", "Alt + Tab"};
        int[] codes = {KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3,
                KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7,
                KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11,
                KeyEvent.KEYCODE_F12, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_META_LEFT};
        new MaterialAlertDialogBuilder(this).setTitle("Send key")
                .setItems(names, (dialog, which) -> {
                    releaseAllKeys();
                    if (which == codes.length) {
                        keyDown(KeyEvent.KEYCODE_ALT_LEFT);
                        pressKey(KeyEvent.KEYCODE_TAB);
                        keyUp(KeyEvent.KEYCODE_ALT_LEFT);
                    } else {
                        pressKey(codes[which]);
                    }
                    focusEmulator();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showStopDialog() {
        releaseAllKeys();
        new MaterialAlertDialogBuilder(this).setTitle("Stop machine?")
                .setMessage("Shut down this Machine from its Start menu first to protect your files. Stop closes this machine and returns to your library.")
                .setNegativeButton("Keep running", null)
                .setPositiveButton("Stop machine", (dialog, which) ->
                        sendCommand(NativeMachine.STOP, null, ""))
                .show();
    }

    @Override public void onBackPressed() {
        showStopDialog();
    }

    @Override public void superOnBackPressed() {
        showStopDialog();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String requested = intent.getStringExtra(EXTRA_MACHINE_ID);
        if (requested != null && !requested.equals(runningMachineId)) {
            toast("Stop the current machine before starting another one.");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        mediaWorker.shutdownNow();

        super.onDestroy();
        if (runtimeLock != null) {
            try { runtimeLock.close(); } catch (IOException ignored) { }
            runtimeLock = null;
        }

        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
