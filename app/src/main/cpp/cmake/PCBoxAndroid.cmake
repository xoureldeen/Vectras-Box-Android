function(vectras_pcbox_replace relative before after)
    set(path "${PCBOX_ANDROID_SOURCE}/${relative}")
    file(READ "${path}" content)
    string(REPLACE "\r\n" "\n" content "${content}")
    string(FIND "${content}" "${before}" offset)
    if(offset EQUAL -1)
        message(FATAL_ERROR "PCBox Android integration needs updating: ${relative}")
    endif()
    string(REPLACE "${before}" "${after}" content "${content}")
    string(PREPEND content "# Android build integration by Vectras LLC, 2026-10-10. Upstream notices retained.\n")
    file(WRITE "${path}" "${content}")
endfunction()

function(vectras_add_pcbox)
    set(upstream "${CMAKE_CURRENT_SOURCE_DIR}/86Box")
    if(NOT EXISTS "${upstream}/src/device/cassette.c")
        message(FATAL_ERROR "PCBox submodule is missing. Run git submodule update --init --recursive.")
    endif()
    set(PCBOX_ANDROID_SOURCE "${CMAKE_CURRENT_BINARY_DIR}/pcbox-android")
    set(android_port "${CMAKE_CURRENT_SOURCE_DIR}/android")
    file(MAKE_DIRECTORY "${PCBOX_ANDROID_SOURCE}")
    file(COPY "${upstream}/" DESTINATION "${PCBOX_ANDROID_SOURCE}" PATTERN ".git" EXCLUDE)

    vectras_pcbox_replace(src/CMakeLists.txt
        "add_executable(PCBox"
        "add_library(PCBox SHARED")
    vectras_pcbox_replace(src/CMakeLists.txt
        "find_package(Freetype REQUIRED)"
        "set(FREETYPE_INCLUDE_DIRS \"${freetype_SOURCE_DIR}/include\")")
    vectras_pcbox_replace(src/CMakeLists.txt
        "find_package(PNG REQUIRED)"
        "set(PNG_INCLUDE_DIRS \"${libpng_SOURCE_DIR}\" \"${libpng_BINARY_DIR}\")")
    vectras_pcbox_replace(src/cdrom/CMakeLists.txt
        "set(WITH_SYSTEM_ZLIB ON)"
        "set(WITH_SYSTEM_ZLIB OFF CACHE BOOL \"\" FORCE)")
    vectras_pcbox_replace(src/cdrom/CMakeLists.txt
        "set(WITH_SYSTEM_ZSTD ON)"
        "set(WITH_SYSTEM_ZSTD OFF CACHE BOOL \"\" FORCE)")
    vectras_pcbox_replace(src/cdrom/CMakeLists.txt
        "find_package(PkgConfig REQUIRED)\n\npkg_check_modules(SNDFILE REQUIRED IMPORTED_TARGET sndfile)"
        "")
    vectras_pcbox_replace(src/cdrom/CMakeLists.txt
        "target_include_directories(PCBox PRIVATE PkgConfig::SNDFILE)"
        "target_link_libraries(cdrom PkgConfig::SNDFILE)")
    vectras_pcbox_replace(src/printer/CMakeLists.txt
        "find_package(PkgConfig REQUIRED)\npkg_check_modules(FREETYPE REQUIRED IMPORTED_TARGET freetype2)"
        "")
    vectras_pcbox_replace(src/network/CMakeLists.txt
        "find_package(PkgConfig REQUIRED)\npkg_check_modules(SLIRP REQUIRED IMPORTED_TARGET slirp)\ntarget_link_libraries(PCBox PkgConfig::SLIRP)"
        "")
    vectras_pcbox_replace(src/sound/CMakeLists.txt
        "if(AUDIO4)"
        "if(ANDROID)\n    target_sources(snd PRIVATE \"${android_port}/sdl_audio.c\")\n    target_link_libraries(snd PRIVATE SDL2::SDL2)\n    target_link_libraries(PCBox SDL2::SDL2)\nelseif(AUDIO4)")
    vectras_pcbox_replace(src/sound/CMakeLists.txt
        "find_package(PkgConfig)\nif(APPLE)\n    pkg_check_modules(SERIALPORT IMPORTED_TARGET libserialport)\nelse()\n    pkg_check_modules(SERIALPORT libserialport)\nendif()"
        "")
    vectras_pcbox_replace(src/unix/CMakeLists.txt
        "find_package(SDL2 REQUIRED)"
        "set(SDL2_INCLUDE_DIRS \"${CMAKE_CURRENT_SOURCE_DIR}/SDL2/include\")\n    set(SDL2_LIBRARIES SDL2::SDL2)")
    vectras_pcbox_replace(src/unix/CMakeLists.txt
        [=[${CMAKE_SOURCE_DIR}/src/osd]=]
        "${PCBOX_ANDROID_SOURCE}/src/osd")

    foreach(frontend sdl_main.c sdl_plat.c sdl_plat_unix.c)
        configure_file("${android_port}/${frontend}" "${PCBOX_ANDROID_SOURCE}/src/unix/${frontend}" COPYONLY)
    endforeach()
    configure_file("${android_port}/vectras_android.h" "${PCBOX_ANDROID_SOURCE}/src/unix/vectras_android.h" COPYONLY)

    set(CMAKE_SOURCE_DIR "${PCBOX_ANDROID_SOURCE}")
    add_subdirectory("${PCBOX_ANDROID_SOURCE}" "${CMAKE_CURRENT_BINARY_DIR}/86Box")
    target_sources(plat PRIVATE "${android_port}/vectras_android.c")
    target_include_directories(plat PRIVATE "${android_port}")
    target_link_libraries(plat SDL2::SDL2)
    target_link_libraries(ui PRIVATE SDL2::SDL2)
    target_link_libraries(osd_backend PRIVATE SDL2::SDL2)
    target_link_libraries(vid PRIVATE PNG::PNG)
    target_link_libraries(net PRIVATE vectras_slirp)
    target_link_libraries(PCBox vectras_slirp)
    target_link_options(PCBox PRIVATE "-Wl,--wrap=get_dns_addr" "-Wl,--wrap=get_dns6_addr")
    target_link_libraries(PCBox SDL2main android log)
    set_target_properties(PCBox PROPERTIES OUTPUT_NAME main)
endfunction()
