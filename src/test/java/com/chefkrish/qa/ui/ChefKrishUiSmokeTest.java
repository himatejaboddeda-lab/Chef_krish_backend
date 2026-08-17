package com.chefkrish.qa.ui;

import com.chefkrish.qa.config.TestConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.*;

/**
 * End-to-end smoke test against the LIVE GitHub Pages deployment
 * (himatejaboddeda-lab.github.io/Chef_krish_backend/) — the actual page a
 * customer loads, not a local copy. Runs headless so it works on a Jenkins
 * agent with no display. Element selectors below are read directly from
 * index.html's real markup (#uinput, #msgs) — update them here if the
 * frontend markup changes, since Jenkins will otherwise fail loudly on the
 * NEXT run rather than silently testing nothing.
 */
public class ChefKrishUiSmokeTest {

    private WebDriver driver;

    @BeforeClass
    public void setUp() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1280,900");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
    }

    @AfterClass
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    @Test(description = "Frontend loads and renders the Chef Krish chat shell")
    public void pageLoadsWithChatInput() {
        driver.get(TestConfig.FRONTEND_URL);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("uinput")));
        assertTrue(input.isDisplayed(), "Chat input textarea (#uinput) should be visible on load.");
        assertTrue(driver.getTitle().contains("Chef Krish"), "Expected page title to reference Chef Krish, got: " + driver.getTitle());
    }

    @Test(description = "Sending a message through the real UI produces a bot reply", dependsOnMethods = "pageLoadsWithChatInput")
    public void sendingMessageProducesReply() {
        driver.get(TestConfig.FRONTEND_URL);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("uinput")));

        input.sendKeys("hello how are you");
        // The send button has no id in the current markup — it's the
        // second .ibtn in the input bar (the mic button is the first).
        WebElement sendBtn = driver.findElements(By.cssSelector(".ibar .ibtn")).get(1);
        sendBtn.click();

        // Wait for a second message bubble (.msg.bot) to appear beyond the
        // one already rendered on load, allowing for the real Worker
        // round-trip (Business Graph + persona wording).
        WebDriverWait replyWait = new WebDriverWait(driver, Duration.ofSeconds(20));
        replyWait.until(d -> d.findElements(By.cssSelector("#msgs .msg.bot")).size() >= 1);

        WebElement lastBotBubble = driver.findElements(By.cssSelector("#msgs .msg.bot .bub"))
            .get(driver.findElements(By.cssSelector("#msgs .msg.bot .bub")).size() - 1);
        assertFalse(lastBotBubble.getText().trim().isEmpty(), "Expected a non-empty reply from Chef Krish.");
    }
}
