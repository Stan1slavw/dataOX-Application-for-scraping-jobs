package com.dataox.jobscraper.controller;

import com.dataox.jobscraper.entity.Job;
import com.dataox.jobscraper.repository.JobRepository;
import com.dataox.jobscraper.service.JobScraperService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;

@Controller
public class WebController {

    private final JobScraperService scraperService;
    private final JobRepository jobRepository;

    public WebController(JobScraperService scraperService, JobRepository jobRepository) {
        this.scraperService = scraperService;
        this.jobRepository = jobRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("functions", List.of(
                "Software Engineering",
                "Product Management",
                "Data Science",
                "Design",
                "Marketing",
                "Operations"
        ));
        return "index";
    }

    @PostMapping("/jobs")
    public String getJobs(@RequestParam(value = "function", required = false) String function,
                          @RequestParam(value = "slug",     required = false) String slug,
                          Model model) throws IOException {

        String raw = (slug != null && !slug.isBlank())
                ? slug.trim()
                : (function != null ? function.trim() : "");

        if (raw.isBlank()) {
            model.addAttribute("jobs", List.of());
            model.addAttribute("function", "");
            return "jobs";
        }

        String normSlug = toSlug(raw);

        // запускаем парсер
        scraperService.scrapeByFunction(normSlug);
        jobRepository.flush();

        // дамп в SQL (кроссплатформенный путь)
        Path dumpPath = resolveDumpPath();
        scraperService.dumpToSql(dumpPath);

        // фильтрация для отображения
        List<Job> all = jobRepository.findAll();
        String spaced = normSlug.replace("-", " ");
        String[] tokens = normSlug.split("-");

        List<Job> jobs = all.stream().filter(j -> {
            String lf   = safe(j.getLaborFunction());
            String url  = safe(j.getJobUrl());
            String name = safe(j.getPositionName());
            if (lf.contains(normSlug) || url.contains(normSlug) || name.contains(normSlug)
                    || lf.contains(spaced) || name.contains(spaced)) return true;
            for (String t : tokens) {
                if (!t.isBlank() && (lf.contains(t) || name.contains(t))) return true;
            }
            return false;
        }).toList();

        if (jobs.isEmpty()) jobs = all;

        model.addAttribute("jobs", jobs);
        model.addAttribute("function", raw);
        System.out.println("Scraped slug = " + normSlug + ", jobs returned = " + jobs.size());
        System.out.println("SQL dump -> " + dumpPath.toAbsolutePath());
        return "jobs";
    }

    @GetMapping("/job/{id}")
    public String jobDetail(@PathVariable Long id, Model model) {
        return jobRepository.findById(id)
                .map(job -> { model.addAttribute("job", job); return "job_detail"; })
                .orElse("redirect:/");
    }

    // ---------- helpers ----------

    private Path resolveDumpPath() {
        // 1) переменная окружения DUMPS_DIR
        String envDir = System.getenv("DUMPS_DIR");
        // 2) системное свойство -Ddumps.dir=...
        String propDir = System.getProperty("dumps.dir");

        Path dir;
        if (envDir != null && !envDir.isBlank()) {
            dir = Paths.get(envDir);
        } else if (propDir != null && !propDir.isBlank()) {
            dir = Paths.get(propDir);
        } else {
            // 3) дефолт: локальная папка проекта ./dumps (IDE)
            dir = Paths.get(System.getProperty("user.dir"), "dumps");
        }

        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir.resolve("dump.sql");
    }

    private String safe(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    private String toSlug(String src) {
        if (src == null) return "";
        return src.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9-]", "");
    }
}
