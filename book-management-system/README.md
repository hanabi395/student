# Book Management System

Training topic 1: Library / Book Management System.

## Features

- Reader registration, login and logout
- Book search, create, update and stock management
- Category management
- Borrow and return workflow with borrow records
- Admin dashboard with basic statistics
- RESTful API plus HTML/CSS/JavaScript frontend
- H2 database with schema and seed data
- AI module: rule-based book recommendation, borrowing Q&A and book description generation

## Tech Stack

- Spring Boot 3.3
- Spring MVC REST API
- Spring JDBC
- H2 Database
- HTML + CSS + JavaScript

## Run

Requirements: JDK 17+ and Maven.

```bash
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

Default accounts:

```text
Admin: admin / admin123
Reader: reader / reader123
```

H2 console:

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:librarydb
User: sa
Password: empty
```

## Demo Flow

1. Log in as reader and borrow a book.
2. Open Borrow Records and return the book.
3. Open AI Assistant, view recommendations, and ask: overdue.
4. Log in as admin, add a category and add or edit a book.
5. Open Admin Dashboard and show stock changes and records.

## Notes for Acceptance

This project covers the required business loop: login -> search book -> borrow -> create record -> decrease stock -> return -> update record -> restore stock.

The AI module is implemented with local rules so it can run without network access or model accounts. The `/api/ai/*` endpoints can later be replaced by OpenAI, DeepSeek, Qwen, Dify or Coze APIs.

## Real LLM Mode

The AI assistant supports OpenAI-compatible chat completion APIs. If no API key is configured, it automatically falls back to local rules.

PowerShell example:

```powershell
$env:LLM_ENABLED="true"
$env:LLM_API_KEY="your_api_key_here"
$env:LLM_BASE_URL="https://api.openai.com/v1/chat/completions"
$env:LLM_MODEL="gpt-4o-mini"
mvn spring-boot:run
```

For other compatible providers, change `LLM_BASE_URL` and `LLM_MODEL`.

## IntelliJ IDEA Run Guide

If no browser appears after clicking Run, check these points:

1. Do not run `Current File` or `README.md`.
2. Open `src/main/java/com/example/library/LibraryApplication.java`.
3. Click the green triangle beside `public static void main`.
4. Wait until the console shows `Tomcat started on port 8080`.
5. Open `http://localhost:8080` manually if the browser does not pop up.

A run configuration named `Run Book Management System` is included under `.idea/runConfigurations`.
