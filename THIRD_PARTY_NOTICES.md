# Third-party notices

Vectras Box includes and modifies open-source components. Copyright notices in
their source files must remain intact.

## PCBox and 86Box

The native emulator core is derived from PCBox, which is based on 86Box and
contains work from their contributors and earlier emulator projects. It is
licensed under GNU GPL version 2 or later.

- PCBox: <https://github.com/PCBox/PCBox>
- 86Box: <https://github.com/86Box/86Box>
- Contributor list: `app/src/main/cpp/86Box/AUTHORS`
- Included license: `app/src/main/cpp/86Box/COPYING`

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

Their exact versions and build sources are declared in
`app/src/main/cpp/CMakeLists.txt`. Their license texts are included in the
source archives fetched by the build.

## Android libraries

Material Components for Android and AndroidX DrawerLayout are distributed
under the Apache License 2.0.

## Additional components

The emulator source contains additional components under compatible licenses,
including libchdr, SoftFloat, MiniVHD, liblzf, Dear ImGui, Ayumi, and reSID-fp.
Their license and copyright files remain alongside their source directories.

The GitHub icon vector is adapted from Font Awesome 5 Free. Font Awesome Free
icons are licensed under CC BY 4.0. GitHub names and marks remain the property
of their respective owners.

## ROMs, firmware and assets

PCBox ROM and asset archives are not part of Vectras Box and are not covered by
the Vectras Box GPL license. They are imported separately by the user and remain
subject to the rights of their respective copyright owners.
