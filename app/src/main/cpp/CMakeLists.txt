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

# Locate BG Media sources
set(BGMEDIA_PINNED_COMMIT "a4d3821c11063684fae754581ba96128528747f1")

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/bg-media/CMakeLists.txt")
    set(BGMEDIA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/bg-media")
elseif(DEFINED BGMEDIA_ROOT AND EXISTS "${BGMEDIA_ROOT}/CMakeLists.txt")
    set(BGMEDIA_DIR "${BGMEDIA_ROOT}")
elseif(EXISTS "F:/BG Media/CMakeLists.txt")
    set(BGMEDIA_DIR "F:/BG Media")
else()
    include(FetchContent)
    FetchContent_Declare(
        bgmedia
        GIT_REPOSITORY https://github.com/Beykus-Y/BG-Media.git
        GIT_TAG        ${BGMEDIA_PINNED_COMMIT}
        GIT_SHALLOW    TRUE
    )
    FetchContent_MakeAvailable(bgmedia)
    set(BGMEDIA_DIR "${bgmedia_SOURCE_DIR}")
endif()

if(NOT TARGET bgmedia_core)
    add_subdirectory("${BGMEDIA_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/bgmedia" EXCLUDE_FROM_ALL)
    if(TARGET bgmedia_miniaudio)
        target_compile_definitions(bgmedia_miniaudio PRIVATE MA_NO_DEVICE_IO)
    endif()
endif()

add_library(bgbeat_jni SHARED
    bgbeat_jni.cpp
)

target_include_directories(bgbeat_jni PRIVATE
    "${BGMEDIA_DIR}/include"
    "${BGMEDIA_DIR}/adapters/miniaudio/include"
)

target_link_libraries(bgbeat_jni PRIVATE
    bgmedia_core
    bgmedia_miniaudio
    android
    log
)

target_compile_features(bgbeat_jni PRIVATE cxx_std_20)
target_compile_options(bgbeat_jni PRIVATE -fexceptions -frtti -Wall -Wextra)
target_compile_definitions(bgbeat_jni PRIVATE MA_NO_DEVICE_IO)
