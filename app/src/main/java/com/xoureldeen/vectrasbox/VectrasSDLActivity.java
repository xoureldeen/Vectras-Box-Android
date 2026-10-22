package com.xoureldeen.vectrasbox;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.PopupMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.xoureldeen.vectrasbox.data.MachineStore;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VectrasSDLActivity extends SDLActivity {
    public static final String EXTRA_MACHINE_ID = "machine_id";
    private static final int PICK_ISO = 1001;
    private final PcBoxInput pcInput = new PcBoxInput((code, down) -> {
        if (down) SDLActivity.onNativeKeyDown(code); else SDLActivity.onNativeKeyUp(code);
    }, (state, action) -> SDLActivity.onNativeMouse(state, action, 0f, 0f, true));
    private final ExecutorService mediaWorker = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private MachineStore machineStore;
    private Closeable runtimeLock;
    private String[] launchArguments = new String[0];
    private String runningMachineId;
    private boolean commandPending;
    private boolean importing;

    private PcBoxControls controls;

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
        configureNetworkDns();
        return launchArguments.clone();
    }

    private void configureNetworkDns() {
        nativeSetenv("VECTRAS_NETWORK_DNS_IPV4", "");
        nativeSetenv("VECTRAS_NETWORK_DNS_IPV6", "");
        try {
            android.net.ConnectivityManager manager =
                    (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.LinkProperties properties = manager == null ? null
                    : manager.getLinkProperties(manager.getActiveNetwork());
            if (properties == null) return;
            boolean ipv4 = false, ipv6 = false;
            for (java.net.InetAddress address : properties.getDnsServers()) {
                if (!ipv4 && address instanceof java.net.Inet4Address) {
                    nativeSetenv("VECTRAS_NETWORK_DNS_IPV4", address.getHostAddress());
                    ipv4 = true;
                } else if (!ipv6 && address instanceof java.net.Inet6Address && !address.isLinkLocalAddress()) {
                    nativeSetenv("VECTRAS_NETWORK_DNS_IPV6", address.getHostAddress().split("%")[0]);
                    ipv6 = true;
                }
            }
        } catch (SecurityException ignored) { }
    }

    private void installControls() {
        ViewGroup overlay = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.activity_vectras_sdl, mLayout, false);
        mLayout.addView(overlay, new RelativeLayout.LayoutParams(-1, -1));
        controls = new PcBoxControls(this, overlay, pcInput, this::focusEmulator);
        if (mSurface instanceof EmulatorSurface) ((EmulatorSurface) mSurface).setControlInput(pcInput);
        View keyboard = overlay.findViewById(R.id.keyboard_button);
        keyboard.setOnClickListener(view -> {
            releaseAllKeys();
            SDLActivity.showTextInput(0, 0, 1, 1);
        });
        keyboard.setOnLongClickListener(view -> {
            releaseAllKeys();
            pressKey(KeyEvent.KEYCODE_F10);
            focusEmulator();
            return true;
        });
        View menu = overlay.findViewById(R.id.menu_button);
        menu.setOnClickListener(view -> { releaseAllKeys(); showMachineMenu(view); });
        menu.setOnLongClickListener(view -> {
            releaseAllKeys();
            pressKey(KeyEvent.KEYCODE_ESCAPE);
            focusEmulator();
            return true;
        });
        overlay.findViewById(R.id.stop_machine_button).setOnClickListener(view -> showStopDialog());
        for (int id : new int[]{R.id.menu_button, R.id.edit_controls_button, R.id.keyboard_button,
                R.id.hide_controls_button, R.id.stop_machine_button, R.id.controls_toggle}) {
            View button = overlay.findViewById(id);
            androidx.core.view.ViewCompat.setTooltipText(button, button.getContentDescription());
        }
    }

    private void pressKey(int keyCode) {
        keyDown(keyCode);
        keyUp(keyCode);
    }

    private void keyDown(int keyCode) { pcInput.toolbarKey(keyCode, true); }

    private void keyUp(int keyCode) { pcInput.toolbarKey(keyCode, false); }

    private void releaseAllKeys() {
        if (controls != null) controls.releaseInputs();
        else pcInput.releaseAll();
    }

    private void focusEmulator() {
        if (mSurface != null) {
            mSurface.requestFocus();
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (controls != null && controls.isEditing()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) controls.closeEditor();
            } else getWindow().superDispatchKeyEvent(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) releaseAllKeys();
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onPause() {
        if (controls != null) controls.pause();
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
        popup.getMenu().add(0, 8, 0, getString(com.vectras.controls.R.string.controls_options));
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
                case 8:
                    controls.edit(true);
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
                "Delete", "Backspace", "Insert", "Home", "End", "Page Up", "Page Down", "Windows", "Alt + Tab", "Ctrl + Alt + Del"};
        int[] codes = {KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3,
                KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7,
                KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11,
                KeyEvent.KEYCODE_F12, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_META_LEFT};
        new MaterialAlertDialogBuilder(this).setTitle("Send key")
                .setItems(names, (dialog, which) -> {
                    releaseAllKeys();
                    if (which == codes.length + 1) {
                        keyDown(KeyEvent.KEYCODE_CTRL_LEFT);
                        keyDown(KeyEvent.KEYCODE_ALT_LEFT);
                        pressKey(KeyEvent.KEYCODE_FORWARD_DEL);
                        keyUp(KeyEvent.KEYCODE_ALT_LEFT);
                        keyUp(KeyEvent.KEYCODE_CTRL_LEFT);
                    } else if (which == codes.length) {
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
        if (controls != null && controls.closeEditor()) return;
        showStopDialog();
    }

    @Override public void superOnBackPressed() {
        onBackPressed();
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
        if (controls != null) controls.closeEditor();
        releaseAllKeys();
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
