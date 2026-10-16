package com.beykus.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Downloads one pinned release asset and rejects content that does not match its digest. */
public abstract class DownloadPinnedMihomoTask extends DefaultTask {
    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract Property<String> getSha256();

    @OutputFile
    public abstract RegularFileProperty getArtifact();

    @TaskAction
    public void download() throws IOException {
        var target = getArtifact().get().getAsFile().toPath();
        Files.createDirectories(target.getParent());
        if (Files.isRegularFile(target) && getSha256().get().equals(sha256(target))) return;

        var temporary = target.resolveSibling(target.getFileName() + ".download");
        try {
            String version = getVersion().get();
            String asset = "libmihomo-android-v" + version + ".aar";
            String address = "https://github.com/oviron/libmihomo-android/releases/download/v" + version + "/" + asset;
            URLConnection connection = URI.create(address).toURL().openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(new BufferedInputStream(connection.getInputStream()), digest);
                 BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                input.transferTo(output);
            }

            String actual = hex(digest.digest());
            String expected = getSha256().get().toLowerCase();
            if (!expected.equals(actual)) {
                throw new GradleException("Pinned Mihomo AAR SHA-256 mismatch: expected " + expected + ", got " + actual);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new GradleException("SHA-256 is unavailable", impossible);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(java.nio.file.Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new GradleException("SHA-256 is unavailable", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }
}
