package in.ruhmans.alliancetrading.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;

import javax.imageio.ImageIO;

import in.ruhmans.alliancetrading.payment.service.UpiQrGenerator;

import org.junit.jupiter.api.Test;

class UpiQrGeneratorTest {

    private final UpiQrGenerator generator = new UpiQrGenerator();

    @Test
    void payloadIsAStandardUpiDeepLink() {
        String uri = generator.payload("alliance.trade@okaxis", "Alliance Trading", new BigDecimal("10000"),
                "AllianceTrading investment");

        assertThat(uri).isEqualTo("upi://pay?pa=alliance.trade@okaxis&pn=Alliance%20Trading"
                + "&am=10000.00&cu=INR&tn=AllianceTrading%20investment");
    }

    @Test
    void payloadEscapesReservedCharactersInNames() {
        String uri = generator.payload("a@upi", "A&B Traders", new BigDecimal("1.5"), null);

        assertThat(uri).isEqualTo("upi://pay?pa=a@upi&pn=A%26B%20Traders&am=1.50&cu=INR");
    }

    @Test
    void rendersAReadablePng() throws IOException {
        byte[] png = generator.renderPng(generator.payload("a@upi", "A", BigDecimal.TEN, null));

        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(512);
    }
}
