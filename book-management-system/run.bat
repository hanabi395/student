@echo off
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven was not found. Please install Maven or open this folder as a Maven project in IntelliJ IDEA.
  echo Main class: src/main/java/com/example/library/LibraryApplication.java
  pause
  exit /b 1
)
mvn spring-boot:run
