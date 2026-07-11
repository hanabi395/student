# Acceptance Guide

## System Overview

This system is a book management web application for a small library, class reading corner or reading room. It supports user login, book management, category management, borrowing, returning, stock tracking, admin statistics and an AI assistant module.

## Roles

- Admin: admin / admin123
- Reader: reader / reader123

## Core Modules

1. User module: registration, login, logout and role control.
2. Book module: search, create, update and stock maintenance.
3. Category module: admin can create categories, books belong to categories.
4. Borrow module: reader borrows and returns books, records are stored, stock is updated.
5. Dashboard module: book count, reader count, active borrow count and available book count.
6. AI module: recommendation, Q&A and book description generation.

## Complete Business Loop

Reader login -> search book -> borrow book -> create borrow record -> stock decreases -> view records -> return book -> record status changes -> stock restores.

## AI Module

The project uses local rules to simulate AI behavior, which is suitable for acceptance demos without external network or API keys.

- Recommendation: recommends books by the reader's most borrowed category, or available books if no history exists.
- Q&A: answers questions about borrowing, returning, overdue handling and recommendations.
- Description generation: admin inputs title, author and category to generate a short book description.

## Suggested Demo Order

1. Start the project and open the home page.
2. Log in as reader, search and borrow a book.
3. Open Borrow Records and return it.
4. Open AI Assistant, view recommendations and ask `overdue`.
5. Log out and log in as admin.
6. Add a category and a book, then show the dashboard.
7. Open H2 console and show users, books, categories and borrow_records tables.
