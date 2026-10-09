# Third-party notices

Vectras Box includes and modifies open-source components. Copyright notices in
their source files must remain intact.

## Vectras-owned code

Vectras-owned code is licensed under GNU GPL version 3 only (`GPL-3.0-only`).
The full text is in the repository-root `COPYING` and `licenses/gpl-3.0.txt`.
This license declaration does not replace upstream file-specific notices or
change rights granted for previously distributed versions.

## PCBox and 86Box

The native emulator core comes from PCBox, which is based on 86Box and
contains work from their contributors and earlier emulator projects. It is
licensed under GNU GPL version 2 or later.

- PCBox: <https://github.com/PCBox/PCBox>
- 86Box: <https://github.com/86Box/86Box>
- Contributor list: `app/src/main/cpp/86Box/AUTHORS`
- Included license: `app/src/main/cpp/86Box/COPYING`
- Unmodified upstream submodule: `app/src/main/cpp/86Box`
- Pinned revision: `51394140ed2995f93245f76e90a6c69b3d5d0908`

Vectras Box is independently maintained and is not an official or endorsed
release of PCBox or 86Box.

## SDL

SDL is distributed under the zlib license. Its license is included at
`app/src/main/cpp/SDL2/LICENSE.txt`.

## Native build dependencies

The native build downloads pinned versions of the following components:

- zlib 1.3.2, under the zlib license: <https://github.com/madler/zlib>
- libpng 1.6.53, under the libpng license: <https://github.com/pnggroup/libpng>
- FreeType 2.13.3, under the FreeType License or GNU GPL version 2:
  <https://gitlab.freedesktop.org/freetype/freetype>
- libsndfile 1.2.2, under GNU LGPL version 2.1 or later:
  <https://github.com/libsndfile/libsndfile>
- libslirp 4.9.5, under its file-specific BSD and MIT notices:
  <https://gitlab.freedesktop.org/slirp/libslirp>.
  The Android build includes a Vectras-owned GPL-3.0-only compatibility layer
  for the GLib calls used by libslirp, not a copy of GLib.

Their exact versions and build sources are declared in
`app/src/main/cpp/CMakeLists.txt`. Copies of their license texts are kept in
`licenses/` and packaged in the application. FreeType is used under its GNU
GPL option, not the alternative FreeType License. HIDAPI is used under its
BSD option. Alternative license notices are retained for reference.

The Android build generates `native-source-attributions.txt` from copyright
and license blocks in the bundled native sources. Notices from the pinned
downloaded dependencies are preserved in
`licenses/native-dependency-attributions.txt`.

## Android libraries

Material Components for Android and AndroidX DrawerLayout are distributed
under the Apache License 2.0.

Apache License 2.0 is compatible with GPL version 3 for a combined work.
The Android UI uses these libraries and Apache-licensed Material Symbols assets.
Their original notices and license texts remain included.

Reference: <https://apache.org/licenses/GPL-compatibility.html>.

## Additional components

The emulator source contains additional components under compatible licenses,
including libchdr, SoftFloat, MiniVHD, liblzf, Dear ImGui, Ayumi, and reSID-fp.
Their license and copyright files remain alongside their source directories.

The GitHub icon vector is adapted from the Font Awesome 5 Free GitHub icon by
Fonticons, Inc.: <https://github.com/FortAwesome/Font-Awesome/tree/5.x>.
The SVG path was converted to an Android vector and uses an Android theme
color. Font Awesome Free icons are licensed under CC BY 4.0:
<https://creativecommons.org/licenses/by/4.0/>. The license and attribution
are included in `licenses/` and the application. GitHub names and marks remain
the property of their respective owners.

The large computer and search icons use Material Symbols paths from Google,
adapted to Android vector drawables, under Apache License 2.0:
<https://github.com/google/material-design-icons>.

## Android integration and cassette support

The PCBox submodule is an unmodified checkout of the upstream repository.
Android-specific SDL frontend, JNI, audio, and networking adapters are kept in
`app/src/main/cpp/android`. Derived SDL frontend sources retain their upstream
notices and identify Vectras's platform modifications.

`app/src/main/cpp/cmake/PCBoxAndroid.cmake` copies the upstream source into
the ignored native build directory, adapts its CMake scripts for Android,
and selects the separate Android frontend. The submodule's tracked files are
not edited. Emulation sources, including cassette support, are compiled from
the upstream source without the former cassette-exclusion changes.
SLiRP uses the real libslirp NAT backend. Android DNS server discovery is
supplied outside the core. The compatibility layer intentionally rejects
guest forwarding to host commands; it does not provide a general GLib API.
Munt MT-32/CM-32L synthesis is built from the upstream PCBox sources under
their retained licenses and requires user-supplied ROMs. The Android SDL
audio backend mixes the core's normal, music, CD, drive, and MIDI streams.

PCBox's overall project notice says GPL version 2 or later.
`app/src/main/cpp/86Box/src/device/cassette.c` and
`app/src/main/cpp/86Box/src/include/86box/cassette.h` specify GPL version 2
without an "or later" option. Their original notices remain intact, and
cassette emulation is included for supported machines. Cassette CFG keys are
handled by upstream PCBox rather than removed.

A Git submodule does not remove license or corresponding-source obligations.
The source-release task includes the checked-out PCBox source and Android
integration. The GPL-3.0-only declaration applies to Vectras-owned code and
does not change upstream permissions. GPL version 2-only cassette code is not
compatible with GPL version 3 in a combined work. Permission from the relevant
copyright holders or another compatible implementation is needed before
distributing the combined APK with cassette support. The unmodified submodule
is not a license exception.

Reference: <https://www.gnu.org/licenses/quick-guide-gplv3.html>.

## ROMs, firmware and assets

PCBox ROM and asset archives are not part of Vectras Box and are not covered by
the Vectras Box GPL license. They are imported separately by the user and remain
subject to the rights of their respective copyright owners.
