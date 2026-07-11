INSERT INTO users (id, username, password_hash, display_name, role, created_at) VALUES
(1, 'admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '系统管理员', 'ADMIN', CURRENT_TIMESTAMP),
(2, 'reader', '128a1cb71e153e042708de7ea043d9a030fc1a83fa258788e7ef7aa23309eb72', '默认读者', 'READER', CURRENT_TIMESTAMP);

INSERT INTO categories (id, name, description) VALUES
(1, '计算机', '程序设计、人工智能、数据库和网络技术'),
(2, '文学', '小说、散文、诗歌与文学评论'),
(3, '管理', '管理学、经济学和创新创业'),
(4, '科普', '自然科学、工程技术与通识读物');

INSERT INTO books (id, title, author, isbn, publisher, description, total_stock, available_stock, category_id, created_at) VALUES
(1, 'Java 核心技术', 'Cay S. Horstmann', '9787111636663', '机械工业出版社', '系统讲解 Java 语言、集合、并发和应用开发，适合程序设计课程学习。', 5, 5, 1, CURRENT_TIMESTAMP),
(2, '深入理解计算机系统', 'Randal E. Bryant', '9787111544937', '机械工业出版社', '从程序员视角介绍计算机系统、内存、链接和并发等核心知识。', 3, 3, 1, CURRENT_TIMESTAMP),
(3, '平凡的世界', '路遥', '9787530212004', '北京十月文艺出版社', '描写普通人在时代变化中的奋斗、选择与成长。', 4, 4, 2, CURRENT_TIMESTAMP),
(4, '管理学', 'Stephen P. Robbins', '9787300286575', '中国人民大学出版社', '介绍计划、组织、领导和控制等管理学基础内容。', 2, 2, 3, CURRENT_TIMESTAMP),
(5, '时间简史', 'Stephen Hawking', '9787535732309', '湖南科学技术出版社', '介绍宇宙学、黑洞和时间等经典科普主题。', 3, 3, 4, CURRENT_TIMESTAMP);

ALTER TABLE users ALTER COLUMN id RESTART WITH 3;
ALTER TABLE categories ALTER COLUMN id RESTART WITH 5;
ALTER TABLE books ALTER COLUMN id RESTART WITH 6;
ALTER TABLE borrow_records ALTER COLUMN id RESTART WITH 1;
