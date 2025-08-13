# Installation & Run Instructions

## 1. Requirements
Before starting, make sure you have installed:
- **Java 21** (or the version specified in `pom.xml`)
- **Maven 3.9+**
- **Docker** and **Docker Compose**
- **Git** (optional, for cloning the repo)

---

## 2. Clone the Repository
bash
git clone https://github.com/dataOX-Application-for-scraping-jobs.git
cd dataOX-Application-for-scraping-jobs

## 3. Running with Docker 
This will start:
PostgreSQL database
Selenium Standalone Chrome

After launching via Docker, I recommend entering "docker logs -f techstars_app" in the IDE console. And wait for a while, via Docker it works longer than via launching in IDE.


Spring Boot application
bash
docker compose up --build
After all containers are healthy, the application will be available at:

## 4. Running from IDE (Local Development)
Make sure PostgreSQL and Selenium are running.
You can do this via:

bash
docker compose up db selenium
Configure application-local.properties:

properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jobsdb
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=Stasqwerty123
SELENIUM_REMOTE_URL=http://localhost:4444/wd/hub
DUMPS_DIR=./data
In your IDE’s Run/Debug Configuration:

Select Spring Boot configuration.

Set Active Profiles = local.

Run the application from IDE.
Access it at http://localhost:8080.

## 5. Dump Files
Scraped job data will be exported to a .sql file in the directory set via:

properties
DUMPS_DIR=./data
In Docker, this path maps to /data.

6. Stopping the Application
bash
docker compose down

## ⚙️ Installation & Run
See [INSTALL.md](INSTALL.md) for full instructions.

---

## 🔗 Access
- **App**: [http://localhost:8080](http://localhost:8080)
- **Selenium Grid UI**: [http://localhost:4444/ui](http://localhost:4444/ui)
