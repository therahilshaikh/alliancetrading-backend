package in.ruhmans.alliancetrading.payment.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.springframework.stereotype.Component;

/**
 * Builds UPI deep links ({@code upi://pay?...}) and renders them as QR PNGs.
 * Any UPI app can scan the QR; the amount is pre-filled for the payer.
 */
@Component
public class UpiQrGenerator {

    private static final int QR_SIZE_PX = 512;

    public String payload(String vpa, String payeeName, BigDecimal amount, String note) {
        StringBuilder uri = new StringBuilder("upi://pay?pa=").append(encode(vpa))
                .append("&pn=").append(encode(payeeName))
                .append("&am=").append(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("&cu=INR");
        if (note != null && !note.isBlank()) {
            uri.append("&tn=").append(encode(note));
        }
        return uri.toString();
    }

    public byte[] renderPng(String payload) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE,
                    QR_SIZE_PX, QR_SIZE_PX, Map.of(
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 2,
                            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(),
                    BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Could not render payment QR", e);
        }
    }

    private static String encode(String value) {
        // UPI apps expect the VPA's '@' unescaped
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%40", "@");
    }
}
