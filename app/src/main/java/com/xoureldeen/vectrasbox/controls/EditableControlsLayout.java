package com.xoureldeen.vectrasbox.controls;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public class EditableControlsLayout extends FrameLayout {

    private static final String PREFS = "vectrasbox_control_positions_v1";
    private static final String X_PREFIX = "x_";
    private static final String Y_PREFIX = "y_";

    private final RectF hitRect = new RectF();
    private boolean editMode;
    private View activeControl;
    private float dragOffsetX;
    private float dragOffsetY;

    public EditableControlsLayout(Context context) {
        super(context);
        init();
    }

    public EditableControlsLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EditableControlsLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
        setMotionEventSplittingEnabled(true);
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean enabled) {
        editMode = enabled;
        if (!enabled) {
            finishDrag();
        }
    }

    public void resetSavedPositions() {
        preferences().edit().clear().apply();
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            child.setTranslationX(0f);
            child.setTranslationY(0f);
            child.setPressed(false);
        }
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        SharedPreferences prefs = preferences();
        int availableWidth = getWidth();
        int availableHeight = getHeight();

        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            String key = controlKey(child);
            if (key == null) {
                continue;
            }
            String xKey = X_PREFIX + key;
            String yKey = Y_PREFIX + key;
            if (prefs.contains(xKey) && prefs.contains(yKey)) {
                float maxX = Math.max(0f, availableWidth - child.getWidth());
                float maxY = Math.max(0f, availableHeight - child.getHeight());
                child.setX(clamp(prefs.getFloat(xKey, 0f), 0f, 1f) * maxX);
                child.setY(clamp(prefs.getFloat(yKey, 0f), 0f, 1f) * maxY);
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return editMode || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editMode) {
            return super.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeControl = findControlAt(event.getX(), event.getY());
                if (activeControl != null) {
                    dragOffsetX = event.getX() - activeControl.getX();
                    dragOffsetY = event.getY() - activeControl.getY();
                    activeControl.setPressed(true);
                    activeControl.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    activeControl.bringToFront();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activeControl != null) {
                    float maxX = Math.max(0f, getWidth() - activeControl.getWidth());
                    float maxY = Math.max(0f, getHeight() - activeControl.getHeight());
                    activeControl.setX(clamp(event.getX() - dragOffsetX, 0f, maxX));
                    activeControl.setY(clamp(event.getY() - dragOffsetY, 0f, maxY));
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                saveActivePosition();
                finishDrag();
                return true;

            default:
                return true;
        }
    }

    private View findControlAt(float x, float y) {
        for (int index = getChildCount() - 1; index >= 0; index--) {
            View child = getChildAt(index);
            if (child.getVisibility() != VISIBLE || controlKey(child) == null) {
                continue;
            }
            hitRect.set(child.getX(), child.getY(),
                    child.getX() + child.getWidth(), child.getY() + child.getHeight());
            if (hitRect.contains(x, y)) {
                return child;
            }
        }
        return null;
    }

    private void saveActivePosition() {
        if (activeControl == null) {
            return;
        }
        String key = controlKey(activeControl);
        if (key == null) {
            return;
        }
        float maxX = Math.max(1f, getWidth() - activeControl.getWidth());
        float maxY = Math.max(1f, getHeight() - activeControl.getHeight());
        preferences().edit()
                .putFloat(X_PREFIX + key, clamp(activeControl.getX() / maxX, 0f, 1f))
                .putFloat(Y_PREFIX + key, clamp(activeControl.getY() / maxY, 0f, 1f))
                .apply();
    }

    private void finishDrag() {
        if (activeControl != null) {
            activeControl.setPressed(false);
        }
        activeControl = null;
    }

    private String controlKey(View child) {
        Object tag = child.getTag();
        if (tag instanceof String && !((String) tag).isEmpty()) {
            return (String) tag;
        }
        if (child.getId() != View.NO_ID) {
            try {
                return getResources().getResourceEntryName(child.getId());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private SharedPreferences preferences() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
