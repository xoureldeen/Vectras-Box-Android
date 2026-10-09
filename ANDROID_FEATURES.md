# Android and desktop feature coverage

Version: 1.0.1 alpha

PCBox is pinned as an unmodified upstream submodule. Android-specific build
and SDL platform integration is outside that checkout.

## Implemented Android backends

- PCBox machine, CPU, chipset, storage, graphics, and sound-card emulation
  selected by the Android build and upstream CFG
- original cassette emulation for supported machines
- SDL touch/external keyboard and mouse input and GLES2 rendering
- real libslirp 4.9.5 NAT transport, with DNS addresses from the Android
  connection at emulator launch; networking is disabled by default
- SDL playback mixing normal sound, music, wavetable, YM2151, CD audio,
  drive sounds, and MIDI streams with resampling
- Munt MT-32 and CM-32L family synthesis with legally obtained user ROMs

Build availability is not proof that every guest OS or device configuration
has been runtime-tested. A compatible guest NIC driver is still required.

## Remaining desktop differences

- FluidSynth SoundFont synthesis and external MIDI input/output are not built.
- Sound Canvas CLAP plugins require an Android-compatible plugin implementation.
- Microphone capture is not implemented; no microphone permission is requested.
- PCAP bridging, TAP, VDE, VFIO, and host physical drives/ports need OS access or
  hardware-specific integration not available in a normal Android app.
- Qt desktop settings/dialogs are not used. Android supplies its own UI; the
  full CFG editor preserves additional device settings but does not enable
  missing backends.
- VNC hosting, Discord presence, and desktop shader/plugin backends are not built.
- libslirp forwarding to arbitrary host commands is intentionally unavailable.
- Guest DNS forwarding does not implement Android Private DNS encryption.
- Dedicated Android file pickers/UI for every removable device and device-specific
  configuration are not yet complete.

These are known gaps, not claims of full desktop parity. Keeping PCBox as a
submodule does not make missing Android backends available automatically.

## Backend verification

Android arm64 checks on October 10, 2026 passed for:

- libslirp 4.9.5: guest ARP and an IPv4 UDP NAT round trip through a local echo
  socket; Android IPv4/IPv6 DNS address validation and compatibility helpers
- SDL audio: every one of the eight streams in integer and float modes,
  resampling, simultaneous mixing, clipping limits, mute and MIDI-rate changes
- Munt 2.7.0: user-supplied MT-32 and CM-32L ROM pairs, MIDI-note synthesis at
  48 kHz and delivery of nonzero finite output through the SDL MIDI mixer

Audio checks run through Android Java/JNI with SDL's dummy output driver and
verify samples, not physical speaker output. These checks do not establish
guest OS Internet access, all network protocols or device-wide desktop parity.
No existing virtual machine or disk image was modified by the native checks.

The check sources are under `app/src/main/cpp/android/tests`. Native check
targets are disabled in application builds and are not packaged in the APK.
