package com.beykus.beygram.media.bgwave

import java.io.File
import java.util.Base64

/** Calls the production JNI wrapper against the pinned BG-Media byte fixtures, never a second wire serializer. */
fun main(args: Array<String>) {
    val fixtures = File(args.single())
    val sid = "102132435465768798a9bacbdcedfe0f"
    for ((index, type) in listOf("offer", "answer", "cancel", "end").withIndex()) {
        val binary = File(fixtures, "$type.bin").readBytes()
        val links = File(fixtures, "$type.uri.txt").readLines().filter { it.isNotBlank() }
        check(links.size == 2)
        for (link in links) {
            val body = if (link.startsWith("bg://")) link.substringAfterLast('/') else link.substringAfter("#bgwave-v1.")
            check(Base64.getUrlDecoder().decode(body).contentEquals(binary))
            val result = BgWaveNative.decodeUri(link, 1763000050L)
            check(result is BgWaveDecodeResult.Ok) { "$type: $result" }
            check(result.type == index + 1 && result.sidHex == sid && result.nonce == (index + 1).toLong())
            check(result.candidateCount == listOf(3, 1, 0, 0)[index])
            if (index >= 2) {
                // Encode through the same C++ API and compare every canonical byte, both wrappers.
                val encoded = BgWaveNative.encodeControl(result.type, result.sidHex, result.issuedAt,
                    result.expiresAt, result.caps, result.nonce, link.startsWith("https://"))
                check(encoded == link) { "canonical encode mismatch: $type" }
            }
        }
    }
    val invalid = fixtures.listFiles()!!.filter { it.name.startsWith("invalid_") && it.extension == "bin" }
    check(invalid.size == 5)
    for (file in invalid) {
        val link = "bg://wave/v1/" + Base64.getUrlEncoder().withoutPadding().encodeToString(file.readBytes())
        check(BgWaveNative.decodeUri(link, 1763000050L) is BgWaveDecodeResult.Rejected) { file.name }
    }
    println("PASS: 8 canonical URI decodes, 4 byte-exact JNI control encodes, 5 rejected binary fixtures")
}
