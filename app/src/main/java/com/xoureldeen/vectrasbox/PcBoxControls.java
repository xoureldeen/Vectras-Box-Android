package com.xoureldeen.vectrasbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vectras.controls.ControlItem;
import com.vectras.controls.ControlInputSink;
import com.vectras.controls.ControlKind;
import com.vectras.controls.ControlLayoutStore;
import com.vectras.controls.ControlOverlay;
import com.vectras.controls.ControlPresets;
import com.vectras.controls.ControlsEditor;
import com.vectras.controls.JoystickMapping;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;

final class PcBoxControls {
    private final Context context;
    private final ViewGroup root;
    private final ControlOverlay overlay;
    private final View toolbar;
    private final View toggle;
    private final PcBoxInput input;
    private final Runnable focus;
    private final ControlLayoutStore store;
    private ControlsEditor.Session editor;
    private boolean visible = true;
    private boolean refreshing;

    PcBoxControls(Context context, ViewGroup root, PcBoxInput input, Runnable focus) {
        this.context = context;
        this.root = root;
        this.input = input;
        this.focus = focus;
        store = new ControlLayoutStore(context);
        overlay = root.findViewById(R.id.editable_controls);
        overlay.setThemeOptions(ControlsAppearance.options);
        toolbar = root.findViewById(R.id.controls_toolbar);
        toggle = root.findViewById(R.id.controls_toggle);
        overlay.setSink(new ControlInputSink() {
            @Override public void key(int code, boolean down) {
                input.key(code, down);
                if (!down && editor == null) focus.run();
            }
            @Override public void mouse(int button, boolean down) { input.mouse(button, down); }
        });
        root.findViewById(R.id.edit_controls_button).setOnClickListener(view -> edit(false));
        root.findViewById(R.id.edit_controls_button).setOnLongClickListener(view -> { reset(); return true; });
        root.findViewById(R.id.hide_controls_button).setOnClickListener(view -> setVisible(false));
        toggle.setOnClickListener(view -> setVisible(true));
        overlay.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (editor == null && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) {
                refresh();
            }
        });
        overlay.post(this::refresh);
    }

    private String profile() {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
            ? "pcbox-landscape" : "pcbox-portrait";
    }

    private List<ControlItem> defaults(float width, float height) {
        if (width < 160 || height < 80) return ControlPresets.INSTANCE.gamepad();
        List<ControlItem> result = new ArrayList<>();
        for (ControlItem item : ControlPresets.INSTANCE.runner(width, height, 48f)) {
            result.add(item.getKind() == ControlKind.JOYSTICK ? item.copy(item.getId(), item.getKind(), item.getKeyCode(),
                item.getX(), item.getY(), item.getSizeDp(), item.getSticky(), JoystickMapping.ARROWS,
                item.getAspectRatio(), item.getDeadZone(), item.getDiagonals(), item.getOpacity()) : item);
        }
        return result;
    }

    private void refresh() {
        if (refreshing || overlay.getWidth() <= 0 || overlay.getHeight() <= 0) return;
        refreshing = true;
        try {
            List<ControlItem> saved = store.load(profile());
            if (saved == null) {
                float density = context.getResources().getDisplayMetrics().density;
                saved = defaults(overlay.getWidth() / density, overlay.getHeight() / density);
                SharedPreferences migration = context.getSharedPreferences("pcbox_controls", Context.MODE_PRIVATE);
                String marker = "migrated-" + profile();
                if (!migration.getBoolean(marker, false)) {
                    List<ControlItem> migrated = PcBoxControlsMigration.apply(saved,
                        context.getSharedPreferences("vectrasbox_control_positions_v1", Context.MODE_PRIVATE).getAll());
                    if (!migrated.equals(saved)) {
                        try { store.save(profile(), migrated); }
                        catch (Exception error) {
                            Toast.makeText(context, com.vectras.controls.R.string.controls_save_failed, Toast.LENGTH_SHORT).show();
                            overlay.setControls(migrated);
                            return;
                        }
                    }
                    saved = migrated;
                    migration.edit().putBoolean(marker, true).apply();
                }
            }
            overlay.setControls(saved);
        } finally { refreshing = false; }
    }

    void edit(boolean tools) {
        if (editor != null) return;
        releaseInputs();
        overlay.setVisibility(View.INVISIBLE);
        toolbar.setVisibility(View.INVISIBLE);
        toggle.setVisibility(View.GONE);
        editor = new ControlsEditor(overlay.themeContext(), store).show(root, profile(), overlay.snapshot(),
            this::defaults, saved -> {
                editor = null;
                overlay.setVisibility(visible ? View.VISIBLE : View.GONE);
                toolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
                toggle.setVisibility(visible ? View.GONE : View.VISIBLE);
                refresh();
                focus.run();
                return Unit.INSTANCE;
            });
        if (tools) editor.showTools();
    }

    private void reset() {
        new MaterialAlertDialogBuilder(context).setTitle(R.string.reset_controls)
            .setMessage(R.string.reset_controls_message).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reset_controls, (dialog, which) -> {
                releaseInputs();
                store.reset(profile());
                context.getSharedPreferences("pcbox_controls", Context.MODE_PRIVATE).edit()
                    .putBoolean("migrated-" + profile(), true).apply();
                refresh();
            }).show();
    }

    private void setVisible(boolean show) {
        releaseInputs();
        visible = show;
        overlay.setVisibility(show ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(show ? View.VISIBLE : View.GONE);
        toggle.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) refresh();
        focus.run();
    }

    boolean isEditing() { return editor != null; }
    void pause() {
        releaseInputs();
        if (editor == null || !editor.isTransferring()) closeEditor();
    }
    boolean closeEditor() {
        if (editor == null) return false;
        editor.dismiss();
        return true;
    }
    void releaseInputs() { overlay.releaseAll(); input.releaseAll(); }
}
