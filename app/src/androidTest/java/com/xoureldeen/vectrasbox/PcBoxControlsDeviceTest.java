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
