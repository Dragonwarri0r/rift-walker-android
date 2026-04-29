package com.google.mlkit.vision.common;

import java.util.Arrays;

public final class InputImage {
    public static final int IMAGE_FORMAT_NV21 = 17;

    private final byte[] bytes;
    private final int width;
    private final int height;
    private final int rotationDegrees;
    private final int format;

    private InputImage(byte[] bytes, int width, int height, int rotationDegrees, int format) {
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.width = width;
        this.height = height;
        this.rotationDegrees = rotationDegrees;
        this.format = format;
    }

    public static InputImage fromByteArray(
        byte[] bytes,
        int width,
        int height,
        int rotationDegrees,
        int format
    ) {
        return new InputImage(bytes, width, height, rotationDegrees, format);
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public int getFormat() {
        return format;
    }

    public int checksum() {
        int checksum = 0;
        for (byte value : bytes) {
            checksum = (checksum + (value & 0xff)) % 65535;
        }
        return checksum;
    }

    public String summary() {
        return "InputImage(width=" + width +
            ", height=" + height +
            ", rotationDegrees=" + rotationDegrees +
            ", format=" + format +
            ", bytes=" + bytes.length +
            ", checksum=" + checksum() +
            ")";
    }
}
