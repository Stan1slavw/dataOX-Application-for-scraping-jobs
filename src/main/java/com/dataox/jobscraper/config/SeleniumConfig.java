package com.dataox.jobscraper.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SeleniumConfig {

    @Value("${selenium.remote-url:#{null}}")
    private String remoteUrl;

    @PostConstruct
    public void setupDriver() {
        // локальный драйвер нужен только когда remote не используется
        if (remoteUrl == null || remoteUrl.isBlank()) {
            io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
        }
    }

    @Bean
    public ChromeOptions chromeOptions() {
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--window-size=1920,1080",
                "--lang=en-US",
                "--disable-blink-features=AutomationControlled",
                "--user-agent=Mozilla/5.0"
        );
        opts.setAcceptInsecureCerts(true);
        opts.setPageLoadStrategy(org.openqa.selenium.PageLoadStrategy.EAGER);
        opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        opts.setExperimentalOption("useAutomationExtension", false);
        return opts;
    }
}
