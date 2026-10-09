<p align="center">
  <img src="app/src/main/play_store_512.png" alt="Vectras Box logo" width="160">
</p>

# Vectras Box

[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-26A5E4?logo=telegram&logoColor=white)](https://t.me/vectras_box)
[![Telegram Chat](https://img.shields.io/badge/Telegram-Chat-26A5E4?logo=telegram&logoColor=white)](https://t.me/vectras_box_chat)

Vectras Box is an Android PC emulator based on [PCBox](https://github.com/PCBox/PCBox)
and [86Box](https://github.com/86Box/86Box). It is an independent project, not an
official PCBox or 86Box release.

## Features

- Create, edit, clone, and delete virtual machines.
- Use existing hard disks, ISO images, and floppy images.
- Configure machine hardware with a Material 3 editor and full CFG editing.
- Touch controls, keyboard and mouse support, optional NAT networking, and Munt MIDI synthesis.

## Requirements

- Android 7.0 or newer on an ARM64 device.
- PCBox-compatible ROM and asset packages that you are legally permitted to use.

ROMs, firmware, operating systems, and disk images are not included.

## Getting started

Install the APK from [Releases](https://github.com/xoureldeen/Vectras-Box-Android/releases).
On first launch, import your PCBox ROM ZIP and assets ZIP, then create a machine
and select your installation media. Both archives are checked before extraction.

This project is experimental; some desktop features are unavailable on Android.

## Build from source

Open the project in Android Studio with JDK 17, Android SDK 35, CMake 3.22.1,
and Android NDK 26.1.10909125 installed.

```text
git clone --recurse-submodules https://github.com/xoureldeen/Vectras-Box-Android.git
cd Vectras-Box-Android
./gradlew assembleDebug
```

## License and notices

Vectras-owned code is licensed under [GPL-3.0-only](COPYING). Third-party code
retains its own licenses. See [Third-party notices](THIRD_PARTY_NOTICES.md)
for attribution, license terms, and distribution restrictions.

- [Privacy Policy](PRIVACYANDPOLICY.md)
- [Terms and Conditions](TERMSANDCONDITIONS.md)

## Support

Report reproducible bugs through [Issues](https://github.com/xoureldeen/Vectras-Box-Android/issues).
Do not attach ROMs, firmware, operating-system images, license keys, or private machine files.
