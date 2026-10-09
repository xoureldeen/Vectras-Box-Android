/* Copyright 2026 Vectras LLC. SPDX-License-Identifier: GPL-3.0-only. */
#define SDL_MAIN_HANDLED
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <jni.h>
#include "../sdl_audio.c"
#include <munt/c_interface/c_interface.h>

int sound_gain, sound_muted, sound_is_float, sound_sample_rate = 48000;
int midi_freq = 44100, midi_buf_size = 4410;
unsigned long long src_freqs[I_MAX] = {
    0, MUSIC_FREQ, WT_FREQ, CD_FREQ, 0, 0, YM2151_FREQ, 0
};

#define CHECK(condition) do { if (!(condition)) { \
    fprintf(stderr, "FAIL line %d: %s (%s)\n", __LINE__, #condition, SDL_GetError()); \
    exit(1); } } while (0)

static void clear_streams(void)
{
    for (int source = 0; source < I_MAX; source++)
        SDL_AudioStreamClear(streams[source]);
}

static double output_energy(void)
{
    float output[2048];
    mix_audio(NULL, (Uint8 *) output, sizeof(output));
    double energy = 0;
    for (size_t i = 0; i < SDL_arraysize(output); i++) {
        CHECK(isfinite(output[i]) && fabsf(output[i]) <= 1.0f);
        energy += fabsf(output[i]);
    }
    return energy;
}

static void test_audio(int floating)
{
    sound_is_float = floating;
    inital();
    CHECK(audio_device);
    SDL_PauseAudioDevice(audio_device, 1);
    float floats[16384];
    int16_t integers[16384];
    for (int i = 0; i < 16384; i++) {
        floats[i] = 0.2f;
        integers[i] = 6553;
    }
    for (int source = 0; source < I_MAX; source++) {
        CHECK(streams[source]);
        clear_streams();
        bool float_source = floating || source == I_FDD || source == I_HDD;
        givealbuffer_common(float_source ? (const void *) floats : integers, source, 16384);
        CHECK(output_energy() > 50.0);
    }
    clear_streams();
    for (int source = 0; source < I_MAX; source++) {
        bool float_source = floating || source == I_FDD || source == I_HDD;
        givealbuffer_common(float_source ? (const void *) floats : integers, source, 16384);
    }
    CHECK(output_energy() > 1500.0);
    sound_muted = 1;
    CHECK(output_energy() == 0.0);
    sound_muted = 0;
    al_set_midi(32000, 3200);
    CHECK(streams[I_MIDI] && src_freqs[I_MIDI] == 32000);
    clear_streams();
    givealbuffer_common(floating ? (const void *) floats : integers, I_MIDI, 16384);
    CHECK(output_energy() > 50.0);
    givealbuffer_common(NULL, I_NORMAL, 2);
    givealbuffer_common(floats, I_MAX, 2);
    givealbuffer_common(floats, I_NORMAL, INT_MAX);
    closeal();
    closeal();
    printf("PASS audio: %s, all %d streams, resampling, mix/clamp, mute, MIDI reconfigure\n",
           floating ? "float" : "s16", I_MAX);
}

static void test_munt(const char *control, const char *pcm)
{
    mt32emu_report_handler_i handler = {0};
    mt32emu_context context = mt32emu_create_context(handler, NULL);
    CHECK(context);
    CHECK(mt32emu_open_synth(context) == MT32EMU_RC_MISSING_ROMS);
    CHECK(mt32emu_add_rom_file(context, control) == MT32EMU_RC_ADDED_CONTROL_ROM);
    CHECK(mt32emu_add_rom_file(context, pcm) == MT32EMU_RC_ADDED_PCM_ROM);
    mt32emu_set_stereo_output_samplerate(context, 48000.0);
    CHECK(mt32emu_open_synth(context) == MT32EMU_RC_OK);
    CHECK(mt32emu_is_open(context));
    CHECK(mt32emu_get_actual_stereo_output_samplerate(context) == 48000);
    mt32emu_play_short_message(context, 0x00643c91);
    sound_is_float = 1;
    al_set_midi(48000, 4800);
    inital();
    CHECK(audio_device);
    SDL_PauseAudioDevice(audio_device, 1);
    double synth_energy = 0, mixed_energy = 0;
    for (int chunk = 0; chunk < 24; chunk++) {
        float samples[2048];
        mt32emu_render_float(context, samples, 1024);
        for (size_t i = 0; i < SDL_arraysize(samples); i++) {
            CHECK(isfinite(samples[i]));
            synth_energy += fabsf(samples[i]);
        }
        givealbuffer_common(samples, I_MIDI, 2048);
        mixed_energy += output_energy();
    }
    CHECK(synth_energy > 0.01 && mixed_energy > 0.01);
    mt32emu_play_short_message(context, 0x00003c81);
    mt32emu_close_synth(context);
    mt32emu_free_context(context);
    closeal();
    printf("PASS Munt %s: ROM pair, note synthesis at 48kHz, SDL MIDI mix (energy %.2f/%.2f)\n",
           mt32emu_get_library_version_string(), synth_energy, mixed_energy);
}

static int run_tests(int argc, const char **argv)
{
    SDL_SetMainReady();
    SDL_setenv("SDL_AUDIODRIVER", "dummy", 1);
    test_audio(0);
    test_audio(1);
    if (argc != 5) {
        fprintf(stderr, "Usage: audio_munt_test MT32_CONTROL MT32_PCM CM32L_CONTROL CM32L_PCM\n");
        return 2;
    }
    test_munt(argv[1], argv[2]);
    test_munt(argv[3], argv[4]);
    SDL_Quit();
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_xoureldeen_vectrasbox_tests_AudioMuntTest_run(JNIEnv *env, jclass type, jobjectArray arguments)
{
    (void) type;
    CHECK((*env)->GetArrayLength(env, arguments) == 5);
    jstring strings[5];
    const char *values[5];
    for (int i = 0; i < 5; i++) {
        strings[i] = (*env)->GetObjectArrayElement(env, arguments, i);
        values[i] = (*env)->GetStringUTFChars(env, strings[i], NULL);
        CHECK(values[i]);
    }
    int result = run_tests(5, values);
    for (int i = 0; i < 5; i++) {
        (*env)->ReleaseStringUTFChars(env, strings[i], values[i]);
        (*env)->DeleteLocalRef(env, strings[i]);
    }
    fflush(stdout);
    return result;
}
