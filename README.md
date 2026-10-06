# Vectras Box

Vectras Box is an Android PC emulator based on PCBox, which is based on 86Box.
It is independently maintained by xoureldeen (me) and is not an official or
endorsed PCBox or 86Box release.

## Features

- PCBox-based x86 machine emulation on arm64 Android devices
- local virtual-machine creation, editing, cloning, and deletion
- raw hard-disk, ISO, floppy, configuration, and NVR import tools
- customizable touch controls and external input support
- validated PCBox ROM and asset ZIP import during first-run setup

## Requirements

- Android 7.0 or newer
- arm64-v8a device
- PCBox-compatible ROM and asset files that you are legally permitted to use

ROMs, firmware, operating systems, and disk images are not included.

## Build

Open the project in Android Studio with JDK 17, Android SDK 35, CMake 3.22.1,
and Android NDK 26.1.10909125 installed. Native dependency versions are pinned
in `app/src/main/cpp/CMakeLists.txt` and are fetched during the native build.

From the repository root:

```text
./gradlew assembleDebug
```

The application ID is `com.xoureldeen.vectrasbox`.

## First launch

The setup screen asks for a PCBox ROM ZIP and assets ZIP. Each archive is
checked before extraction. Select only packages obtained from a lawful source.
After both packages pass validation, the machine library becomes available.

## License and notices

Vectras Box is open-source software under the GNU GPL version 2 or later. See
[`COPYING`](COPYING) for the license and
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for component attribution.

- [Privacy Policy](PRIVACYANDPOLICY.md)
- [Terms and Conditions](TERMSANDCONDITIONS.md)
- [PCBox](https://github.com/PCBox/PCBox)
- [86Box](https://github.com/86Box/86Box)

When publishing an APK or AAB, publish the complete corresponding source for
that exact release and preserve all upstream copyright and license notices.

## Support

Use the repository issue tracker for reproducible bugs. Do not upload ROMs,
firmware, operating-system images, license keys, or private virtual-machine
files to an issue.
