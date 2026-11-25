package com.inventario.uat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/** Calcula el codigo de 6 digitos igual que Google/Microsoft Authenticator (RFC 6238, HMAC-SHA1, 30 s). */
final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    static String codigo(String secretoBase32) {
        try {
            long contador = System.currentTimeMillis() / 1000 / 30;
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(secretoBase32), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(contador).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binario % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Espera al inicio de una nueva ventana de 30 s si quedan menos de 5 s, para no enviar un codigo a punto de vencer. */
    static void esperarVentanaSegura() throws InterruptedException {
        long restante = 30 - (System.currentTimeMillis() / 1000 % 30);
        if (restante < 5) {
            Thread.sleep(restante * 1000 + 500);
        }
    }

    private static byte[] decodificar(String base32) {
        String limpio = base32.replace("=", "").replace(" ", "").toUpperCase();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            buffer = (buffer << 5) | BASE32.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                salida.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return salida.toByteArray();
    }
}
