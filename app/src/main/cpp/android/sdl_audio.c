/* Copyright 2026 Vectras LLC. SPDX-License-Identifier: GPL-3.0-only. */
#include <stdint.h>
#include <stddef.h>
#include <limits.h>
#include <math.h>
#include <SDL.h>
#include <86box/86box.h>
#include <86box/midi.h>
#include <86box/sound.h>

static SDL_AudioDeviceID audio_device;
static SDL_AudioStream *streams[I_MAX];
static SDL_AudioFormat source_format;

static void
mix_audio(void *opaque, Uint8 *output, int bytes)
{
    (void) opaque;
    SDL_memset(output, 0, bytes);
    const float gain = sound_muted ? 0.0f : powf(10.0f, sound_gain / 20.0f);
    float samples[2048];
    for (int source = 0; source < I_MAX; source++) {
        if (!streams[source])
            continue;
        int offset = 0;
        while (offset < bytes) {
            int requested = bytes - offset;
            if (requested > (int) sizeof(samples))
                requested = sizeof(samples);
            int received = SDL_AudioStreamGet(streams[source], samples, requested);
            if (received <= 0)
                break;
            float *destination = (float *) (output + offset);
            for (int i = 0; i < received / (int) sizeof(float); i++)
                destination[i] += samples[i] * gain;
            offset += received;
        }
    }
    float *destination = (float *) output;
    for (int i = 0; i < bytes / (int) sizeof(float); i++)
        destination[i] = fmaxf(-1.0f, fminf(1.0f, destination[i]));
}

const char *sound_get_output_devices(void) { return "Android audio\0\0"; }
const char *sound_get_input_devices(void) { return NULL; }
int sound_get_device_sample_rate(const char *device_name)
{
    (void) device_name;
    return 48000;
}
int sound_get_device_supported_rates(const char *device_name, int *rates_out, int maximum)
{
    (void) device_name;
    if (!rates_out || maximum <= 0)
        return 0;
    rates_out[0] = 48000;
    if (maximum > 1) {
        rates_out[1] = 44100;
        return 2;
    }
    return 1;
}

void
al_set_midi(const int frequency, const int buffer_size)
{
    midi_freq = frequency;
    midi_buf_size = buffer_size;
    src_freqs[I_MIDI] = frequency;
    if (audio_device) {
        SDL_LockAudioDevice(audio_device);
        if (streams[I_MIDI])
            SDL_FreeAudioStream(streams[I_MIDI]);
        streams[I_MIDI] = SDL_NewAudioStream(source_format, 2, frequency, AUDIO_F32SYS, 2, 48000);
        SDL_UnlockAudioDevice(audio_device);
    }
}

void
inital(void)
{
    if (audio_device)
        return;
    src_freqs[I_NORMAL] = src_freqs[I_FDD] = src_freqs[I_HDD] = sound_sample_rate;
    src_freqs[I_MIDI] = midi_freq;
    source_format = sound_is_float ? AUDIO_F32SYS : AUDIO_S16SYS;
    if (SDL_InitSubSystem(SDL_INIT_AUDIO) != 0)
        return;
    SDL_AudioSpec wanted = {0};
    wanted.freq = 48000;
    wanted.format = AUDIO_F32SYS;
    wanted.channels = 2;
    wanted.samples = 1024;
    wanted.callback = mix_audio;
    audio_device = SDL_OpenAudioDevice(NULL, 0, &wanted, NULL, 0);
    if (!audio_device)
        return;
    for (int source = 0; source < I_MAX; source++) {
        SDL_AudioFormat format = (source == I_FDD || source == I_HDD) ? AUDIO_F32SYS : source_format;
        streams[source] = SDL_NewAudioStream(format, 2, (int) src_freqs[source], AUDIO_F32SYS, 2, 48000);
    }
    SDL_PauseAudioDevice(audio_device, 0);
}

void
closeal(void)
{
    if (audio_device) {
        SDL_CloseAudioDevice(audio_device);
        audio_device = 0;
    }
    for (int source = 0; source < I_MAX; source++) {
        if (streams[source])
            SDL_FreeAudioStream(streams[source]);
        streams[source] = NULL;
    }
}

void
givealbuffer_common(const void *buffer, const uint8_t source, const int size)
{
    if (!audio_device || !buffer || source >= I_MAX || size <= 0)
        return;
    const int sample_bytes = (source == I_FDD || source == I_HDD || source_format == AUDIO_F32SYS)
            ? sizeof(float) : sizeof(int16_t);
    if (size > INT_MAX / sample_bytes)
        return;
    SDL_LockAudioDevice(audio_device);
    if (streams[source]) {
        if (SDL_AudioStreamAvailable(streams[source]) > 48000 * 2 * (int) sizeof(float))
            SDL_AudioStreamClear(streams[source]);
        SDL_AudioStreamPut(streams[source], buffer, size * sample_bytes);
    }
    SDL_UnlockAudioDevice(audio_device);
}

void al_capture_open(void) { }
void al_capture_close(void) { }
int al_capture_get_rate(void) { return 0; }
int al_capture_available(void) { return 0; }
void al_capture_start(void) { }
void al_capture_stop(void) { }
void al_capture_get_data(int16_t *buffer, size_t *length)
{
    (void) buffer;
    if (length)
        *length = 0;
}
