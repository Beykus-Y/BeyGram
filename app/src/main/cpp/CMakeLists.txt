cmake_minimum_required(VERSION 3.22.1)
project(bgbeat_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Build options for BG Media
set(BGMEDIA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BGMEDIA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(BGMEDIA_BUILD_MINIAUDIO ON CACHE BOOL "" FORCE)
set(BGMEDIA_BUILD_DESKTOP OFF CACHE BOOL "" FORCE)
set(BGMEDIA_WARNINGS_AS_ERRORS OFF CACHE BOOL "" FORCE)

# Pinned commit of BG Media main (BG Mix 0.3 MixSession, BG Beat, PcmTrack resampler, PR #65 mix fixes). Bump deliberately.
set(BGMEDIA_PINNED_COMMIT "2368c132660d73ee976ec3a50b67249417938a7f")

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/bg-media/CMakeLists.txt")
    set(BGMEDIA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/bg-media")
    set(HAVE_BGMEDIA TRUE)
elseif(DEFINED BGMEDIA_ROOT AND EXISTS "${BGMEDIA_ROOT}/CMakeLists.txt")
    set(BGMEDIA_DIR "${BGMEDIA_ROOT}")
    set(HAVE_BGMEDIA TRUE)
else()
    set(BGMEDIA_AUTH_TOKEN "$ENV{GH_TOCKEN}")
    if(NOT BGMEDIA_AUTH_TOKEN)
        set(BGMEDIA_AUTH_TOKEN "$ENV{GH_TOKEN}")
    endif()
    if(NOT BGMEDIA_AUTH_TOKEN)
        set(BGMEDIA_AUTH_TOKEN "$ENV{BG_MEDIA_TOKEN}")
    endif()

    if(BGMEDIA_AUTH_TOKEN)
        include(FetchContent)
        # The token reaches git through an insteadOf rule in the environment. The repository URL that FetchContent
        # writes into the build tree stays free of it.
        set(ENV{GIT_CONFIG_COUNT} "1")
        set(ENV{GIT_CONFIG_KEY_0} "url.https://${BGMEDIA_AUTH_TOKEN}@github.com/.insteadOf")
        set(ENV{GIT_CONFIG_VALUE_0} "https://github.com/")
        FetchContent_Declare(
            bgmedia
            GIT_REPOSITORY "https://github.com/Beykus-Y/BG-Media.git"
            GIT_TAG        ${BGMEDIA_PINNED_COMMIT}
            GIT_SHALLOW    TRUE
        )
        FetchContent_MakeAvailable(bgmedia)
        set(BGMEDIA_DIR "${bgmedia_SOURCE_DIR}")
        set(HAVE_BGMEDIA TRUE)
    else()
        message(WARNING "BG Media submodule not found and no access token provided in secrets. Building stub bgbeat_jni.")
        set(HAVE_BGMEDIA FALSE)
    endif()
endif()

add_library(bgbeat_jni SHARED
    bgbeat_jni.cpp
    bgmix_jni.cpp
)

target_compile_features(bgbeat_jni PRIVATE cxx_std_20)
target_compile_options(bgbeat_jni PRIVATE -fexceptions -frtti -Wall -Wextra)

if(HAVE_BGMEDIA)
    if(NOT TARGET bgmedia_core)
        # bgmedia_miniaudio keeps miniaudio device IO enabled: MiniaudioMixDevice.cpp needs ma_device,
        # which MA_NO_DEVICE_IO removes. bgbeat_jni.cpp still defines it for its own translation unit.
        add_subdirectory("${BGMEDIA_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/bgmedia" EXCLUDE_FROM_ALL)
    endif()

    target_include_directories(bgbeat_jni PRIVATE
        "${BGMEDIA_DIR}/include"
        "${BGMEDIA_DIR}/adapters/miniaudio/include"
        "${BGMEDIA_DIR}/third_party/miniaudio"
    )

    target_link_libraries(bgbeat_jni PRIVATE
        bgmedia_core
        bgmedia_miniaudio
        android
        log
    )
    target_compile_definitions(bgbeat_jni PRIVATE HAVE_BGMEDIA=1 MA_NO_DEVICE_IO)
else()
    target_link_libraries(bgbeat_jni PRIVATE
        android
        log
    )
    target_compile_definitions(bgbeat_jni PRIVATE HAVE_BGMEDIA=0)
endif()
