package com.xoureldeen.vectrasbox;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.vectras.controls.ControlOverlay;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PcBoxControlsDeviceTest {
    @Test @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    public void searchKeyboardAndViewportResizeKeepEditorOpen() {
        android.app.Instrumentation instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        PcBoxControls[] controls = new PcBoxControls[1];
        ViewGroup[] root = new ViewGroup[1];
        com.google.android.material.textfield.TextInputEditText[] input = new com.google.android.material.textfield.TextInputEditText[1];
        float[] point = new float[2];
        try (ActivityScenario<AboutActivity> scenario = ActivityScenario.launch(AboutActivity.class)) {
            scenario.onActivity(activity -> {
                activity.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                root[0] = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.activity_vectras_sdl, null);
                activity.setContentView(root[0]);
                controls[0] = new PcBoxControls(activity, root[0], new PcBoxInput((code, down) -> {}, (state, action) -> {}), () -> {});
            });
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                controls[0].edit(true);
                findText(root[0], activity.getString(com.vectras.controls.R.string.controls_add_key)).performClick();
            });
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    com.google.android.material.textfield.TextInputEditText found = findInput(window);
                    if (found != null && found.isShown()) input[0] = found;
                }
                assertNotNull(input[0]);
                int[] location = new int[2]; input[0].getLocationOnScreen(location);
                point[0] = location[0] + input[0].getWidth() / 2f;
                point[1] = location[1] + input[0].getHeight() / 2f;
            });
            long now = android.os.SystemClock.uptimeMillis();
            for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
                android.view.MotionEvent event = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), action, point[0], point[1], 0);
                event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                try { instrumentation.sendPointerSync(event); } finally { event.recycle(); }
            }
            boolean[] keyboard = {false};
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (!keyboard[0] && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync();
                scenario.onActivity(activity -> {
                    assertTrue("IME must not close the editing session", controls[0].isEditing());
                    androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(input[0]);
                    keyboard[0] = insets != null && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());
                });
                if (!keyboard[0]) android.os.SystemClock.sleep(50);
            }
            assertTrue("Keyboard must open for search", keyboard[0]);
            scenario.onActivity(activity -> {
                ControlOverlay overlay = root[0].findViewById(R.id.editable_controls);
                overlay.layout(overlay.getLeft(), overlay.getTop(), overlay.getRight(), Math.max(overlay.getTop() + 1, overlay.getBottom() - 100));
                assertTrue("Viewport resize must retain the editing session", controls[0].isEditing());
                assertTrue(input[0].isShown());
                input[0].setText("f10");
                assertEquals("f10", input[0].getText().toString());
                activity.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(input[0].getWindowToken(), 0);
            });
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                assertTrue(controls[0].isEditing());
                assertTrue(controls[0].closeEditor());
                assertFalse(controls[0].isEditing());
            });
        }
    }

    private com.google.android.material.textfield.TextInputEditText findInput(View view) {
        if (view instanceof com.google.android.material.textfield.TextInputEditText) return (com.google.android.material.textfield.TextInputEditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                com.google.android.material.textfield.TextInputEditText found = findInput(group.getChildAt(i));
                if (found != null && found.isShown()) return found;
            }
        }
        return null;
    }

    @Test public void toolbarOpensSharedEditorWithGridAndInputFeedbackTools() {
        try (ActivityScenario<AboutActivity> scenario = ActivityScenario.launch(AboutActivity.class)) {
            scenario.onActivity(activity -> {
                ViewGroup root = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.activity_vectras_sdl, null);
                activity.setContentView(root);
                PcBoxInput input = new PcBoxInput((code, down) -> {}, (state, action) -> {});
                PcBoxControls controls = new PcBoxControls(activity, root, input, () -> {});
                root.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 1200, 700);
                ControlOverlay overlay = root.findViewById(R.id.editable_controls);
                assertFalse(overlay.snapshot().isEmpty());
                assertEquals(1f, root.getAlpha(), 0f);
                assertTrue(root.indexOfChild(overlay) < root.indexOfChild(root.findViewById(R.id.controls_toolbar)));
                assertTrue(root.findViewById(R.id.stop_machine_button).getContentDescription().length() > 0);
                root.findViewById(R.id.edit_controls_button).performClick();
                assertTrue(controls.isEditing());
                assertEquals(View.INVISIBLE, overlay.getVisibility());
                TextView tools = findText(root, activity.getString(com.vectras.controls.R.string.controls_options));
                assertNotNull(tools);
                tools.performClick();
                TextView snapping = findText(root, activity.getString(com.vectras.controls.R.string.controls_snap_grid));
                TextView feedback = findText(root, activity.getString(com.vectras.controls.R.string.controls_input_haptics));
                assertTrue(snapping instanceof MaterialSwitch);
                assertTrue(feedback instanceof MaterialSwitch);
                assertEquals(View.VISIBLE, snapping.getVisibility());
                assertEquals(View.VISIBLE, feedback.getVisibility());
                assertNotNull(findText(root, activity.getString(com.vectras.controls.R.string.controls_change_size)));
                assertTrue(controls.closeEditor());
                assertFalse(controls.isEditing());
                assertEquals(View.VISIBLE, overlay.getVisibility());
                int controlCount = overlay.snapshot().size();
                root.findViewById(R.id.edit_controls_button).performClick();
                TextView save = findText(root, activity.getString(com.vectras.controls.R.string.controls_save));
                assertNotNull(save);
                save.performClick();
                assertFalse(controls.isEditing());
                assertEquals(controlCount, overlay.snapshot().size());
                root.findViewById(R.id.hide_controls_button).performClick();
                assertEquals(View.GONE, overlay.getVisibility());
                assertEquals(View.VISIBLE, root.findViewById(R.id.controls_toggle).getVisibility());
                root.findViewById(R.id.controls_toggle).performClick();
                assertEquals(View.VISIBLE, overlay.getVisibility());
                controls.releaseInputs();
            });
        }
    }

    private TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findText(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
