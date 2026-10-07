package com.waylo.media;

import com.waylo.media.process.Exif;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Читач EXIF: збираємо мінімальний JPEG із APP1 вручну й перевіряємо тег 0x0112. */
class ExifTest {

    /** SOI + APP1(Exif/TIFF little-endian з одним тегом орієнтації) + SOS. */
    private static byte[] jpegWithOrientation(int orientation) throws Exception {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write(new byte[]{'I', 'I', 42, 0});          // little-endian, магічне 42
        tiff.write(new byte[]{8, 0, 0, 0});               // зсув до IFD0
        tiff.write(new byte[]{1, 0});                     // один запис
        tiff.write(new byte[]{0x12, 0x01});               // тег 0x0112
        tiff.write(new byte[]{3, 0});                     // тип SHORT
        tiff.write(new byte[]{1, 0, 0, 0});               // кількість
        tiff.write(new byte[]{(byte) orientation, 0, 0, 0});
        tiff.write(new byte[]{0, 0, 0, 0});               // наступний IFD — немає

        byte[] exifBody = tiff.toByteArray();
        int length = 2 + 6 + exifBody.length;             // довжина сегмента + "Exif\0\0"
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        jpeg.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        jpeg.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) (length & 0xFF)});
        jpeg.write("Exif".getBytes(StandardCharsets.US_ASCII));
        jpeg.write(new byte[]{0, 0});
        jpeg.write(exifBody);
        jpeg.write(new byte[]{(byte) 0xFF, (byte) 0xDA, 0, 2});
        return jpeg.toByteArray();
    }

    @Test
    void readsOrientationFromApp1() throws Exception {
        assertEquals(6, Exif.orientation(jpegWithOrientation(6)));
        assertEquals(8, Exif.orientation(jpegWithOrientation(8)));
    }

    @Test
    void nonJpegOrMissingTag_isNormal() {
        assertEquals(Exif.NORMAL, Exif.orientation(new byte[]{1, 2, 3}));
        assertEquals(Exif.NORMAL, Exif.orientation(new byte[0]));
    }

    @Test
    void brokenSegmentLength_doesNotThrow() {
        byte[] broken = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, (byte) 0xFF, (byte) 0xFF, 'E', 'x'};

        assertEquals(Exif.NORMAL, Exif.orientation(broken));
    }
}
