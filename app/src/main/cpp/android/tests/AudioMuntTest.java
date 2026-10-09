package com.xoureldeen.vectrasbox.tests;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLAudioManager;
import org.libsdl.app.SDLControllerManager;

public final class AudioMuntTest {
    private static native int run(String[] arguments);

    public static void main(String[] arguments) {
        if (arguments.length != 5) throw new IllegalArgumentException("Library folder and four ROM files required");
        System.load(arguments[0] + "/libSDL2.so");
        SDLActivity.nativeSetupJNI();
        SDLAudioManager.nativeSetupJNI();
        SDLControllerManager.nativeSetupJNI();
        System.load(arguments[0] + "/libvectras_audio_munt_test.so");
        System.exit(run(arguments));
    }
}
