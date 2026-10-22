package com.inventario.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas E2E de flujos de negocio reales (Selenium WebDriver + Chrome headless) contra el
 * frontend del Gestor de Inventario: reportes/kardex y control de acceso por rol. Mezcla casos
 * positivos (camino feliz) y negativos (seguridad que debe restringir, no solo aceptar).
 */
class FlujosDeNegocioTest {

    private static final String BASE_URL = "http://localhost:3000";

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1366,900", "--disable-gpu", "--no-sandbox",
                "--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    private void login(String usuario, String password) {
        driver.get(BASE_URL + "/login");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(usuario);
        driver.findElement(By.id("password")).sendKeys(password);
        click(By.cssSelector("button[type='submit']"));
        wait.until(ExpectedConditions.urlContains("/menu"));
    }

    private void click(WebElement elemento) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", elemento);
    }

    private void click(By locator) {
        click(wait.until(ExpectedConditions.elementToBeClickable(locator)));
    }

    // ---------- Reportes ----------

    @Test
    void verReporteDeStock_debeMostrarResumenYListado() {
        login("operador", "operador123");
        driver.get(BASE_URL + "/reportes");

        WebElement valorTotal = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//p[text()='Valor total inventario']")));
        assertTrue(valorTotal.isDisplayed());

        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(text(),'Cargando')]")));
        WebElement tabla = driver.findElement(By.tagName("table"));
        assertTrue(tabla.isDisplayed());
    }

    @Test
    void verKardexDeUnProducto_debeMostrarSuHistorial() {
        login("operador", "operador123");
        driver.get(BASE_URL + "/reportes");

        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(text(),'Cargando')]")));
        click(By.xpath("(//button[contains(text(),'Ver historial')])[1]"));

        WebElement kardex = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//h2[contains(text(),'Kardex')]")));
        assertTrue(kardex.isDisplayed());
    }

    // ---------- Seguridad / control de acceso ----------

    @Test
    void accederARutaProtegidaSinSesion_debeRedirigirALogin() {
        // Sin login previo: cada @Test levanta un ChromeDriver nuevo, sin localStorage ni sesion.
        driver.get(BASE_URL + "/productos");
        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().endsWith("/login"));
    }

    @Test
    void operadorAccedeDirectoANotificaciones_debeMostrarRestriccionDeRol() {
        login("operador", "operador123");

        // El link "Notificaciones" es solo-ADMIN: no debe aparecer en el menu para un operador.
        assertTrue(driver.findElements(By.xpath("//nav//a[@href='/notificaciones']")).isEmpty());

        // Aunque el operador navegue directo por URL, la pagina debe bloquear el contenido.
        driver.get(BASE_URL + "/notificaciones");
        WebElement restriccion = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(text(),'Esta seccion es solo para el rol ADMIN')]")));
        assertTrue(restriccion.isDisplayed());
        assertFalse(driver.getPageSource().contains("Azure Service Bus"));
    }
}
