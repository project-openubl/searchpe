package io.github.projectopenubl.xhandler.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Map;

/**
 * Renders printed representations (DESIGN.md §11.6): Qute HTML templates shipped with the service,
 * turned into PDF by OpenHTMLtoPDF. A4 and 80/58 mm tickets; companies customize through options.
 * Thread-safe: templates are immutable, and each render builds its own renderer.
 */
@ApplicationScoped
public class PdfRenderer {

    private final Template a4;
    private final Template ticket;

    public PdfRenderer(@Location("pdf/a4.html") Template a4, @Location("pdf/ticket.html") Template ticket) {
        this.a4 = a4;
        this.ticket = ticket;
    }

    /** {@code format}: a4, ticket80 or ticket58. {@code data}: the template's model. */
    public byte[] render(String format, Map<String, Object> data) {
        String html = switch (format) {
            case "ticket80" -> ticket.data(with(data, "width", "80mm")).render();
            case "ticket58" -> ticket.data(with(data, "width", "58mm")).render();
            default -> a4.data(data).render();
        };
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // Only data: URIs (QR, logo): templates never make the renderer fetch anything (SSRF)
            builder.useUriResolver((base, uri) -> uri != null && uri.startsWith("data:") ? uri : null);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Object> with(Map<String, Object> data, String key, Object value) {
        Map<String, Object> copy = new java.util.HashMap<>(data);
        copy.put(key, value);
        return copy;
    }

    /** SUNAT's QR as a PNG data URI. */
    public static String qrDataUri(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 240, 240,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q, EncodeHintType.MARGIN, 1));
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(matrix.getWidth(), matrix.getHeight(),
                    java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            javax.imageio.ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (com.google.zxing.WriterException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
