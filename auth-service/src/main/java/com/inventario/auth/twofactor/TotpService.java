package com.inventario.auth.twofactor;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Implementacion de TOTP (RFC 6238, basado en HOTP / RFC 4226) compatible con
 * Google Authenticator y Microsoft Authenticator: codigo de 6 digitos que cambia
 * cada 30 segundos, derivado de un secreto compartido (Base32) y la hora actual.
 */
@Service
public class TotpService {

    private static final int SECRET_BYTES_LENGTH = 20; // 160 bits, estandar para HMAC-SHA1
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int ALLOWED_STEP_DRIFT = 1; // tolera +/- 30s de desfase de reloj
    private static final String ISSUER = "GestorInventario";
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base32 base32 = new Base32();

    /** Genera un nuevo secreto compartido, codificado en Base32 (el formato que esperan las apps autenticadoras). */
    public String generarSecreto() {
        byte[] bytes = new byte[SECRET_BYTES_LENGTH];
        secureRandom.nextBytes(bytes);
        return base32.encodeToString(bytes).replace("=", "");
    }

    /** URI otpauth:// estandar que las apps autenticadoras leen desde el codigo QR. */
    public String generarOtpAuthUrl(String username, String secretoBase32) {
        String label = URLEncoder.encode(ISSUER + ":" + username, StandardCharsets.UTF_8);
        String issuerParam = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + secretoBase32
                + "&issuer=" + issuerParam
                + "&algorithm=SHA1&digits=" + CODE_DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    /** Genera el codigo QR (PNG, codificado en base64) a partir de la URI otpauth. */
    public String generarQrBase64(String otpAuthUrl) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 250, 250);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("No fue posible generar el codigo QR", ex);
        }
    }

    /** Valida un codigo de 6 digitos ingresado por el usuario contra el secreto guardado. */
    public boolean verificarCodigo(String secretoBase32, String codigoIngresado) {
        if (codigoIngresado == null || !codigoIngresado.matches("\\d{6}")) {
            return false;
        }
        long currentStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        byte[] secretBytes = base32.decode(secretoBase32);

        for (int drift = -ALLOWED_STEP_DRIFT; drift <= ALLOWED_STEP_DRIFT; drift++) {
            String codigoEsperado = generarCodigoParaStep(secretBytes, currentStep + drift);
            if (codigoEsperado.equals(codigoIngresado)) {
                return true;
            }
        }
        return false;
    }

    private String generarCodigoParaStep(byte[] secretBytes, long step) {
        byte[] stepBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            stepBytes[i] = (byte) (step & 0xFF);
            step >>= 8;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(stepBytes);

            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("Error calculando el codigo TOTP", ex);
        }
    }
}
