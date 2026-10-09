package com.xoureldeen.vectrasbox;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;

public final class EmulatorSurface extends SDLSurface {
    private static final long TAP_TIMEOUT_MS = 280L;

    private final float touchSlop;
    private final float scrollStep;
    private float downX;
    private float downY;
    private float lastTwoFingerY;
    private float twoFingerTravel;
    private float scrollRemainder;
    private long downTime;
    private int maxPointers;
    private boolean moved;

    public EmulatorSurface(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scrollStep = 28f * density;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            return super.onTouch(view, event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downTime = event.getEventTime();
                maxPointers = 1;
                moved = false;
                scrollRemainder = 0f;
                return super.onTouch(view, event);

            case MotionEvent.ACTION_POINTER_DOWN:
                maxPointers = Math.max(maxPointers, event.getPointerCount());
                if (event.getPointerCount() >= 2) {
                    lastTwoFingerY = averageY(event);
                    twoFingerTravel = 0f;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                maxPointers = Math.max(maxPointers, event.getPointerCount());
                if (event.getPointerCount() >= 2) {
                    float currentY = averageY(event);
                    float deltaY = currentY - lastTwoFingerY;
                    lastTwoFingerY = currentY;
                    scrollRemainder += deltaY;
                    twoFingerTravel += Math.abs(deltaY);
                    if (twoFingerTravel > touchSlop) {
                        moved = true;
                    }
                    while (Math.abs(scrollRemainder) >= scrollStep) {
                        float wheelY = scrollRemainder > 0f ? -1f : 1f;
                        SDLActivity.onNativeMouse(0, MotionEvent.ACTION_SCROLL, 0f, wheelY, false);
                        scrollRemainder += scrollRemainder > 0f ? -scrollStep : scrollStep;
                    }
                    return true;
                }
                if (Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop) {
                    moved = true;
                }
                return super.onTouch(view, event);

            case MotionEvent.ACTION_POINTER_UP:
                return true;

            case MotionEvent.ACTION_UP:
                long duration = event.getEventTime() - downTime;
                if (!moved && duration <= TAP_TIMEOUT_MS) {
                    int button = maxPointers >= 2
                        ? MotionEvent.BUTTON_SECONDARY
                        : MotionEvent.BUTTON_PRIMARY;
                    sendClick(button);
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
                return maxPointers == 1 ? super.onTouch(view, event) : true;

            case MotionEvent.ACTION_CANCEL:
                return maxPointers == 1 ? super.onTouch(view, event) : true;

            default:
                return super.onTouch(view, event);
        }
    }

    private static float averageY(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getY(i);
        }
        return sum / event.getPointerCount();
    }

    private static void sendClick(int buttonState) {
        SDLActivity.onNativeMouse(buttonState, MotionEvent.ACTION_DOWN, 0f, 0f, true);
        SDLActivity.onNativeMouse(0, MotionEvent.ACTION_UP, 0f, 0f, true);
    }
}
