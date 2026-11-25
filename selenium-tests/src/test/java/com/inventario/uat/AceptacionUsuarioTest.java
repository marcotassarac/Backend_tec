package com.inventario.uat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Ejecucion de las pruebas de aceptacion de usuario (UAT) del Gestor de Inventario.
 * Un metodo por criterio de aceptacion (docs/EvaluacionParcial3_TCY0101/criterios_aceptacion.md).
 * Los datos previos se preparan por API; la accion evaluada se hace siempre por la interfaz.
 * Al terminar cada criterio se guarda una captura en evidencias/ciclo{N}/.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AceptacionUsuarioTest {

    private static final String WEB = "http://localhost:3000";
    private static final String CICLO = System.getProperty("uat.ciclo", "1");
    private static final Path EVIDENCIAS = Path.of("..", "docs", "EvaluacionParcial3_TCY0101", "evidencias", "ciclo" + CICLO);
    private static final List<String> TERMINOS_TECNICOS = List.of(
            "could not execute", "sql", "constraint", "exception", "public.", "jdbc", "hibernate", "stack");

    private WebDriver driver;
    private WebDriverWait wait;
    private final StringBuilder notas = new StringBuilder();

    @BeforeAll
    static void cuentaSin2fa() {
        ApiUat.desactivar2faSiQuedoActivo("operador", "operador123", Path.of("target", "uat-2fa-secreto.txt"));
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(EVIDENCIAS);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1366,900", "--disable-gpu", "--no-sandbox",
                "--disable-dev-shm-usage", "--lang=es-CL");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterEach
    void tearDown(TestInfo info) throws IOException {
        String nombre = info.getDisplayName().split(" ")[0];
        try {
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(EVIDENCIAS.resolve(nombre + ".png"), png);
        } catch (RuntimeException ignorada) {
            // Si hay un dialogo nativo abierto no se puede capturar; la nota de texto queda igual.
        }
        if (!notas.isEmpty()) {
            Files.writeString(EVIDENCIAS.resolve(nombre + ".txt"),
                    info.getDisplayName() + System.lineSeparator() + LocalDateTime.now() + System.lineSeparator()
                            + notas, StandardCharsets.UTF_8);
        }
        driver.quit();
    }

    // ------------------------------------------------------------------ utilidades

    private void nota(String texto) {
        notas.append(texto).append(System.lineSeparator());
    }

    private String sufijo() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000, 99_999));
    }

    private void click(By locator) {
        WebElement el = wait.until(ExpectedConditions.elementToBeClickable(locator));
        prepararDialogos();
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    /**
     * Vacia el campo como lo reconoce React (setter nativo + evento input) y escribe el texto como entrada
     * de teclado real por el protocolo de Chrome (CDP). Con Chrome 155 y Selenium 4.23, sendKeys de WebDriver no
     * entrega las teclas en algunas paginas (verificado: ni un listener de window recibe keydown).
     */
    private void escribir(By locator, String texto) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        ((JavascriptExecutor) driver).executeScript(
                "const el = arguments[0]; el.focus();"
                        + "const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;"
                        + "Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, '');"
                        + "el.dispatchEvent(new Event('input', { bubbles: true }));", el);
        ((ChromiumDriver) driver).executeCdpCommand("Input.insertText", Map.of("text", texto));
    }

    private void clickEn(WebElement fila, String boton) {
        WebElement el = fila.findElement(By.xpath(".//button[normalize-space()='" + boton + "']"));
        prepararDialogos();
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el);
    }

    private void iniciarSesion(String usuario, String password) {
        driver.get(WEB + "/login");
        escribir(By.id("username"), usuario);
        escribir(By.id("password"), password);
        click(By.cssSelector("button[type='submit']"));
    }

    private void loginOk(String usuario, String password) {
        iniciarSesion(usuario, password);
        wait.until(ExpectedConditions.urlContains("/menu"));
    }

    private void esperarCarga() {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//td[contains(.,'Cargando')]")));
    }

    private WebElement fila(String textoUnico) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//tbody/tr[td[normalize-space()='" + textoUnico + "']]")));
    }

    private boolean existeFila(String textoUnico) {
        return !driver.findElements(By.xpath("//tbody/tr[td[normalize-space()='" + textoUnico + "']]")).isEmpty();
    }

    private By campoDelModal(String etiqueta) {
        return By.xpath("//form//label[normalize-space()='" + etiqueta + "']/following-sibling::*[1]");
    }

    private String mensajeDeError() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("form p.bg-red-50, p.bg-red-50"))).getText();
    }

    /**
     * Responde "Aceptar" a los confirm() y guarda el texto de los alert() en vez de abrir dialogos
     * nativos: en Chrome headless esos dialogos aparecen con un retraso variable y hacian intermitentes
     * las pruebas. Se instala justo antes de cada clic.
     */
    private void prepararDialogos() {
        ((JavascriptExecutor) driver).executeScript(
                "window.__confirmaciones = [];"
                        + "window.__alertas = [];"
                        + "window.confirm = m => { window.__confirmaciones.push(String(m)); return true; };"
                        + "window.alert = m => { window.__alertas.push(String(m)); };");
    }

    /** Verifica que se pidio confirmacion y devuelve el texto del alert() de error si aparecio. */
    private String confirmarYLeerAlerta() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        wait.until(d -> ((Number) js.executeScript("return window.__confirmaciones.length")).intValue() > 0);
        nota("Confirmacion pedida al usuario: " + js.executeScript("return window.__confirmaciones[0]"));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(4))
                    .until(d -> ((Number) js.executeScript("return window.__alertas.length")).intValue() > 0);
            return (String) js.executeScript("return window.__alertas[0]");
        } catch (TimeoutException sinAlerta) {
            return null;
        }
    }

    private void sinTerminosTecnicos(String mensaje) {
        String bajo = mensaje.toLowerCase(Locale.ROOT);
        for (String termino : TERMINOS_TECNICOS) {
            assertFalse(bajo.contains(termino),
                    "El mensaje al usuario expone detalle tecnico ('" + termino + "'): " + mensaje);
        }
    }

    private static long monto(String clp) {
        String digitos = clp.replaceAll("[^0-9]", "");
        return digitos.isEmpty() ? 0 : Long.parseLong(digitos);
    }

    // ------------------------------------------------------------------ HU-01 Sesion

    @Test
    @Order(1)
    @DisplayName("CA-01 Login valido ADMIN llega al menu con su nombre y rol")
    void ca01() {
        loginOk("admin", "admin123");
        WebElement header = driver.findElement(By.tagName("header"));
        nota("Header: " + header.getText());
        assertTrue(header.getText().contains("Administrador del Sistema"));
        assertTrue(header.getText().contains("ADMIN"));
    }

    @Test
    @Order(2)
    @DisplayName("CA-02 Login valido OPERADOR llega al menu con rol OPERADOR")
    void ca02() {
        loginOk("operador", "operador123");
        String header = driver.findElement(By.tagName("header")).getText();
        nota("Header: " + header);
        assertTrue(header.contains("OPERADOR"));
    }

    @Test
    @Order(3)
    @DisplayName("CA-03 Credenciales invalidas muestran error y no ingresan")
    void ca03() {
        iniciarSesion("operador", "clave-incorrecta");
        String error = mensajeDeError();
        nota("Mensaje mostrado: " + error);
        assertTrue(driver.getCurrentUrl().contains("/login"));
        sinTerminosTecnicos(error);
    }

    @Test
    @Order(4)
    @DisplayName("CA-04 Cerrar sesion bloquea el acceso a paginas internas")
    void ca04() {
        loginOk("operador", "operador123");
        click(By.xpath("//button[normalize-space()='Cerrar sesion']"));
        wait.until(ExpectedConditions.urlContains("/login"));
        driver.get(WEB + "/productos");
        wait.until(ExpectedConditions.urlContains("/login"));
        nota("Tras cerrar sesion, /productos redirige a: " + driver.getCurrentUrl());
        assertTrue(driver.getCurrentUrl().contains("/login"));
    }

    @Test
    @Order(5)
    @DisplayName("CA-05 La pantalla de login no expone usuarios ni contrasenas")
    void ca05() {
        driver.get(WEB + "/login");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
        String pagina = driver.findElement(By.tagName("body")).getText();
        nota("Texto visible del login:" + System.lineSeparator() + pagina);
        assertFalse(pagina.contains("admin123") || pagina.contains("operador123"),
                "El login muestra contrasenas del sistema a cualquier visitante");
    }

    // ------------------------------------------------------------------ HU-03 Categorias

    @Test
    @Order(9)
    @DisplayName("CA-09 Crear una categoria la muestra activa en el listado")
    void ca09() {
        String nombre = "UAT Ferreteria " + sufijo();
        loginOk("admin", "admin123");
        driver.get(WEB + "/categorias");
        esperarCarga();
        click(By.xpath("//button[normalize-space()='+ Nueva categoria']"));
        escribir(campoDelModal("Nombre"), nombre);
        escribir(campoDelModal("Descripcion"), "Herramientas y articulos de ferreteria");
        click(By.xpath("//button[@type='submit' and normalize-space()='Guardar']"));
        WebElement fila = fila(nombre);
        nota("Fila creada: " + fila.getText());
        assertTrue(fila.getText().contains("Activa"));
    }

    @Test
    @Order(10)
    @DisplayName("CA-10 Crear categoria con nombre existente muestra mensaje claro")
    void ca10() {
        String token = ApiUat.token("admin", "admin123");
        String nombre = "UAT Duplicada " + sufijo();
        ApiUat.crearCategoria(token, nombre);
        loginOk("admin", "admin123");
        driver.get(WEB + "/categorias");
        esperarCarga();
        click(By.xpath("//button[normalize-space()='+ Nueva categoria']"));
        escribir(campoDelModal("Nombre"), nombre);
        click(By.xpath("//button[@type='submit' and normalize-space()='Guardar']"));
        String error = mensajeDeError();
        nota("Mensaje mostrado: " + error);
        sinTerminosTecnicos(error);
        assertTrue(error.toLowerCase(Locale.ROOT).contains("existe"), "Se esperaba un mensaje de nombre existente");
    }

    @Test
    @Order(11)
    @DisplayName("CA-11 Editar una categoria refleja los cambios")
    void ca11() {
        String token = ApiUat.token("admin", "admin123");
        String nombre = "UAT Editar " + sufijo();
        ApiUat.crearCategoria(token, nombre);
        String nuevo = nombre + " v2";
        loginOk("admin", "admin123");
        driver.get(WEB + "/categorias");
        esperarCarga();
        clickEn(fila(nombre), "Editar");
        escribir(campoDelModal("Nombre"), nuevo);
        escribir(campoDelModal("Descripcion"), "Descripcion actualizada en UAT");
        click(By.xpath("//button[@type='submit' and normalize-space()='Guardar']"));
        WebElement fila = fila(nuevo);
        nota("Fila editada: " + fila.getText());
        assertTrue(fila.getText().contains("Descripcion actualizada en UAT"));
    }

    @Test
    @Order(12)
    @DisplayName("CA-12 Editar categoria con nombre de otra muestra mensaje claro")
    void ca12() {
        String token = ApiUat.token("admin", "admin123");
        String a = "UAT Origen " + sufijo();
        String b = "UAT Destino " + sufijo();
        ApiUat.crearCategoria(token, a);
        ApiUat.crearCategoria(token, b);
        loginOk("admin", "admin123");
        driver.get(WEB + "/categorias");
        esperarCarga();
        clickEn(fila(b), "Editar");
        escribir(campoDelModal("Nombre"), a);
        click(By.xpath("//button[@type='submit' and normalize-space()='Guardar']"));
        String error = mensajeDeError();
        nota("Mensaje mostrado al editar '" + b + "' con el nombre '" + a + "': " + error);
        sinTerminosTecnicos(error);
        assertTrue(error.toLowerCase(Locale.ROOT).contains("existe"), "Se esperaba un mensaje de nombre existente");
    }

    @Test
    @Order(13)
    @DisplayName("CA-13 No se puede eliminar una categoria con productos")
    void ca13() {
        String token = ApiUat.token("admin", "admin123");
        String nombre = "UAT Con Productos " + sufijo();
        long categoriaId = ApiUat.crearCategoria(token, nombre);
        ApiUat.crearProducto(token, "UAT-CAT-" + sufijo(), categoriaId, 5, 1, true);
        loginOk("admin", "admin123");
        driver.get(WEB + "/categorias");
        esperarCarga();
        clickEn(fila(nombre), "Eliminar");
        String alerta = confirmarYLeerAlerta();
        nota("Mensaje al intentar eliminar: " + alerta);
        driver.navigate().refresh();
        esperarCarga();
        boolean sigue = existeFila(nombre);
        nota("La categoria sigue en el listado: " + sigue);
        assertTrue(sigue, "La categoria con productos fue eliminada y sus productos quedaron sin categoria");
        assertTrue(alerta != null, "No se informo al usuario por que no se puede eliminar");
        sinTerminosTecnicos(alerta);
    }

    @Test
    @Order(14)
    @DisplayName("CA-14 Eliminar una categoria sin productos la quita del listado")
    void ca14() {
        String token = ApiUat.token("admin", "admin123");
        String nombre = "UAT Vacia " + sufijo();
        ApiUat.crearCategoria(token, nombre);
        loginOk("admin", "admin123");
        driver.get(WEB + "/categorias");
        esperarCarga();
        clickEn(fila(nombre), "Eliminar");
        String alerta = confirmarYLeerAlerta();
        nota("Alerta tras eliminar (se espera ninguna): " + alerta);
        wait.until(d -> !existeFila(nombre));
        assertFalse(existeFila(nombre));
    }

    // ------------------------------------------------------------------ HU-04 Productos

    @Test
    @Order(15)
    @DisplayName("CA-15 Crear un producto lo muestra con sus datos y activo")
    void ca15() {
        String codigo = "UAT-NEW-" + sufijo();
        loginOk("admin", "admin123");
        driver.get(WEB + "/productos");
        esperarCarga();
        click(By.xpath("//button[normalize-space()='+ Nuevo producto']"));
        escribir(campoDelModal("Codigo"), codigo);
        escribir(campoDelModal("Nombre"), "Taladro percutor UAT");
        escribir(campoDelModal("Precio"), "45990");
        escribir(campoDelModal("Stock inicial"), "12");
        escribir(campoDelModal("Stock minimo"), "3");
        click(By.xpath("//button[@type='submit' and normalize-space()='Guardar']"));
        WebElement fila = fila(codigo);
        nota("Fila creada: " + fila.getText());
        assertTrue(fila.getText().contains("Taladro percutor UAT"));
        assertTrue(fila.getText().contains("45.990"));
        assertTrue(fila.getText().contains("12"));
        assertTrue(fila.getText().contains("Activo"));
    }

    @Test
    @Order(16)
    @DisplayName("CA-16 Crear producto con codigo existente muestra mensaje claro")
    void ca16() {
        String token = ApiUat.token("admin", "admin123");
        String codigo = "UAT-DUP-" + sufijo();
        ApiUat.crearProducto(token, codigo, ApiUat.primeraCategoriaId(token), 5, 1, true);
        loginOk("admin", "admin123");
        driver.get(WEB + "/productos");
        esperarCarga();
        click(By.xpath("//button[normalize-space()='+ Nuevo producto']"));
        escribir(campoDelModal("Codigo"), codigo);
        escribir(campoDelModal("Nombre"), "Duplicado UAT");
        escribir(campoDelModal("Precio"), "1000");
        click(By.xpath("//button[@type='submit' and normalize-space()='Guardar']"));
        String error = mensajeDeError();
        nota("Mensaje mostrado: " + error);
        sinTerminosTecnicos(error);
        assertTrue(error.toLowerCase(Locale.ROOT).contains("existe"), "Se esperaba un mensaje de codigo existente");
    }

    @Test
    @Order(17)
    @DisplayName("CA-17 Editar producto con codigo de otro muestra mensaje claro")
    void ca17() {
        String token = ApiUat.token("admin", "admin123");
        long cat = ApiUat.primeraCategoriaId(token);
        String a = "UAT-A-" + sufijo();
        String b = "UAT-B-" + sufijo();
        ApiUat.crearProducto(token, a, cat, 5, 1, true);
        ApiUat.crearProducto(token, b, cat, 5, 1, true);
        loginOk("admin", "admin123");
        driver.get(WEB + "/productos");
        esperarCarga();
        clickEn(fila(b), "Editar");
        escribir(campoDelModal("Codigo"), a);
        click(By.xpath("//button[@type='submit' and normalize-space()='Guardar']"));
        String error = mensajeDeError();
        nota("Mensaje mostrado al editar '" + b + "' con el codigo '" + a + "': " + error);
        sinTerminosTecnicos(error);
        assertTrue(error.toLowerCase(Locale.ROOT).contains("existe"), "Se esperaba un mensaje de codigo existente");
    }

    @Test
    @Order(18)
    @DisplayName("CA-18 Un producto bajo su stock minimo se marca Stock bajo")
    void ca18() {
        String token = ApiUat.token("admin", "admin123");
        String codigo = "UAT-LOW-" + sufijo();
        ApiUat.crearProducto(token, codigo, ApiUat.primeraCategoriaId(token), 2, 5, true);
        loginOk("operador", "operador123");
        driver.get(WEB + "/productos");
        esperarCarga();
        WebElement fila = fila(codigo);
        nota("Fila (stock 2, minimo 5): " + fila.getText());
        assertTrue(fila.getText().contains("Stock bajo"));
    }

    @Test
    @Order(19)
    @DisplayName("CA-19 Eliminar un producto conserva su kardex")
    void ca19() {
        String token = ApiUat.token("admin", "admin123");
        String codigo = "UAT-KDX-" + sufijo();
        long id = ApiUat.crearProducto(token, codigo, ApiUat.primeraCategoriaId(token), 10, 1, true);
        ApiUat.registrarMovimiento(token, id, "SALIDA", 4, "Venta UAT antes de eliminar");
        loginOk("admin", "admin123");
        driver.get(WEB + "/productos");
        esperarCarga();
        clickEn(fila(codigo), "Eliminar");
        String alerta = confirmarYLeerAlerta();
        nota("Alerta al eliminar: " + alerta);
        driver.get(WEB + "/reportes");
        esperarCarga();
        if (!existeFila(codigo)) {
            nota("El producto ya no aparece en Reportes: su kardex no se puede consultar.");
            fail("Al eliminar el producto se perdio el acceso a su historial (kardex)");
        }
        clickEn(fila(codigo), "Ver historial");
        WebElement kardex = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//h2[starts-with(normalize-space(),'Kardex')]/..")));
        wait.until(ExpectedConditions.textToBePresentInElement(kardex, "Venta UAT antes de eliminar"));
        nota("Kardex tras eliminar: " + kardex.getText());
    }

    // ------------------------------------------------------------------ HU-05 Movimientos

    private long prepararProductoParaMovimiento(String codigo, int stock) {
        String token = ApiUat.token("admin", "admin123");
        return ApiUat.crearProducto(token, codigo, ApiUat.primeraCategoriaId(token), stock, 1, true);
    }

    private void registrarMovimientoUi(String codigo, String tipoBoton, int cantidad, String motivo) {
        driver.get(WEB + "/movimientos");
        esperarCarga();
        Select producto = new Select(wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//label[normalize-space()='Producto']/following-sibling::select"))));
        producto.selectByVisibleText(codigo + " - Producto " + codigo);
        click(By.xpath("//button[@type='button' and normalize-space()='" + tipoBoton + "']"));
        escribir(By.xpath("//label[normalize-space()='Cantidad']/following-sibling::input"), String.valueOf(cantidad));
        escribir(By.xpath("//label[normalize-space()='Motivo']/following-sibling::input"), motivo);
        nota("Formulario antes de enviar: cantidad=" + driver.findElement(
                By.xpath("//label[normalize-space()='Cantidad']/following-sibling::input")).getAttribute("value")
                + ", motivo=" + driver.findElement(
                By.xpath("//label[normalize-space()='Motivo']/following-sibling::input")).getAttribute("value"));
        click(By.xpath("//button[@type='submit']"));
    }

    @Test
    @Order(20)
    @DisplayName("CA-20 Una ENTRADA aumenta el stock y queda en el historial con el usuario")
    void ca20() {
        String codigo = "UAT-IN-" + sufijo();
        long id = prepararProductoParaMovimiento(codigo, 10);
        loginOk("operador", "operador123");
        registrarMovimientoUi(codigo, "Entrada", 7, "Compra a proveedor UAT");
        String exito = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("p.bg-emerald-50"))).getText();
        nota("Mensaje: " + exito);
        assertTrue(exito.contains("17"));
        WebElement fila = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//tbody/tr[td[normalize-space()='Producto " + codigo + "']]")));
        nota("Historial: " + fila.getText());
        assertTrue(fila.getText().contains("ENTRADA") && fila.getText().contains("operador"));
        assertEquals(17, ApiUat.producto(ApiUat.token("admin", "admin123"), id).path("stock").asInt());
    }

    @Test
    @Order(21)
    @DisplayName("CA-21 Una SALIDA disminuye el stock")
    void ca21() {
        String codigo = "UAT-OUT-" + sufijo();
        long id = prepararProductoParaMovimiento(codigo, 10);
        loginOk("operador", "operador123");
        registrarMovimientoUi(codigo, "Salida", 3, "Venta mesón UAT");
        String exito = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("p.bg-emerald-50"))).getText();
        nota("Mensaje: " + exito);
        assertEquals(7, ApiUat.producto(ApiUat.token("admin", "admin123"), id).path("stock").asInt());
    }

    @Test
    @Order(22)
    @DisplayName("CA-22 Una SALIDA mayor al stock se rechaza sin cambiar el stock")
    void ca22() {
        String codigo = "UAT-NOSTK-" + sufijo();
        long id = prepararProductoParaMovimiento(codigo, 4);
        loginOk("operador", "operador123");
        registrarMovimientoUi(codigo, "Salida", 9, "Intento de venta sin stock");
        String error = mensajeDeError();
        nota("Mensaje: " + error);
        sinTerminosTecnicos(error);
        assertTrue(error.toLowerCase(Locale.ROOT).contains("stock insuficiente"));
        assertEquals(4, ApiUat.producto(ApiUat.token("admin", "admin123"), id).path("stock").asInt());
    }

    @Test
    @Order(23)
    @DisplayName("CA-23 Un producto inactivo no se ofrece para movimientos")
    void ca23() {
        String token = ApiUat.token("admin", "admin123");
        String codigo = "UAT-OFF-" + sufijo();
        ApiUat.crearProducto(token, codigo, ApiUat.primeraCategoriaId(token), 5, 1, false);
        loginOk("operador", "operador123");
        driver.get(WEB + "/movimientos");
        esperarCarga();
        WebElement select = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//label[normalize-space()='Producto']/following-sibling::select")));
        List<String> opciones = new ArrayList<>();
        new Select(select).getOptions().forEach(o -> opciones.add(o.getText()));
        nota("Opciones disponibles: " + opciones);
        assertTrue(opciones.stream().noneMatch(o -> o.startsWith(codigo)));
    }

    // ------------------------------------------------------------------ HU-06 Reportes

    @Test
    @Order(24)
    @DisplayName("CA-24 El reporte muestra totales y el valor total cuadra con precio x stock")
    void ca24() {
        loginOk("operador", "operador123");
        driver.get(WEB + "/reportes");
        esperarCarga();
        String valorTotal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//p[normalize-space()='Valor total inventario']/following-sibling::p"))).getText();
        String total = driver.findElement(By.xpath("//p[normalize-space()='Total de productos']/following-sibling::p")).getText();
        long suma = 0;
        List<WebElement> filas = driver.findElements(By.xpath("//table[1]/tbody/tr[td[6]]"));
        for (WebElement f : filas) {
            suma += monto(f.findElement(By.xpath("./td[6]")).getText());
        }
        nota("Total de productos: " + total + " | filas: " + filas.size());
        nota("Valor total mostrado: " + valorTotal + " | suma de precio x stock por fila: " + suma);
        assertEquals(Integer.parseInt(total.trim()), filas.size());
        assertEquals(monto(valorTotal), suma);
    }

    @Test
    @Order(25)
    @DisplayName("CA-25 El kardex muestra tipo, cantidad, stock, motivo y usuario")
    void ca25() {
        String codigo = "UAT-KX2-" + sufijo();
        String token = ApiUat.token("operador", "operador123");
        long id = prepararProductoParaMovimiento(codigo, 10);
        ApiUat.registrarMovimiento(token, id, "ENTRADA", 5, "Reposicion UAT");
        ApiUat.registrarMovimiento(token, id, "SALIDA", 2, "Venta UAT");
        loginOk("operador", "operador123");
        driver.get(WEB + "/reportes");
        esperarCarga();
        clickEn(fila(codigo), "Ver historial");
        WebElement kardex = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//h2[starts-with(normalize-space(),'Kardex')]/..")));
        wait.until(ExpectedConditions.textToBePresentInElement(kardex, "Reposicion UAT"));
        String texto = kardex.getText();
        nota("Kardex: " + texto);
        assertTrue(texto.contains("ENTRADA") && texto.contains("SALIDA"));
        assertTrue(texto.contains("Venta UAT") && texto.contains("operador"));
        assertTrue(texto.contains("15") && texto.contains("13"), "Stock resultante 15 y luego 13");
    }

    // ------------------------------------------------------------------ HU-07 Roles

    @Test
    @Order(26)
    @DisplayName("CA-26 El OPERADOR no ve Notificaciones y por URL ve acceso restringido")
    void ca26() {
        loginOk("operador", "operador123");
        boolean veLink = !driver.findElements(By.xpath("//nav//a[normalize-space()='Notificaciones']")).isEmpty();
        nota("Ve el enlace Notificaciones: " + veLink);
        driver.get(WEB + "/notificaciones");
        String aviso = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(),'solo para el rol ADMIN')]"))).getText();
        nota("Aviso por URL directa: " + aviso);
        assertFalse(veLink);
    }

    @Test
    @Order(27)
    @DisplayName("CA-27 El OPERADOR no puede eliminar productos ni categorias")
    void ca27() {
        String token = ApiUat.token("admin", "admin123");
        String nombreCat = "UAT Rol " + sufijo();
        ApiUat.crearCategoria(token, nombreCat);
        String codigo = "UAT-ROL-" + sufijo();
        ApiUat.crearProducto(token, codigo, ApiUat.primeraCategoriaId(token), 5, 1, true);
        loginOk("operador", "operador123");

        driver.get(WEB + "/productos");
        esperarCarga();
        List<WebElement> botonProducto = fila(codigo).findElements(By.xpath(".//button[normalize-space()='Eliminar']"));
        if (!botonProducto.isEmpty()) {
            clickEn(fila(codigo), "Eliminar");
            nota("Producto - el operador ve 'Eliminar'. Respuesta: " + confirmarYLeerAlerta());
            driver.navigate().refresh();
            esperarCarga();
        } else {
            nota("Producto - el boton Eliminar no se muestra al operador.");
        }
        boolean productoSigue = existeFila(codigo);
        nota("El producto sigue existiendo: " + productoSigue);

        driver.get(WEB + "/categorias");
        esperarCarga();
        List<WebElement> botonCategoria = fila(nombreCat).findElements(By.xpath(".//button[normalize-space()='Eliminar']"));
        if (!botonCategoria.isEmpty()) {
            clickEn(fila(nombreCat), "Eliminar");
            nota("Categoria - el operador ve 'Eliminar'. Respuesta: " + confirmarYLeerAlerta());
            driver.navigate().refresh();
            esperarCarga();
        } else {
            nota("Categoria - el boton Eliminar no se muestra al operador.");
        }
        boolean categoriaSigue = existeFila(nombreCat);
        nota("La categoria sigue existiendo: " + categoriaSigue);

        assertTrue(productoSigue, "El OPERADOR pudo eliminar un producto");
        assertTrue(categoriaSigue, "El OPERADOR pudo eliminar una categoria");
    }

    // ------------------------------------------------------------------ HU-08 Notificaciones

    @Test
    @Order(28)
    @DisplayName("CA-28 El ADMIN ve en Notificaciones el movimiento de un OPERADOR")
    void ca28() {
        String codigo = "UAT-NOT-" + sufijo();
        long id = prepararProductoParaMovimiento(codigo, 10);
        ApiUat.registrarMovimiento(ApiUat.token("operador", "operador123"), id, "SALIDA", 1, "Venta notificada UAT");
        loginOk("admin", "admin123");
        driver.get(WEB + "/notificaciones");
        By filaMovimiento = By.xpath("//tbody/tr[td[contains(.,'" + codigo + "')]]");
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(filaMovimiento));
        } catch (TimeoutException ex) {
            List<WebElement> errores = driver.findElements(By.cssSelector("p.bg-red-50"));
            nota("Buzon ADMIN sin el movimiento. Error mostrado: "
                    + (errores.isEmpty() ? "(ninguno)" : errores.get(0).getText()));
            fail("El ADMIN no ve el movimiento del operador en Notificaciones");
        }
        nota("Buzon ADMIN: " + driver.findElement(filaMovimiento).getText());
        click(By.xpath("//button[contains(.,'Auditoria completa')]"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(filaMovimiento));
        nota("Auditoria: " + driver.findElement(filaMovimiento).getText());
    }

    // ------------------------------------------------------------------ No funcionales

    @Test
    @Order(29)
    @DisplayName("CA-29 El frontend envia cabeceras de seguridad HTTP")
    void ca29() {
        HttpResponse<String> r = ApiUat.getFrontend(WEB + "/login");
        List<String> requeridas = List.of("content-security-policy", "x-frame-options", "x-content-type-options",
                "referrer-policy", "permissions-policy");
        List<String> faltan = new ArrayList<>();
        for (String h : requeridas) {
            String valor = r.headers().firstValue(h).orElse(null);
            nota(h + ": " + (valor == null ? "(ausente)" : valor));
            if (valor == null) {
                faltan.add(h);
            }
        }
        driver.get(WEB + "/login");
        assertTrue(faltan.isEmpty(), "Faltan cabeceras de seguridad: " + faltan);
    }

    @Test
    @Order(30)
    @DisplayName("CA-30 Los errores no exponen detalles tecnicos al usuario")
    void ca30() {
        String token = ApiUat.token("admin", "admin123");
        String a = "UAT-T1-" + sufijo();
        String b = "UAT-T2-" + sufijo();
        long cat = ApiUat.primeraCategoriaId(token);
        ApiUat.crearProducto(token, a, cat, 1, 1, true);
        long idB = ApiUat.crearProducto(token, b, cat, 1, 1, true);
        ApiUat.Respuesta r = ApiUat.enviar("PUT", ApiUat.PRODUCT + "/api/productos/" + idB, token, Map.of(
                "codigo", a, "nombre", "x", "categoriaId", cat, "precio", 1, "stock", 1, "stockMinimo", 1, "activo", true));
        String mensaje = r.body().path("message").asText("");
        nota("Respuesta HTTP " + r.status() + " al provocar un conflicto de datos: " + mensaje);
        loginOk("admin", "admin123");
        sinTerminosTecnicos(mensaje);
        assertTrue(r.status() < 500, "El servidor respondio con error interno (" + r.status() + ") a un error del usuario");
    }

    // ------------------------------------------------------------------ HU-02 2FA (al final: modifica la cuenta)

    private String secreto2fa;

    private void activar2fa() throws InterruptedException {
        driver.get(WEB + "/seguridad");
        click(By.xpath("//button[contains(.,'Configurar') or contains(.,'Activar')]"));
        WebElement secreto = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//p[contains(.,'Codigo manual')]/following-sibling::*[1]")));
        secreto2fa = secreto.getText().trim();
        Totp.esperarVentanaSegura();
        escribir(By.id("setup-code"), Totp.codigo(secreto2fa));
        click(By.xpath("//form//button[@type='submit']"));
    }

    @Test
    @Order(90)
    @DisplayName("CA-06 Activar 2FA con un codigo valido muestra la confirmacion")
    void ca06() throws InterruptedException {
        loginOk("operador", "operador123");
        activar2fa();
        String ok = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(),'activada correctamente')]"))).getText();
        nota("Mensaje: " + ok);
        Files2fa.guardar(secreto2fa);
    }

    @Test
    @Order(91)
    @DisplayName("CA-07 Con 2FA activo el login pide codigo y rechaza uno incorrecto")
    void ca07() throws InterruptedException {
        String secreto = Files2fa.leer();
        iniciarSesion("operador", "operador123");
        WebElement campo = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("code")));
        campo.sendKeys("000000".equals(Totp.codigo(secreto)) ? "111111" : "000000");
        click(By.xpath("//button[@type='submit']"));
        String error = mensajeDeError();
        nota("Codigo incorrecto -> " + error);
        Totp.esperarVentanaSegura();
        escribir(By.id("code"), Totp.codigo(secreto));
        click(By.xpath("//button[@type='submit']"));
        wait.until(ExpectedConditions.urlContains("/menu"));
        nota("Codigo correcto -> " + driver.getCurrentUrl());
    }

    @Test
    @Order(92)
    @DisplayName("CA-08 Desactivar 2FA hace que el login ya no pida codigo")
    void ca08() throws InterruptedException {
        String secreto = Files2fa.leer();
        iniciarSesion("operador", "operador123");
        Totp.esperarVentanaSegura();
        escribir(By.id("code"), Totp.codigo(secreto));
        click(By.xpath("//button[@type='submit']"));
        wait.until(ExpectedConditions.urlContains("/menu"));
        driver.get(WEB + "/seguridad");
        click(By.xpath("//button[contains(.,'Desactivar')]"));
        confirmarYLeerAlerta();
        String ok = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(),'desactivada')]"))).getText();
        nota("Mensaje: " + ok);
        click(By.xpath("//button[normalize-space()='Cerrar sesion']"));
        wait.until(ExpectedConditions.urlContains("/login"));
        loginOk("operador", "operador123");
        nota("Login posterior sin codigo -> " + driver.getCurrentUrl());
    }

    /** Comparte el secreto 2FA entre CA-06, CA-07 y CA-08 (cada prueba usa un navegador nuevo). */
    private static final class Files2fa {
        private static final Path ARCHIVO = Path.of("target", "uat-2fa-secreto.txt");

        static void guardar(String secreto) {
            try {
                Files.createDirectories(ARCHIVO.getParent());
                Files.writeString(ARCHIVO, secreto);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        static String leer() {
            try {
                return Files.readString(ARCHIVO).trim();
            } catch (IOException ex) {
                throw new IllegalStateException("CA-06 no dejo un secreto 2FA", ex);
            }
        }
    }
}
