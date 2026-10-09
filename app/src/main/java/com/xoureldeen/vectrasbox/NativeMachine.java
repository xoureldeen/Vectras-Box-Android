package com.xoureldeen.vectrasbox;

public final class NativeMachine {
    public static final int MOUNT_CD = 1;
    public static final int EJECT_CD = 2;
    public static final int RESET = 3;
    public static final int STOP = 4;

    private NativeMachine() { }

    public static native boolean request(int command, String absolutePath);

    public static native int result();
}
