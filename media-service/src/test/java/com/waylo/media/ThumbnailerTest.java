package com.waylo.media;

import com.waylo.media.process.Thumbnailer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Чиста логіка зменшення — без Spring і без контейнерів. */
class ThumbnailerTest {

    private final Thumbnailer thumbnailer = new Thumbnailer();

    private static byte[] image(int w, int h, String format) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLUE);
        g.fillOval(0, 0, w / 2, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void shrinksLongSideToMax_andKeepsAspectRatio() throws Exception {
        Optional<Thumbnailer.Result> result = thumbnailer.thumbnail(image(1600, 900, "jpg"), 640, 0.82f);

        assertTrue(result.isPresent());
        Thumbnailer.Result r = result.get();
        assertEquals(640, r.width());
        assertEquals(360, r.height());
        assertEquals(1600, r.sourceWidth(), "розміри оригіналу віддаємо власнику як є");
        assertEquals(900, r.sourceHeight());
        assertTrue(r.bytes().length > 0);
        assertTrue(r.bytes().length < image(1600, 900, "jpg").length, "мініатюра має бути легшою за оригінал");
    }

    @Test
    void smallerThanTarget_isKeptAsIs() throws Exception {
        Thumbnailer.Result r = thumbnailer.thumbnail(image(200, 100, "png"), 640, 0.82f).orElseThrow();

        assertEquals(200, r.width());
        assertEquals(100, r.height());
    }

    @Test
    void pngWithAlpha_becomesJpegWithoutBreaking() throws Exception {
        BufferedImage img = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);

        Thumbnailer.Result r = thumbnailer.thumbnail(out.toByteArray(), 640, 0.82f).orElseThrow();

        assertEquals(640, r.width());
        // JPEG — з втратами, тож біле може бути не рівно 0xFF.
        int blue = ImageIO.read(new java.io.ByteArrayInputStream(r.bytes())).getRGB(10, 10) & 0xFF;
        assertTrue(blue >= 0xF0, "прозоре мало стати білим, а не чорним (отримано " + blue + ")");
    }

    @Test
    void unreadableBytes_giveNoThumbnail() {
        byte[] notAnImage = "просто текст, а не фото".getBytes(StandardCharsets.UTF_8);

        assertTrue(thumbnailer.thumbnail(notAnImage, 640, 0.82f).isEmpty());
    }

    @Test
    void rotatedOrientation_swapsSides() throws Exception {
        BufferedImage wide = ImageIO.read(new java.io.ByteArrayInputStream(image(400, 200, "jpg")));

        BufferedImage upright = Thumbnailer.applyOrientation(wide, 6);   // поворот на 90°

        assertEquals(200, upright.getWidth());
        assertEquals(400, upright.getHeight());
    }
}
