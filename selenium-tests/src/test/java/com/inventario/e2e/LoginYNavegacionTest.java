package com.inventario.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de automatizacion (Selenium WebDriver + Chrome headless) contra el frontend
 * real del Gestor de Inventario, corriendo en http://localhost:3000. Requiere que el
 * frontend y los microservicios ya esten levantados.
 */
class LoginYNavegacionTest {

    private static final String BASE_URL = "http://localhost:3000";

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1366,900", "--disable-gpu", "--no-sandbox");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
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
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }

    @Test
    void loginConCredencialesValidas_debeRedirigirAlMenu() {
        login("operador", "operador123");

        wait.until(ExpectedConditions.urlContains("/menu"));

        assertTrue(driver.getCurrentUrl().endsWith("/menu"));
        // Scope al <header>: el <title> de la pestaña tambien contiene este texto pero nunca esta "visible"
        WebElement encabezado = driver.findElement(By.xpath("//header//*[contains(text(),'Gestor de Inventario')]"));
        assertTrue(encabezado.isDisplayed());
    }

    @Test
    void loginConCredencialesInvalidas_debeMostrarErrorYNoRedirigir() {
        login("operador", "clave-incorrecta");

        WebElement error = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".text-red-700")));

        assertTrue(error.getText().length() > 0);
        assertTrue(driver.getCurrentUrl().endsWith("/login"));
    }

    @Test
    void navegarAProductos_debeMostrarElListadoDeProductos() {
        login("operador", "operador123");
        wait.until(ExpectedConditions.urlContains("/menu"));

        driver.findElement(By.xpath("//a[@href='/productos']")).click();
        wait.until(ExpectedConditions.urlContains("/productos"));

        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(text(),'Cargando')]")));
        WebElement tabla = driver.findElement(By.tagName("table"));
        assertTrue(tabla.isDisplayed());
    }

    @Test
    void cerrarSesion_debeRedirigirAlLogin() {
        login("operador", "operador123");
        wait.until(ExpectedConditions.urlContains("/menu"));

        driver.findElement(By.xpath("//button[contains(text(),'Cerrar sesion')]")).click();
        wait.until(ExpectedConditions.urlContains("/login"));

        assertEquals(BASE_URL + "/login", driver.getCurrentUrl());
    }
}
