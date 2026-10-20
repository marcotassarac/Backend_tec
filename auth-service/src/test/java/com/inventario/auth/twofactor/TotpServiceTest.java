package com.inventario.auth.twofactor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de TotpService (RFC 6238). Para validar verificarCodigo() con un
 * codigo realmente correcto, se invoca por reflexion el metodo privado
 * generarCodigoParaStep (el mismo algoritmo de produccion), en vez de reimplementar el
 * calculo HMAC-SHA1 por separado en el test.
 */
class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    void generarSecreto_debeProducirUnBase32ValidoSinRelleno() {
        String secreto = totpService.generarSecreto();

        assertThat(secreto).isNotBlank();
        assertThat(secreto).doesNotContain("=");
        assertThat(secreto).matches("[A-Z2-7]+");
    }

    @Test
    void generarOtpAuthUrl_debeContenerLosParametrosEstandarDeTotp() {
        String url = totpService.generarOtpAuthUrl("operador", "JBSWY3DPEHPK3PXP");

        assertThat(url).startsWith("otpauth://totp/");
        assertThat(url).contains("algorithm=SHA1");
        assertThat(url).contains("digits=6");
        assertThat(url).contains("period=30");
        assertThat(url).contains("secret=JBSWY3DPEHPK3PXP");
    }

    @Test
    void verificarCodigo_conElCodigoRealDelPasoActual_debeSerValido() throws Exception {
        String secreto = totpService.generarSecreto();
        String codigoReal = generarCodigoParaStepActual(secreto);

        boolean resultado = totpService.verificarCodigo(secreto, codigoReal);

        assertThat(resultado).isTrue();
    }

    @Test
    void verificarCodigo_conCodigoIncorrecto_debeSerInvalido() {
        String secreto = totpService.generarSecreto();

        boolean resultado = totpService.verificarCodigo(secreto, "000000");

        assertThat(resultado).isFalse();
    }

    @Test
    void verificarCodigo_conFormatoInvalido_debeSerInvalidoSinLanzarExcepcion() {
        String secreto = totpService.generarSecreto();

        assertThat(totpService.verificarCodigo(secreto, "12345")).isFalse();
        assertThat(totpService.verificarCodigo(secreto, "abcdef")).isFalse();
        assertThat(totpService.verificarCodigo(secreto, null)).isFalse();
    }

    /** Invoca por reflexion el algoritmo privado real para obtener el codigo del paso actual. */
    private String generarCodigoParaStepActual(String secretoBase32) throws Exception {
        long currentStep = System.currentTimeMillis() / 1000L / 30L;
        org.apache.commons.codec.binary.Base32 base32 = new org.apache.commons.codec.binary.Base32();
        byte[] secretBytes = base32.decode(secretoBase32);

        Method metodo = TotpService.class.getDeclaredMethod("generarCodigoParaStep", byte[].class, long.class);
        metodo.setAccessible(true);
        return (String) metodo.invoke(totpService, secretBytes, currentStep);
    }
}
