package com.xoureldeen.vectrasbox;

import android.view.MotionEvent;
import com.vectras.controls.ControlInputSink;
import com.vectras.controls.HeldInputs;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import kotlin.Unit;

final class PcBoxInput implements ControlInputSink {
    interface Keys { void send(int code, boolean down); }
    interface Mouse { void send(int state, int action); }
    private final HeldInputs keyboard;
    private final Mouse mouse;
    private final Set<Integer> toolbarKeys = new HashSet<>();
    private int mouseState;

    PcBoxInput(Keys keys, Mouse mouse) {
        this.mouse = mouse;
        keyboard = new HeldInputs((code, down) -> { keys.send(code, down); return Unit.INSTANCE; });
    }

    @Override public void key(int code, boolean down) {
        keyboard.update("overlay-" + code, down ? Collections.singleton(code) : Collections.emptySet());
    }

    void toolbarKey(int code, boolean down) {
        if (down) toolbarKeys.add(code); else toolbarKeys.remove(code);
        keyboard.update("toolbar", toolbarKeys);
    }

    @Override public void mouse(int button, boolean down) {
        int mask;
        switch (button) {
            case 1: mask = MotionEvent.BUTTON_PRIMARY; break;
            case 2: mask = MotionEvent.BUTTON_TERTIARY; break;
            case 3: mask = MotionEvent.BUTTON_SECONDARY; break;
            default: return;
        }
        int next = down ? mouseState | mask : mouseState & ~mask;
        if (next == mouseState) return;
        mouseState = next;
        mouse.send(mouseState, down ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP);
    }

    void releaseAll() {
        keyboard.releaseAll();
        toolbarKeys.clear();
        for (int button = 1; button <= 3; button++) mouse(button, false);
    }

    void click(int button) {
        int mask = button == 1 ? MotionEvent.BUTTON_PRIMARY : button == 2 ? MotionEvent.BUTTON_TERTIARY
            : button == 3 ? MotionEvent.BUTTON_SECONDARY : 0;
        if (mask == 0 || (mouseState & mask) != 0) return;
        mouse(button, true);
        mouse(button, false);
    }
}
