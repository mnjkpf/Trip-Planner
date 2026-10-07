package com.waylo.media.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * Мініатюра з оригіналу: тільки JDK, без зовнішніх бібліотек.
 *
 * Результат завжди JPEG — прозорість кладемо на біле. Формати, яких ImageIO не
 * знає (webp, heic з телефона), чесно повертають empty: тоді показується
 * оригінал, а не зламана картинка.
 */
@Component
public class Thumbnailer {

    private static final Logger log = LoggerFactory.getLogger(Thumbnailer.class);
    /** Захист від «зображень-бомб»: 50 Мп достатньо для будь-якого фото. */
    private static final long MAX_PIXELS = 50_000_000L;

    static {
        // Тримаємо декодування в пам'яті: у контейнері немає сенсу писати кеш на диск.
        ImageIO.setUseCache(false);
    }

    public record Result(byte[] bytes, int width, int height, int sourceWidth, int sourceHeight) {}

    public Optional<Result> thumbnail(byte[] source, int maxPx, float quality) {
        try {
            BufferedImage image = decode(source);
            if (image == null) {
                return Optional.empty();
            }
            BufferedImage upright = applyOrientation(image, Exif.orientation(source));
            BufferedImage scaled = scale(upright, maxPx);
            byte[] jpeg = encodeJpeg(scaled, quality);
            return Optional.of(new Result(jpeg, scaled.getWidth(), scaled.getHeight(),
                    upright.getWidth(), upright.getHeight()));
        } catch (Exception ex) {
            log.warn("мініатюру не зроблено: {}", ex.toString());
            return Optional.empty();
        }
    }

    /** Спершу дивимось розмір у заголовку й лише потім декодуємо весь кадр. */
    private static BufferedImage decode(byte[] source) throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;       // формат не підтримується — не помилка
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    log.warn("зображення {} пікселів — пропускаю мініатюру", pixels);
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Канонічна таблиця перетворень EXIF (w/h — розміри ОРИГІНАЛУ). public — бо її ж перевіряє тест. */
    public static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= Exif.NORMAL || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swapSides = orientation >= 5;
        BufferedImage out = new BufferedImage(swapSides ? h : w, swapSides ? w : h, BufferedImage.TYPE_INT_RGB);
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.scale(-1.0, 1.0); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1.0, -1.0); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1.0, 1.0); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1.0, 1.0); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
        }
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    /**
     * Зменшення вдвічі, поки не наблизимось до цілі, і лише тоді точний крок:
     * одноразове стискання великого кадру дає помітний «шум» на дрібних деталях.
     */
    static BufferedImage scale(BufferedImage src, int maxPx) {
        int w = src.getWidth();
        int h = src.getHeight();
        double factor = (double) maxPx / Math.max(w, h);
        if (factor >= 1.0) {
            return toRgb(src);     // менше за ціль — лишаємо як є, тільки без альфи
        }
        BufferedImage current = toRgb(src);
        int targetW = Math.max(1, (int) Math.round(w * factor));
        int targetH = Math.max(1, (int) Math.round(h * factor));
        int curW = w;
        int curH = h;
        while (curW / 2 > targetW && curH / 2 > targetH) {
            curW /= 2;
            curH /= 2;
            current = draw(current, curW, curH);
        }
        return draw(current, targetW, targetH);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** JPEG не має альфи: прозоре стало б чорним, тому підкладаємо біле. */
    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
