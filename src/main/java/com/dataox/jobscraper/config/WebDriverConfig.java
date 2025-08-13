package com.dataox.jobscraper.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URL;
import java.time.Duration;
import java.util.Arrays;

@Configuration
public class WebDriverConfig {

    @Value("${SELENIUM_REMOTE_URL:}")
    private String remoteUrl;

    @Value("${SELENIUM_SCRIPT_TIMEOUT_SEC:60}")
    private long scriptTimeoutSec;

    @Bean(destroyMethod = "quit")
    public WebDriver webDriver() throws Exception {
        ChromeOptions opts = new ChromeOptions();
        // устойчивые флаги для докера/CI
        opts.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-notifications",
                "--window-size=1920,1080"
        );

        WebDriver driver;
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            // Remote (Selenium в контейнере)
            driver = new RemoteWebDriver(new URL(remoteUrl), opts);
        } else {
            // Локальный Chrome
            driver = new ChromeDriver(opts);
        }

        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(scriptTimeoutSec));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        return driver;
    }
}
