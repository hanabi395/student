
package com.example.library;

import jakarta.servlet.http.HttpSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class LibraryApplication {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${llm.enabled:true}")
    private boolean llmEnabled;

    @Value("${llm.api-key:sk-41867f76f1bc447cad1f950c026cb43e}")
    private String llmApiKey;

    @Value("${llm.base-url:https://api.deepseek.com/chat/completions}")
    private String llmBaseUrl;

    @Value("${llm.model:deepseek-v4-pro}")
    private String llmModel;

    private String lastLlmError = "暂未调用真实大模型。";

    public LibraryApplication(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }


    @EventListener(ApplicationReadyEvent.class)
    public void openBrowserAfterStart() {
        String url = "http://localhost:8080/";
        System.out.println("\nBook Management System started: " + url + "\n");
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            System.out.println("Browser was not opened automatically. Please open: " + url);
        }
    }

    private final RowMapper<Map<String, Object>> mapRow = (rs, rowNum) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            m.put(rs.getMetaData().getColumnLabel(i).toUpperCase(Locale.ROOT), rs.getObject(i));
        }
        return m;
    };

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        try {
            Map<String, Object> user = jdbc.queryForObject(
                    "SELECT id, username, display_name, role, password_hash FROM users WHERE username=?",
                    mapRow, username);
            if (!Objects.equals(user.get("PASSWORD_HASH"), sha256(password))) {
                return bad("Invalid username or password");
            }
            user.remove("PASSWORD_HASH");
            session.setAttribute("user", user);
            return ResponseEntity.ok(user);
        } catch (EmptyResultDataAccessException e) {
            return bad("Invalid username or password");
        }
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        String displayName = body.getOrDefault("displayName", username).trim();
        if (username.length() < 3 || password.length() < 6) return bad("Username must be at least 3 chars and password at least 6 chars");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username=?", Integer.class, username);
        if (exists != null && exists > 0) return bad("Username already exists");
        jdbc.update("INSERT INTO users(username,password_hash,display_name,role,created_at) VALUES(?,?,?,?,?)",
                username, sha256(password), displayName.isBlank() ? username : displayName, "READER", LocalDateTime.now());
        return login(Map.of("username", username, "password", password), session);
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return ok("Done");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Map<String, Object> user = currentUser(session);
        return user == null ? bad("Not logged in") : ResponseEntity.ok(user);
    }

    @GetMapping("/books")
    public List<Map<String, Object>> books(@RequestParam(defaultValue = "") String keyword,
                                           @RequestParam(required = false) Long categoryId) {
        String sql = "SELECT b.id,b.title,b.author,b.isbn,b.publisher,b.description,b.total_stock,b.available_stock," +
                " c.id category_id,c.name category_name FROM books b JOIN categories c ON b.category_id=c.id WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (!keyword.isBlank()) {
            sql += " AND (LOWER(b.title) LIKE ? OR LOWER(b.author) LIKE ? OR LOWER(b.isbn) LIKE ?)";
            String like = "%" + keyword.toLowerCase() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (categoryId != null) {
            sql += " AND b.category_id=?";
            args.add(categoryId);
        }
        sql += " ORDER BY b.id DESC";
        return jdbc.query(sql, mapRow, args.toArray());
    }

    @GetMapping("/books/{id}")
    public ResponseEntity<?> book(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(jdbc.queryForObject(
                    "SELECT b.*,c.name category_name FROM books b JOIN categories c ON b.category_id=c.id WHERE b.id=?",
                    mapRow, id));
        } catch (EmptyResultDataAccessException e) {
            return bad("Book not found");
        }
    }

    @PostMapping("/books")
    public ResponseEntity<?> saveBook(@RequestBody Map<String, Object> body, HttpSession session) {
        if (!admin(session)) return bad("Admin permission required");
        Long id = longValue(body.get("id"));
        String title = text(body.get("title"));
        String author = text(body.get("author"));
        String isbn = text(body.get("isbn"));
        String publisher = text(body.get("publisher"));
        String description = text(body.get("description"));
        Long categoryId = longValue(body.get("categoryId"));
        int total = intValue(body.get("totalStock"));
        int available = Math.min(total, intValue(body.getOrDefault("availableStock", total)));
        if (title.isBlank() || author.isBlank() || isbn.isBlank() || categoryId == null) return bad("Title, author, ISBN and category are required");
        if (id == null || id == 0) {
            jdbc.update("INSERT INTO books(title,author,isbn,publisher,description,total_stock,available_stock,category_id,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    title, author, isbn, publisher, description, total, available, categoryId, LocalDateTime.now());
            return ResponseEntity.ok(ok("Done"));
        }
        jdbc.update("UPDATE books SET title=?,author=?,isbn=?,publisher=?,description=?,total_stock=?,available_stock=?,category_id=? WHERE id=?",
                title, author, isbn, publisher, description, total, available, categoryId, id);
        return ResponseEntity.ok(ok("Done"));
    }

    @DeleteMapping("/books/{id}")
    public ResponseEntity<?> deleteBook(@PathVariable Long id, HttpSession session) {
        if (!admin(session)) return bad("Admin permission required");
        Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM borrow_records WHERE book_id=?", Integer.class, id);
        if (used != null && used > 0) return bad("This book has borrow records and cannot be deleted. Set stock to 0 instead.");
        jdbc.update("DELETE FROM books WHERE id=?", id);
        return ResponseEntity.ok(ok("Done"));
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return jdbc.query("SELECT * FROM categories ORDER BY id", mapRow);
    }

    @PostMapping("/categories")
    public ResponseEntity<?> addCategory(@RequestBody Map<String, String> body, HttpSession session) {
        if (!admin(session)) return bad("Admin permission required");
        String name = body.getOrDefault("name", "").trim();
        String description = body.getOrDefault("description", "").trim();
        if (name.isBlank()) return bad("Category name is required");
        jdbc.update("INSERT INTO categories(name,description) VALUES(?,?)", name, description);
        return ResponseEntity.ok(ok("Done"));
    }

    @PostMapping("/borrow/{bookId}")
    @Transactional
    public ResponseEntity<?> borrow(@PathVariable Long bookId, HttpSession session) {
        Map<String, Object> user = currentUser(session);
        if (user == null) return bad("Please log in first");
        Map<String, Object> book = jdbc.queryForObject("SELECT id,title,available_stock FROM books WHERE id=?", mapRow, bookId);
        int available = ((Number) book.get("AVAILABLE_STOCK")).intValue();
        if (available <= 0) return bad("No available stock");
        jdbc.update("UPDATE books SET available_stock=available_stock-1 WHERE id=?", bookId);
        jdbc.update("INSERT INTO borrow_records(user_id,book_id,borrowed_at,due_at,status) VALUES(?,?,?,?,?)",
                ((Number) user.get("ID")).longValue(), bookId, LocalDateTime.now(), LocalDateTime.now().plusDays(30), "BORROWED");
        return ResponseEntity.ok(ok("Borrowed successfully. Please return it within 30 days."));
    }

    @PostMapping("/return/{recordId}")
    @Transactional
    public ResponseEntity<?> returnBook(@PathVariable Long recordId, HttpSession session) {
        Map<String, Object> user = currentUser(session);
        if (user == null) return bad("Please log in first");
        Map<String, Object> record = jdbc.queryForObject("SELECT * FROM borrow_records WHERE id=?", mapRow, recordId);
        if (!Objects.equals(((Number) record.get("USER_ID")).longValue(), ((Number) user.get("ID")).longValue())) return bad("Cannot return another user record");
        if (Objects.equals(record.get("STATUS"), "RETURNED")) return ResponseEntity.ok(ok("This record has already been returned"));
        jdbc.update("UPDATE borrow_records SET status='RETURNED', returned_at=? WHERE id=?", LocalDateTime.now(), recordId);
        jdbc.update("UPDATE books SET available_stock=available_stock+1 WHERE id=?", record.get("BOOK_ID"));
        return ResponseEntity.ok(ok("Returned successfully and stock has been restored"));
    }

    @GetMapping("/records")
    public ResponseEntity<?> records(HttpSession session) {
        Map<String, Object> user = currentUser(session);
        if (user == null) return bad("Please log in first");
        boolean isAdmin = Objects.equals(user.get("ROLE"), "ADMIN");
        String sql = "SELECT r.id,u.display_name,b.title,r.borrowed_at,r.due_at,r.returned_at,r.status FROM borrow_records r " +
                "JOIN users u ON r.user_id=u.id JOIN books b ON r.book_id=b.id" + (isAdmin ? "" : " WHERE r.user_id=?") + " ORDER BY r.id DESC";
        Object[] args = isAdmin ? new Object[]{} : new Object[]{user.get("ID")};
        return ResponseEntity.ok(jdbc.query(sql, mapRow, args));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(HttpSession session) {
        if (!admin(session)) return bad("Admin permission required");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookCount", jdbc.queryForObject("SELECT COUNT(*) FROM books", Integer.class));
        data.put("readerCount", jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='READER'", Integer.class));
        data.put("borrowedCount", jdbc.queryForObject("SELECT COUNT(*) FROM borrow_records WHERE status='BORROWED'", Integer.class));
        data.put("availableKinds", jdbc.queryForObject("SELECT COUNT(*) FROM books WHERE available_stock>0", Integer.class));
        data.put("lowStock", jdbc.query("SELECT title,available_stock,total_stock FROM books ORDER BY available_stock ASC LIMIT 5", mapRow));
        return ResponseEntity.ok(data);
    }

    @GetMapping("/ai/recommend")
    public ResponseEntity<?> recommend(HttpSession session) {
        Map<String, Object> user = currentUser(session);
        if (user == null) return bad("Please log in first");
        List<Map<String, Object>> categories = jdbc.query("SELECT b.category_id,COUNT(*) cnt FROM borrow_records r JOIN books b ON r.book_id=b.id WHERE r.user_id=? GROUP BY b.category_id ORDER BY cnt DESC LIMIT 1", mapRow, user.get("ID"));
        if (categories.isEmpty()) {
            return ResponseEntity.ok(jdbc.query("SELECT b.id,b.title,b.author,c.name category_name FROM books b JOIN categories c ON b.category_id=c.id WHERE b.available_stock>0 ORDER BY b.id DESC LIMIT 6", mapRow));
        }
        return ResponseEntity.ok(jdbc.query("SELECT b.id,b.title,b.author,c.name category_name FROM books b JOIN categories c ON b.category_id=c.id WHERE b.available_stock>0 AND b.category_id=? ORDER BY b.id DESC LIMIT 6", mapRow, categories.get(0).get("CATEGORY_ID")));
    }

    @PostMapping("/ai/ask")
    public Map<String, Object> ask(@RequestBody Map<String, String> body) {
        String q = body.getOrDefault("question", "").trim();
        if (q.isBlank()) {
            return Map.of("answer", "Please enter a question. Try: overdue, borrow, return, stock, search, admin, or recommendation. Chinese questions are also supported.");
        }
        Optional<String> llmAnswer = askLargeModel(q);
        if (llmAnswer.isPresent()) {
            return Map.of("answer", llmAnswer.get(), "source", "llm");
        }
        return Map.of("answer", localAiAnswer(q), "source", "local", "reason", lastLlmError);
    }

    @GetMapping("/ai/status")
    public Map<String, Object> aiStatus() {
        return Map.of(
                "enabled", llmEnabled,
                "hasApiKey", llmApiKey != null && !llmApiKey.isBlank(),
                "baseUrl", llmBaseUrl,
                "model", llmModel,
                "lastError", lastLlmError
        );
    }

    private Optional<String> askLargeModel(String question) {
        if (!llmEnabled || llmApiKey == null || llmApiKey.isBlank()) {
            lastLlmError = !llmEnabled
                    ? "真实大模型未启用：请在运行项中设置 LLM_ENABLED=true。"
                    : "真实大模型密钥为空：请在运行项中设置 LLM_API_KEY。";
            return Optional.empty();
        }
        try {
            lastLlmError = "正在连接真实大模型...";
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", llmModel);
            requestBody.put("temperature", 0.3);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", libraryAssistantPrompt()),
                    Map.of("role", "user", "content", question)
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(llmBaseUrl))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + llmApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                lastLlmError = "真实大模型请求失败：HTTP " + response.statusCode() + "，返回内容：" + response.body();
                System.out.println(lastLlmError);
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            String answer = root.path("choices").path(0).path("message").path("content").asText("").trim();
            lastLlmError = answer.isBlank() ? "真实大模型返回了空回答。" : "真实大模型请求成功。";
            return answer.isBlank() ? Optional.empty() : Optional.of(answer);
        } catch (Exception e) {
            lastLlmError = "真实大模型连接失败，已自动切换为本地后备方案。详细原因：" + e.getMessage();
            System.out.println(lastLlmError);
            return Optional.empty();
        }
    }

    private String libraryAssistantPrompt() {
        String books = jdbc.query(
                "SELECT b.title,c.name category_name,b.available_stock FROM books b JOIN categories c ON b.category_id=c.id ORDER BY b.id LIMIT 12",
                (rs, rowNum) -> "- " + rs.getString("title") + " [" + rs.getString("category_name") + "], available stock: " + rs.getInt("available_stock")
        ).stream().reduce("", (left, right) -> left + right + "\n");
        return """
                You are the AI assistant of a Book Management System.
                Answer in the same language as the user. If the user asks in Chinese, answer in Chinese.
                Keep answers concise and useful for a course demo.

                System rules:
                - Reader account: reader / reader123
                - Admin account: admin / admin123
                - Borrow period: 30 days
                - Borrowing creates a borrow record and decreases available stock by 1
                - Returning changes the record to RETURNED and restores stock by 1
                - Admin can manage categories, books and stock
                - Recommendation uses reader borrow history and available books

                Current sample books:
                """ + books;
    }

    private String localAiAnswer(String q) {
        String s = q.toLowerCase(Locale.ROOT);
        String answer;
        if (hasAny(s, "\u903e\u671f", "\u8d85\u671f", "\u8fc7\u671f", "late", "overdue")) {
            answer = "Borrow period is 30 days. If a book is overdue, return it as soon as possible in Borrow Records and contact the admin. This can be extended with fine or restriction rules later.";
        } else if (hasAny(s, "\u600e\u4e48\u501f", "\u501f\u4e66", "\u501f\u9605", "borrow")) {
            answer = "Borrow flow: log in as a reader, open Books, search or browse a book, make sure available stock is greater than 0, then click Borrow. The system creates a borrow record and decreases stock by 1.";
        } else if (hasAny(s, "\u600e\u4e48\u8fd8", "\u5f52\u8fd8", "\u8fd8\u4e66", "return")) {
            answer = "Return flow: open Borrow Records, find a BORROWED record, and click Return. The record becomes RETURNED and stock is restored automatically.";
        } else if (hasAny(s, "\u63a8\u8350", "recommend", "\u9002\u5408", "\u770b\u4ec0\u4e48", "\u8bfb\u4ec0\u4e48")) {
            answer = "Recommendation logic: the system checks your borrow history and recommends available books from your favorite category. If you have no history, it recommends available books.";
        } else if (hasAny(s, "\u5e93\u5b58", "\u6ca1\u4e66", "\u65e0\u5e93\u5b58", "stock", "available")) {
            answer = "Stock is shown as Available/Total. Borrowing decreases Available, returning restores Available. Admin can edit stock on the Admin page.";
        } else if (hasAny(s, "\u641c\u7d22", "\u67e5\u627e", "\u627e\u4e66", "search", "find")) {
            answer = "Search supports title, author and ISBN. Enter a keyword on the Books page and click Search.";
        } else if (hasAny(s, "\u767b\u5f55", "\u6ce8\u518c", "\u8d26\u53f7", "\u5bc6\u7801", "login", "register", "account")) {
            answer = "Default accounts: reader/reader123 for reader features, admin/admin123 for admin features. New readers can register on the login page.";
        } else if (hasAny(s, "\u7ba1\u7406\u5458", "\u540e\u53f0", "\u5206\u7c7b", "\u65b0\u589e", "\u5220\u9664", "admin", "category")) {
            answer = "Admin features include category creation, book maintenance, stock editing and dashboard statistics. Log in with admin/admin123 and open the Admin page.";
        } else if (hasAny(s, "ai", "\u7b80\u4ecb", "\u751f\u6210", "description", "summary")) {
            answer = "The AI module is a local rule-based simulation. It supports Q&A, recommendations and book description generation, so it works without external API keys or network access.";
        } else if (hasAny(s, "\u4f60\u597d", "hello", "hi")) {
            answer = "Hello, I am the library AI assistant. You can ask: how to borrow, how to return, what if overdue, how to search books, how recommendation works, or what admins can do.";
        } else {
            answer = "I did not fully understand that question. Try asking: how to borrow? what if overdue? how to search Java books? how does recommendation work? how can admin add books?";
        }
        return answer;
    }

    private boolean hasAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @PostMapping("/ai/description")
    public Map<String, Object> description(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "Untitled Book");
        String author = body.getOrDefault("author", "Unknown Author");
        String category = body.getOrDefault("category", "General");
        return Map.of("description", title + " by " + author + " is a " + category + " book. It is suitable for course reading, interest exploration and topic-based learning.");
    }

    private Map<String, Object> currentUser(HttpSession session) {
        Object value = session.getAttribute("user");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> user = new LinkedHashMap<>();
            map.forEach((k, v) -> user.put(String.valueOf(k), v));
            return user;
        }
        return null;
    }

    private boolean admin(HttpSession session) {
        Map<String, Object> user = currentUser(session);
        return user != null && Objects.equals(user.get("ROLE"), "ADMIN");
    }

    private ResponseEntity<Map<String, Object>> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private Map<String, Object> ok(String message) {
        return Map.of("message", message);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private int intValue(Object value) { return value == null || String.valueOf(value).isBlank() ? 0 : Integer.parseInt(String.valueOf(value)); }
    private Long longValue(Object value) { return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value)); }
}
