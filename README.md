# Techstars Job Scraper

**Techstars Job Scraper** is a Java Spring Boot web application for automatically collecting vacancies from the Techstars platform.

The program uses Selenium to control the browser and parse pages, PostgreSQL to store data, and also supports exporting results to a `.sql` file.

Suitable for analyzing open vacancies by functions (Software Engineering, Product Management, Design, etc.) and quickly finding positions in Techstars companies.

## 🚀 Features
- Parsing vacancies from [jobs.techstars.com](https://jobs.techstars.com)
- Filtering by function or slug
- Saving to PostgreSQL
- Exporting data to SQL dump. Dumb.sql is located in the root of the project in the dumbs folder
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
bash
docker compose up --build

After launch:

The application is available at http://localhost:8080

Selenium Grid at http://localhost:4444

2. Launch from IDE
Make sure PostgreSQL and Selenium are running (you can do this via Docker Compose).

Add environment variables:

env
Copy
Edit
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jobsdb
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=Stasqwerty123
SELENIUM_REMOTE_URL=http://localhost:4444/
DUMPS_DIR=./data
Run the ScrapperDataOXApplication class.

📂 Project structure
controller/ — web controllers (UI and API)

service/ — parsing business logic

repository/ — database access

entity/ — JPA entities

resources/templates/ — Thymeleaf HTML templates

resources/application.properties — Spring Boot configuration

