package com.xoureldeen.vectrasbox;

import android.view.KeyEvent;
import com.vectras.controls.ControlItem;
import com.vectras.controls.ControlKind;
import java.util.*;

final class PcBoxControlsMigration {
    private static final int[] CODES = {KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_X,
        KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ALT_LEFT};
    private static final String[] TAGS = {"e", "q", "r", "x", "del", "up", "down", "left", "right", "ctrl", "enter", "tab", "space",
        "esc", "f8", "f10", "c", "d", "pgup", "pgdn", "shift", "alt"};

    static List<ControlItem> apply(List<ControlItem> layout, Map<String, ?> old) {
        List<ControlItem> migrated = new ArrayList<>();
        for (ControlItem item : layout) {
            String tag = item.getKind() == ControlKind.JOYSTICK ? "joystick" : null;
            if (item.getKind() == ControlKind.KEY) for (int i = 0; i < CODES.length; i++)
                if (item.getKeyCode() == CODES[i]) { tag = TAGS[i]; break; }
            Object rawX = old.get("x_" + tag), rawY = old.get("y_" + tag);
            if (tag != null && rawX instanceof Number && rawY instanceof Number) {
                float x = ((Number) rawX).floatValue(), y = ((Number) rawY).floatValue();
                if (Float.isFinite(x) && Float.isFinite(y)) item = item.copy(item.getId(), item.getKind(), item.getKeyCode(),
                    Math.max(0f, Math.min(1f, x)), Math.max(0f, Math.min(1f, y)), item.getSizeDp(), item.getSticky(),
                    item.getMapping(), item.getAspectRatio(), item.getDeadZone(), item.getDiagonals());
            }
            migrated.add(item);
        }
        return migrated;
    }
}
