# Techstars Job Scraper

**Techstars Job Scraper** is a Java Spring Boot web application for automatically collecting vacancies from the Techstars platform.

The program uses Selenium to control the browser and parse pages, PostgreSQL to store data, and also supports exporting results to a `.sql` file.

Suitable for analyzing open vacancies by functions (Software Engineering, Product Management, Design, etc.) and quickly finding positions in Techstars companies.

## 🚀 Features
- Parsing vacancies from [jobs.techstars.com](https://jobs.techstars.com)
- Filtering by function or slug
- Saving to PostgreSQL
- Exporting data to SQL dump
- Working via Docker or directly from IDE
- Built-in web interface on Thymeleaf

## 🛠️ Tech stack
- **Java 21**
- **Spring Boot**
- **Selenium**
- **PostgreSQL**
- **Docker**
- **Thymeleaf**

---

## 📦 Installation and launch

### 1. Launch via Docker
```bash
docker compose up --build
