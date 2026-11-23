#!/usr/bin/env python3
"""Fail CI if R8 removed or renamed classes required by native TDLib JNI."""

import re
import struct
import sys
import zipfile

REQUIRED = frozenset({
    "Lorg/drinkless/tdlib/Client;",
    "Lorg/drinkless/tdlib/TdApi;",
})


def uint32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def skip_uleb128(data: bytes, offset: int) -> int:
    for _ in range(5):
        value = data[offset]
        offset += 1
        if (value & 0x80) == 0:
            return offset
    raise ValueError("Malformed ULEB128 in DEX string data")


def classes_in_dex(data: bytes) -> set[str]:
    if len(data) < 112 or data[:4] != b"dex\n":
        raise ValueError("Invalid DEX header")
    string_count, string_off = uint32(data, 56), uint32(data, 60)
    type_count, type_off = uint32(data, 64), uint32(data, 68)
    class_count, class_off = uint32(data, 96), uint32(data, 100)
    found: set[str] = set()
    for i in range(class_count):
        type_index = uint32(data, class_off + i * 32)
        if type_index >= type_count:
            raise ValueError("Invalid DEX class type index")
        string_index = uint32(data, type_off + type_index * 4)
        if string_index >= string_count:
            raise ValueError("Invalid DEX class descriptor index")
        value_offset = uint32(data, string_off + string_index * 4)
        start = skip_uleb128(data, value_offset)
        end = data.index(b"\0", start)
        descriptor = data[start:end].decode("utf-8", errors="replace")
        if descriptor in REQUIRED:
            found.add(descriptor)
    return found


def main(apk: str) -> None:
    found: set[str] = set()
    with zipfile.ZipFile(apk) as archive:
        dex_files = [
            name for name in archive.namelist()
            if re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", name)
        ]
        if not dex_files:
            raise ValueError("No classes*.dex in PROF APK")
        for name in dex_files:
            found.update(classes_in_dex(archive.read(name)))
    missing = REQUIRED - found
    if missing:
        print("::error::TDLib JNI class definitions missing after R8: " + ", ".join(sorted(missing)), file=sys.stderr)
        raise SystemExit(1)
    print("Verified TDLib JNI classes in PROF APK DEX: Client and TdApi")


if __name__ == "__main__":
    if len(sys.argv) != 2 or not sys.argv[1]:
        raise SystemExit("Usage: verify-tdlib-dex.py <prof-unsigned.apk>")
    main(sys.argv[1])
