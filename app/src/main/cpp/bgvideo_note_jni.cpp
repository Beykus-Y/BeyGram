#include "bgmedia/video/ByteSource.hpp"

#include <jni.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <string>

namespace {
using namespace bgmedia::video;
using Handle = std::shared_ptr<ByteSource>;

Handle* handle(jlong address) {
    return reinterpret_cast<Handle*>(address);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_playback_BgVideoNoteNative_open(JNIEnv* env, jobject, jbyteArray utf8) {
    try {
        if (!utf8) return 0;
        std::u8string path(static_cast<std::size_t>(env->GetArrayLength(utf8)), u8'\0');
        env->GetByteArrayRegion(utf8, 0, static_cast<jsize>(path.size()),
                                reinterpret_cast<jbyte*>(path.data()));
        if (env->ExceptionCheck() || path.find(u8'\0') != std::u8string::npos) return 0;
        return reinterpret_cast<jlong>(new Handle(openFileByteSource(std::filesystem::path(path))));
    } catch (...) { return 0; }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_beykus_beygram_playback_BgVideoNoteNative_size(JNIEnv*, jobject, jlong address) {
    return address ? static_cast<jlong>((*handle(address))->size()) : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_beykus_beygram_playback_BgVideoNoteNative_read(
    JNIEnv* env, jobject, jlong address, jlong position, jbyteArray target, jint offset, jint length) {
    if (!address || position < 0 || !target || offset < 0 || length <= 0 ||
        offset > env->GetArrayLength(target) || length > env->GetArrayLength(target) - offset) return -1;
    try {
        std::array<std::uint8_t, 64 * 1024> bytes;
        const auto count = std::min<std::size_t>(bytes.size(), static_cast<std::size_t>(length));
        (*handle(address))->readAt(static_cast<std::uint64_t>(position), std::span(bytes).first(count),
            {{}, std::chrono::steady_clock::now() + std::chrono::seconds(2)});
        env->SetByteArrayRegion(target, offset, static_cast<jsize>(count), reinterpret_cast<const jbyte*>(bytes.data()));
        return env->ExceptionCheck() ? -1 : static_cast<jint>(count);
    } catch (...) { return -1; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_beykus_beygram_playback_BgVideoNoteNative_close(JNIEnv*, jobject, jlong address) {
    delete handle(address);
}
