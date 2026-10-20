package com.xoureldeen.vectrasbox;

import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PcBoxInputTest {
    @Test public void sharedModifierStaysHeldUntilBothSourcesReleaseIt() {
        List<String> keys = new ArrayList<>();
        PcBoxInput input = new PcBoxInput((code, down) -> keys.add(code + ":" + down), (state, action) -> {});
        input.key(KeyEvent.KEYCODE_CTRL_LEFT, true);
        input.toolbarKey(KeyEvent.KEYCODE_CTRL_LEFT, true);
        input.toolbarKey(KeyEvent.KEYCODE_CTRL_LEFT, false);
        assertEquals(Arrays.asList("113:true"), keys);
        input.key(KeyEvent.KEYCODE_CTRL_LEFT, false);
        assertEquals(Arrays.asList("113:true", "113:false"), keys);
    }

    @Test public void mouseButtonsKeepSdlButtonStateUntilEachRelease() {
        List<String> events = new ArrayList<>();
        PcBoxInput input = new PcBoxInput((code, down) -> {}, (state, action) -> events.add(state + ":" + action));
        input.mouse(1, true);
        input.mouse(3, true);
        input.mouse(1, false);
        input.mouse(3, false);
        assertEquals(Arrays.asList("1:0", "3:0", "2:1", "0:1"), events);
    }

    @Test public void releasingOnPauseClearsHeldKeyboardAndMouseWithoutDuplicates() {
        List<String> keys = new ArrayList<>();
        List<String> mouse = new ArrayList<>();
        PcBoxInput input = new PcBoxInput((code, down) -> keys.add(code + ":" + down),
            (state, action) -> mouse.add(state + ":" + action));
        input.key(KeyEvent.KEYCODE_W, true);
        input.toolbarKey(KeyEvent.KEYCODE_ALT_LEFT, true);
        input.mouse(2, true);
        input.releaseAll();
        int count = keys.size();
        input.releaseAll();
        assertEquals(count, keys.size());
        assertTrue(keys.contains(KeyEvent.KEYCODE_W + ":false"));
        assertTrue(keys.contains(KeyEvent.KEYCODE_ALT_LEFT + ":false"));
        assertEquals(Arrays.asList("4:0", "0:1"), mouse);
    }

    @Test public void invalidMouseButtonsCannotReachSdl() {
        List<Integer> mouse = new ArrayList<>();
        PcBoxInput input = new PcBoxInput((code, down) -> {}, (state, action) -> mouse.add(action));
        input.mouse(0, true);
        input.mouse(4, true);
        assertTrue(mouse.isEmpty());
    }

    @Test public void touchpadClickPreservesHeldOverlayMouseButtons() {
        List<Integer> states = new ArrayList<>();
        PcBoxInput input = new PcBoxInput((code, down) -> {}, (state, action) -> states.add(state));
        input.mouse(1, true);
        input.click(1);
        input.click(3);
        assertEquals(Arrays.asList(1, 3, 1), states);
        input.releaseAll();
        assertEquals(Arrays.asList(1, 3, 1, 0), states);
    }
}
