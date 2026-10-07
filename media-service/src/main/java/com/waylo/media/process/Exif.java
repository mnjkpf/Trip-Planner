package com.waylo.media.process;

/**
 * Мінімальний читач EXIF-орієнтації JPEG — рівно один тег (0x0112) і жодної
 * залежності заради нього.
 *
 * Навіщо: браузер сам повертає фото за EXIF, а ось мініатюра метадані втрачає.
 * Без цього всі горизонтальні знімки з телефона лягли б набік.
 */
public final class Exif {

    public static final int NORMAL = 1;
    private static final int ORIENTATION_TAG = 0x0112;

    private Exif() {
    }

    /** @return 1..8 за специфікацією EXIF; 1, якщо тега немає або дані пошкоджені. */
    public static int orientation(byte[] jpeg) {
        try {
            return read(jpeg);
        } catch (RuntimeException ex) {
            return NORMAL;      // биті метадані не привід втрачати фото
        }
    }

    private static int read(byte[] b) {
        if (b.length < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) {
            return NORMAL;      // не JPEG
        }
        int p = 2;
        while (p + 4 <= b.length) {
            if (u8(b, p) != 0xFF) {
                return NORMAL;  // розсинхрон у потоці сегментів
            }
            int marker = u8(b, p + 1);
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                p += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                return NORMAL;  // почались дані зображення — EXIF тут уже не буде
            }
            int length = (u8(b, p + 2) << 8) | u8(b, p + 3);
            if (length < 2 || p + 2 + length > b.length) {
                return NORMAL;
            }
            if (marker == 0xE1 && length >= 14 && "Exif".equals(ascii(b, p + 4, 4))) {
                return fromTiff(b, p + 10, p + 2 + length);
            }
            p += 2 + length;
        }
        return NORMAL;
    }

    /** TIFF-заголовок: порядок байтів, 42, зсув до IFD0; у IFD0 шукаємо тег орієнтації. */
    private static int fromTiff(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) {
            return NORMAL;
        }
        boolean little = u8(b, tiff) == 0x49 && u8(b, tiff + 1) == 0x49;
        boolean big = u8(b, tiff) == 0x4D && u8(b, tiff + 1) == 0x4D;
        if (!little && !big) {
            return NORMAL;
        }
        int ifd = tiff + (int) u32(b, tiff + 4, little);
        if (ifd + 2 > end) {
            return NORMAL;
        }
        int count = u16(b, ifd, little);
        int entry = ifd + 2;
        for (int i = 0; i < count && entry + 12 <= end; i++, entry += 12) {
            if (u16(b, entry, little) == ORIENTATION_TAG) {
                int value = u16(b, entry + 8, little);   // SHORT лежить у перших 2 байтах поля значення
                return (value >= 1 && value <= 8) ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16(byte[] b, int i, boolean little) {
        return little ? (u8(b, i) | (u8(b, i + 1) << 8)) : ((u8(b, i) << 8) | u8(b, i + 1));
    }

    private static long u32(byte[] b, int i, boolean little) {
        return little
                ? (u8(b, i) | ((long) u8(b, i + 1) << 8) | ((long) u8(b, i + 2) << 16) | ((long) u8(b, i + 3) << 24))
                : (((long) u8(b, i) << 24) | ((long) u8(b, i + 1) << 16) | ((long) u8(b, i + 2) << 8) | u8(b, i + 3));
    }

    private static String ascii(byte[] b, int from, int len) {
        return new String(b, from, len, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
