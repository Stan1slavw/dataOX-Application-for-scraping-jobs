package com.dataox.jobscraper.service;

import com.dataox.jobscraper.entity.Job;
import com.dataox.jobscraper.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobScraperService {

    private final JobRepository jobRepository;
    private final ChromeOptions chromeOptions;
    private final String remoteUrl;

    public JobScraperService(JobRepository jobRepository,
                             ChromeOptions chromeOptions,
                             @Value("${SELENIUM_REMOTE_URL:}") String remoteUrl) {
        this.jobRepository = jobRepository;
        this.chromeOptions = chromeOptions;
        this.remoteUrl = remoteUrl == null ? "" : remoteUrl.trim();
    }

    private WebDriver newDriver() {
        try {
            if (!remoteUrl.isBlank()) {
                System.out.println("[Selenium] Using remote grid: " + remoteUrl);
                try {
                    return new RemoteWebDriver(new URL(remoteUrl), chromeOptions);
                } catch (Exception e) {
                    String alt = remoteUrl.endsWith("/") ? remoteUrl + "wd/hub" : remoteUrl + "/wd/hub";
                    System.out.println("[Selenium] Retry with: " + alt);
                    return new RemoteWebDriver(new URL(alt), chromeOptions);
                }
            }
            System.out.println("[Selenium] Using local ChromeDriver");
            return new org.openqa.selenium.chrome.ChromeDriver(chromeOptions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create WebDriver for " +
                    (remoteUrl.isBlank() ? "local ChromeDriver" : remoteUrl), e);
        }
    }

    public int scrapeByFunction(String functionSlugOrQuery) {
        String q = functionSlugOrQuery == null ? "" : functionSlugOrQuery.trim();
        String slug = toSlug(q);

        WebDriver driver = newDriver();
        try {
            List<Job> fromList = collectFromList(driver, q, slug);
            int saved = 0;

            for (Job j : fromList) {
                String url = normalizeUrl(j.getJobUrl());
                if (url == null) continue;

                if (jobRepository.existsByJobUrl(url)) continue;

                boolean needDetail =
                        isBlank(j.getDescription()) ||
                                j.getPostedDate() == null ||
                                isBlank(j.getLogoUrl()) ||
                                j.getTags() == null || j.getTags().isEmpty();

                if (needDetail && url.contains("jobs.techstars.com")) {
                    enrichFromDetail(driver, j);
                }

                if (isBlank(j.getPositionName())) j.setPositionName(titleFromJobUrl(url));
                if (isBlank(j.getOrganizationTitle())) j.setOrganizationTitle(companyFromUrl(url));
                if (isBlank(j.getLaborFunction())) j.setLaborFunction(slug);

                if (j.getPostedDate() == null && j.getJobUrl() != null && j.getJobUrl().contains("jobs.techstars.com")) {
                    try { enrichFromDetail(driver, j); } catch (Exception ignore) {}
                }
                if (j.getPostedDate() == null) {
                    j.setPostedDate(Instant.now().getEpochSecond());
                }

                System.out.println("[Fast] tags for " + url + " -> " + j.getTags());

                jobRepository.save(j);
                saved++;
                System.out.println("[Fast] Saved: " + safe(j.getPositionName()) + " | " + safe(j.getOrganizationTitle()));
            }
            System.out.println("[Fast] saved=" + saved);
            return saved;
        } finally {
            driver.quit();
        }
    }

    public List<Job> scrapeAndReturn(String function) {
        scrapeByFunction(function);

        String slug = toSlug(function);
        String spaced = slug.replace("-", " ");
        String[] tokens = slug.split("-");

        List<Job> all = jobRepository.findAll();
        List<Job> jobs = all.stream().filter(j -> {
            String lf = safeLower(j.getLaborFunction());
            String url = safeLower(j.getJobUrl());
            String name = safeLower(j.getPositionName());
            if (lf.contains(slug) || url.contains(slug) || name.contains(slug) ||
                    lf.contains(spaced) || name.contains(spaced)) return true;
            for (String t : tokens) if (!t.isBlank() && (lf.contains(t) || name.contains(t))) return true;
            return false;
        }).collect(Collectors.toList());

        if (jobs.isEmpty()) jobs = all;
        return jobs;
    }

    /* ===================== LIST PAGE ===================== */

    @SuppressWarnings("unchecked")
    private List<Job> collectFromList(WebDriver driver, String queryOrSlug, String slug) {
        String listUrl = "https://jobs.techstars.com/jobs?jobFunction=" + slug + "&q=" + urlEncode(queryOrSlug);
        driver.get(listUrl);

        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(d -> ((JavascriptExecutor) d).executeScript(
                        "return document.querySelectorAll(\"[data-testid*='job']\").length > 0"));

        expandAllJobs(driver);

        Object res = ((JavascriptExecutor) driver).executeScript("""
        const abs = (u) => {
          if (!u) return '';
          try { return new URL(u, window.location.origin).href; } catch(e) { return u; }
        };

        const pickSrcFromSrcset = (ss) => {
          if (!ss) return '';
          const first = (ss.split(',')[0] || '').trim();
          return first.split(' ')[0] || '';
        };

        const isPlaceholder = (u) => {
          if (!u) return true;
          const x = u.toLowerCase();
          return x.startsWith('data:') ||
                 x.includes('placeholder') ||
                 x.endsWith('/logo.png') ||
                 x.endsWith('/logo.svg') ||
                 x.includes('default') ||
                 x.includes('sprite') ||
                 x.includes('onetrust') ||
                 x.includes('cookie');
        };

        const pickLogo = (root) => {
          const candidates = Array.from(root.querySelectorAll(
            "img.organization-logo, img.company-logo, [class*='logo'] img, picture img, img[alt*='logo' i], img"
          )).filter(img => {
            const alt = (img.getAttribute('alt') || '').toLowerCase();
            if (alt.includes('cookie') || alt.includes('onetrust')) return false;
            const w = img.naturalWidth || img.width || 0;
            const h = img.naturalHeight || img.height || 0;
            return (w * h) >= 900;
          });

          candidates.sort((a,b) =>
            ((b.naturalWidth||b.width||0)*(b.naturalHeight||b.height||0)) -
            ((a.naturalWidth||a.width||0)*(a.naturalHeight||a.height||0))
          );

          for (const img of candidates) {
            let url = img.getAttribute('src') || img.getAttribute('data-src') || img.getAttribute('data-lazy-src') || '';
            if (!url) url = pickSrcFromSrcset(img.getAttribute('srcset'));
            url = abs(url);
            if (url && !isPlaceholder(url)) return url;
          }

          const box = root.querySelector("[class*='logo']");
          if (box) {
            const bg = getComputedStyle(box).backgroundImage || '';
            const m = bg && bg.match(/url\\((["'])?(.*?)\\1\\)/i);
            const url = m && abs(m[2]);
            if (url && !isPlaceholder(url)) return url;
          }
          return '';
        };

        const getLocation = (root) => {
          // 1) пробуем schema.org: собрать локаль/регион/страну
          const adr = root.querySelector('[itemprop="jobLocation"] [itemprop="address"], [itemprop="address"]');
          if (adr) {
            const city = adr.querySelector('[itemprop="addressLocality"]')?.textContent?.trim() || '';
            const region = adr.querySelector('[itemprop="addressRegion"]')?.textContent?.trim() || '';
            const country = adr.querySelector('[itemprop="addressCountry"]')?.textContent?.trim() || '';
            const parts = [city, region, country].filter(Boolean);
            if (parts.length) return parts.join(', ');
          }
          // meta content (если вдруг есть)
          const meta = root.querySelector('[itemprop="jobLocation"] meta[itemprop="address"][content], meta[itemprop="address"][content]');
          let loc = (meta?.getAttribute('content') || '').trim();
          if (loc) return loc;

          // 2) fallback: видимый текст
          const locNode = root.querySelector('[itemprop="jobLocation"], .location, .job-location, [data-testid*="location"], [class*="location"]');
          if (!locNode) return '';
          loc = (locNode.textContent || locNode.innerText || '').replace(/\\s+/g,' ').trim();

          // убрать хвосты "• 2 months", "3 weeks", "days ago"
          loc = loc
            .replace(/\\b\\d+\\s+(day|days|week|weeks|month|months|year|years)\\b/ig, '')
            .replace(/\\b(ago|posted|posting)\\b/ig, '')
            .replace(/\\s*(?:•|\\||·)\\s*$/g, '')
            .replace(/\\s{2,}/g, ' ')
            .trim();

          return loc;
        };

        const out = [];
        const cards = document.querySelectorAll("[data-testid*='job']");
        cards.forEach(card => {
          const a = (card.closest("a[href*='/companies/'][href*='/jobs/']") ||
                     card.querySelector("a[href*='/companies/'][href*='/jobs/']"));
          if (!a) return;

          let href = a.getAttribute('href') || '';
          if (href && !href.startsWith('http')) href = window.location.origin + (href.startsWith('/')?'':'/') + href;
          if (!href.includes('/companies/') || !href.includes('/jobs/') || href.endsWith('/jobs')) return;
          const url = href.split('#')[0];

          const titleEl   = card.querySelector('h3, h2, .title, .job-title') || a;
          const companyEl = card.querySelector('.company, .company-name, [data-testid*="company"]');

          const iso = card.querySelector('meta[itemprop="datePosted"]')?.getAttribute('content') || '';

          const tags = Array.from(card.querySelectorAll(
            "[data-testid='tag'], [data-testid*='tag']," +
            ".tags .tag, .job-tags .tag, .labels span, .pill, .chip, .badge, [class*='Tag']"
          )).map(n => (n.textContent || n.innerText || '').replace(/\\s+/g,' ').trim())
           .filter(t => t && !/^read more$/i.test(t) && !/^apply$/i.test(t));

          const logo = pickLogo(card);
          const jobLocation = getLocation(card);

          out.push({
            url,
            title:   (titleEl?.textContent || '').trim(),
            company: (companyEl?.textContent || '').trim(),
            location: jobLocation,
            isoDate: iso,
            logoUrl: logo,
            tags
          });
        });
        return out;
        """);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) res;
        List<Job> jobs = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Job j = new Job();
            j.setJobUrl(String.valueOf(r.get("url")));
            j.setPositionName((String) r.get("title"));
            j.setOrganizationTitle((String) r.get("company"));
            j.setLocation((String) r.get("location"));
            j.setLaborFunction(slug);

            Long ts = parseIsoDateToUnix((String) r.get("isoDate"));
            if (ts != null) j.setPostedDate(ts);

            List<String> tags = (List<String>) r.get("tags");
            if (tags != null) j.setTags(tags);

            String logo = (String) r.get("logoUrl");
            if (logo != null && !logo.isBlank()) {
                String low = logo.toLowerCase();
                if (!low.startsWith("data:")
                        && !low.contains("placeholder")
                        && !low.endsWith("/logo.png")
                        && !low.endsWith("/logo.svg")
                        && !low.contains("sprite")
                        && !low.contains("onetrust")
                        && !low.contains("cookie")) {
                    j.setLogoUrl(logo);
                }
            }
            jobs.add(j);
        }
        System.out.println("[Fast] list collected: " + jobs.size());
        return jobs;
    }

    private void expandAllJobs(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        final By CARD_SEL = By.cssSelector("[data-testid*='job']");
        final By LOAD_MORE_BTN = By.xpath(
                "//button[contains(.,'Load more') or .//text()[contains(.,'Load more')]]" +
                        " | //a[contains(.,'Load more') or .//text()[contains(.,'Load more')]]"
        );

        int guard = 0;
        while (guard++ < 50) {
            int before = driver.findElements(CARD_SEL).size();

            List<WebElement> btns = driver.findElements(LOAD_MORE_BTN);
            if (!btns.isEmpty()) {
                WebElement btn = btns.get(0);
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", btn);
                try {
                    if (btn.isEnabled() && btn.isDisplayed()) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
                    }
                } catch (Exception ignored) {}
            } else {
                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
            }

            boolean grew = false;
            try {
                grew = wait.until(d -> {
                    int now = ((List<WebElement>) d.findElements(CARD_SEL)).size();
                    boolean noBtn = d.findElements(LOAD_MORE_BTN).isEmpty();
                    boolean disabled = false;
                    if (!noBtn) {
                        WebElement b = d.findElements(LOAD_MORE_BTN).get(0);
                        String dis = b.getAttribute("disabled");
                        String aria = b.getAttribute("aria-disabled");
                        disabled = "true".equalsIgnoreCase(aria) || dis != null;
                    }
                    return now > before || noBtn || disabled;
                });
            } catch (Exception ignored) {}

            int after = driver.findElements(CARD_SEL).size();
            if (!grew || after <= before) {
                if (driver.findElements(LOAD_MORE_BTN).isEmpty()) break;
                break;
            }
        }
    }

    /* ===================== DETAIL PAGE ENRICH ===================== */

    private void enrichFromDetail(WebDriver driver, Job j) {
        try {
            driver.get(j.getJobUrl());

            try {
                WebElement title = driver.findElement(By.cssSelector("h1, .job-title, .posting-title"));
                if (isBlank(j.getPositionName())) j.setPositionName(title.getText().trim());
            } catch (Exception ignored) {}

            try {
                WebElement comp = driver.findElement(By.cssSelector(".organization-name, .company-name, a[href*='/companies/']"));
                String txt = comp.getText();
                if (!isBlank(txt)) j.setOrganizationTitle(txt.trim());
                try {
                    String href = comp.getAttribute("href");
                    if (!isBlank(href)) j.setOrganizationUrl(href);
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}

            // LOGO (мягко, без влияния на теги/дату)
            try {
                String existing = safe(j.getLogoUrl());
                String found = null;

                List<By> logoSelectors = List.of(
                        By.cssSelector("img.organization-logo"),
                        By.cssSelector("img.company-logo"),
                        By.cssSelector("[class*='logo'] img"),
                        By.cssSelector("img[alt*='logo' i]"),
                        By.cssSelector("picture img")
                );
                for (By by : logoSelectors) {
                    try {
                        WebElement img = driver.findElement(by);
                        String src = img.getAttribute("src");
                        if (isBlank(src)) src = img.getAttribute("data-src");
                        if (isBlank(src)) src = img.getAttribute("data-lazy-src");
                        if (isBlank(src)) {
                            String srcset = img.getAttribute("srcset");
                            if (!isBlank(srcset)) {
                                String first = srcset.split(",")[0].trim().split(" ")[0];
                                if (!isBlank(first)) src = first;
                            }
                        }
                        if (!isBlank(src)) { found = normalizeUrl(src); break; }
                    } catch (Exception ignoredInner) {}
                }

                if (isBlank(found)) {
                    try {
                        WebElement box = driver.findElement(By.cssSelector("[class*='logo']"));
                        String bg = (String) ((JavascriptExecutor)driver).executeScript(
                                "return getComputedStyle(arguments[0]).backgroundImage;", box);
                        if (!isBlank(bg)) {
                            java.util.regex.Matcher m = java.util.regex.Pattern
                                    .compile("url\\((?:\"|')?(.*?)(?:\"|')?\\)", java.util.regex.Pattern.CASE_INSENSITIVE)
                                    .matcher(bg);
                            if (m.find()) found = normalizeUrl(m.group(1));
                        }
                    } catch (Exception ignoredInner) {}
                }

                if (isBlank(found)) {
                    try {
                        String og = (String) ((JavascriptExecutor)driver).executeScript(
                                "return document.querySelector(\"meta[property='og:image']\")?.getAttribute('content') || '';");
                        if (!isBlank(og)) found = normalizeUrl(og);
                    } catch (Exception ignoredInner) {}
                }

                if (isBlank(existing) && !isBlank(found)) {
                    j.setLogoUrl(found);
                } else if (!isBlank(existing) && !isBlank(found)) {
                    if (existing.startsWith("data:") || existing.contains("placeholder") || existing.endsWith("/logo.png")) {
                        j.setLogoUrl(found);
                    }
                }
            } catch (Exception ignored) {}

            try {
                WebElement loc = driver.findElement(By.cssSelector(".location, .job-location, .job-meta .location"));
                String txt = loc.getText();
                if (!isBlank(txt)) j.setLocation(txt.trim());
            } catch (Exception ignored) {}

            // Описание — сначала careerPage, потом как было
            try {
                WebElement d = null;
                try { d = driver.findElement(By.cssSelector("[data-testid='careerPage']")); } catch (Exception ignored) {}
                if (d == null) {
                    d = driver.findElement(By.cssSelector(".description, .job-description, .job-body, #job-description, article"));
                }
                String html = (String) ((JavascriptExecutor) driver).executeScript("return arguments[0].innerHTML;", d);
                if (!isBlank(html)) j.setDescription(html);
            } catch (Exception ignored) {}

            // Теги — как были
            try {
                Object arr = ((JavascriptExecutor) driver).executeScript("""
                return Array.from(document.querySelectorAll(
                  "[data-testid='tag'], [data-testid*='tag']," +
                  ".tags .tag, .job-tags .tag, .labels span, .pill, .chip, .badge, [class*='Tag']"
                )).map(n => (n.textContent || n.innerText || '').replace(/\\s+/g,' ').trim())
                 .filter(t => t && !/^read more$/i.test(t) && !/^apply$/i.test(t));
            """);
                if (arr instanceof List<?> list) {
                    LinkedHashSet<String> set = list.stream()
                            .map(String::valueOf)
                            .map(this::cleanTag)
                            .filter(s -> !s.isBlank())
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                    if (!set.isEmpty()) j.setTags(new ArrayList<>(set));
                }
            } catch (Exception ignored) {}

            // Дата — ISO из meta
            try {
                String iso = (String) ((JavascriptExecutor) driver).executeScript(
                        "return document.querySelector('meta[itemprop=\"datePosted\"]')?.getAttribute('content') || '';");
                Long ts = parseIsoDateToUnix(iso);
                if (ts != null) j.setPostedDate(ts);
            } catch (Exception ignored) {}

        } catch (Exception e) {
            System.err.println("[Fast] enrich error: " + e.getMessage());
        }
    }

    private Long parseIsoDateToUnix(String iso) {
        if (iso == null) return null;
        String s = iso.trim();
        if (s.isEmpty()) return null;
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(s);
            return d.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
        } catch (Exception e) {
            return null;
        }
    }

    /* ===================== SQL DUMP ===================== */

    /**
     * Пишет SQL-файл с созданием схемы и INSERT'ами текущих данных.
     * Пример вызова: jobScraperService.dumpToSql(Path.of("dump.sql"));
     */
    public void dumpToSql(Path outFile) throws IOException {
        List<Job> all = jobRepository.findAll();

        try (BufferedWriter w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            w.write("""
                -- Generated by JobScraperService.dumpToSql
                SET NAMES utf8mb4;
                SET FOREIGN_KEY_CHECKS=0;

                CREATE DATABASE IF NOT EXISTS `job_scraper` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
                USE `job_scraper`;

                DROP TABLE IF EXISTS `job_tags`;
                DROP TABLE IF EXISTS `jobs`;

                CREATE TABLE `jobs` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                  `position_name` VARCHAR(255),
                  `organization_title` VARCHAR(255),
                  `organization_url` VARCHAR(512),
                  `job_url` VARCHAR(512) NOT NULL,
                  `location` VARCHAR(255),
                  `labor_function` VARCHAR(128),
                  `posted_date` BIGINT,
                  `logo_url` VARCHAR(512),
                  `description` MEDIUMTEXT,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_job_url` (`job_url`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                CREATE TABLE `job_tags` (
                  `job_id` BIGINT NOT NULL,
                  `tag` VARCHAR(128) NOT NULL,
                  KEY `idx_job_tags_job_id` (`job_id`),
                  CONSTRAINT `fk_job_tags_job` FOREIGN KEY (`job_id`) REFERENCES `jobs` (`id`) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                """);

            // вставка jobs
            for (Job j : all) {
                String sql = String.format(Locale.ROOT,
                        "INSERT INTO `jobs` (`id`,`position_name`,`organization_title`,`organization_url`,`job_url`,`location`,`labor_function`,`posted_date`,`logo_url`,`description`) " +
                                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s);\n",
                        val(j.getId()),
                        val(j.getPositionName()),
                        val(j.getOrganizationTitle()),
                        val(j.getOrganizationUrl()),
                        val(j.getJobUrl()),
                        val(j.getLocation()),
                        val(j.getLaborFunction()),
                        val(j.getPostedDate()),
                        val(j.getLogoUrl()),
                        valLongText(j.getDescription())
                );
                w.write(sql);

                if (j.getTags() != null) {
                    for (String tag : j.getTags()) {
                        if (tag == null || tag.isBlank()) continue;
                        String tsql = String.format(Locale.ROOT,
                                "INSERT INTO `job_tags` (`job_id`,`tag`) VALUES (%s,%s);\n",
                                val(j.getId()), val(tag));
                        w.write(tsql);
                    }
                }
            }

            w.write("SET FOREIGN_KEY_CHECKS=1;\n");
        }
    }

    // безопасные литералы для SQL
    private String val(Object o) {
        if (o == null) return "NULL";
        if (o instanceof Number) return o.toString();
        String s = String.valueOf(o);
        // экранируем одинарные кавычки
        s = s.replace("'", "''");
        return "'" + s + "'";
    }
    private String valLongText(Object o) {
        if (o == null) return "NULL";
        String s = String.valueOf(o).replace("'", "''");
        return "'" + s + "'";
    }

    /* ===================== HELPERS ===================== */

    private void waitReady(WebDriver d) {
        try {
            new WebDriverWait(d, Duration.ofSeconds(15))
                    .until(dr -> "complete".equals(((JavascriptExecutor) dr).executeScript("return document.readyState")));
        } catch (Exception ignored) {}
    }

    private String firstText(WebDriver d, String css) {
        List<WebElement> els = d.findElements(By.cssSelector(css));
        return els.isEmpty() ? null : els.getFirst().getText();
    }
    private String firstAttr(WebDriver d, String css, String attr) {
        List<WebElement> els = d.findElements(By.cssSelector(css));
        return els.isEmpty() ? null : els.getFirst().getAttribute(attr);
    }
    private String firstHtml(WebDriver d, String css) {
        List<WebElement> els = d.findElements(By.cssSelector(css));
        return els.isEmpty() ? null : els.getFirst().getAttribute("innerHTML");
    }

    private String cleanText(String s) { return s == null ? null : s.replaceAll("\\s+", " ").trim(); }
    private String cleanTag(String s) {
        if (s == null) return "";
        String v = s.replaceAll("\\s+", " ").trim();
        if (v.equalsIgnoreCase("Read more") || v.equalsIgnoreCase("Apply")) return "";
        return v;
    }
    private String urlEncode(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8); } catch (Exception e) { return s; }
    }
    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    private String safe(String s) { return s == null ? "" : s; }
    private String safeLower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String normalizeUrl(String href) {
        if (href == null) return null;
        String u = href.trim();
        int hash = u.indexOf('#'); if (hash > -1) u = u.substring(0, hash);
        if (u.endsWith("/") && u.length() > "https://x/".length()) u = u.substring(0, u.length() - 1);
        return u;
    }
    private String toSlug(String src) {
        if (src == null) return "";
        return src.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9-]", "");
    }

    private Long parseDateToUnix(String raw) {
        if (isBlank(raw)) return null;
        try { return Instant.parse(raw).getEpochSecond(); } catch (Exception ignored) {}
        return null;
    }

    private String titleFromJobUrl(String url) {
        if (isBlank(url)) return null;
        try {
            String[] parts = url.split("/");
            String last = parts[parts.length - 1]
                    .replaceAll("\\?.*$", "")
                    .replaceAll("#.*$", "")
                    .replaceAll("^[0-9]+-", "");
            if (last.matches("^[0-9]+$")) return null;
            String words = last.replace("-", " ").trim();
            return toTitleCase(words);
        } catch (Exception e) { return null; }
    }

    private String companyFromUrl(String url) {
        if (isBlank(url)) return null;
        try {
            String[] parts = url.split("/");
            for (int i = 0; i < parts.length; i++) {
                if ("companies".equals(parts[i]) && i + 1 < parts.length) {
                    String slug = parts[i + 1].replace("-", " ").replace("_", " ").trim();
                    return toTitleCase(slug);
                }
            }
            return null;
        } catch (Exception e) { return null; }
    }

    private String toTitleCase(String s) {
        if (isBlank(s)) return s;
        StringBuilder sb = new StringBuilder(s.length());
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) { cap = true; sb.append(c); }
            else if (cap) { sb.append(Character.toTitleCase(c)); cap = false; }
            else { sb.append(Character.toLowerCase(c)); }
        }
        return sb.toString();
    }
}
