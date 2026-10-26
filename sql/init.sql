-- ============================================================
-- 康轨 KangRail · 高并发铁路购票系统 初始化脚本
-- 依赖：MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS railway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE railway;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密密码',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1正常',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT = '用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码 ADMIN/USER',
    role_name VARCHAR(64) NOT NULL,
    status    TINYINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB COMMENT = '角色表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE = InnoDB COMMENT = '用户角色关联表';

-- 影院表
CREATE TABLE IF NOT EXISTS cinema (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    cinema_code VARCHAR(16) NOT NULL COMMENT '影院电报码/站码',
    cinema_name VARCHAR(64) NOT NULL,
    city         VARCHAR(64) DEFAULT NULL,
    status       TINYINT     NOT NULL DEFAULT 1,
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cinema_code (cinema_code)
) ENGINE = InnoDB COMMENT = '影院表';

-- 场次表
CREATE TABLE IF NOT EXISTS screening (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    screening_no         VARCHAR(32) NOT NULL COMMENT '场次号，如 S1001',
    movie_name       VARCHAR(64) DEFAULT NULL,
    cinema_id BIGINT      NOT NULL,
    hall_id   BIGINT      NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    status           TINYINT     NOT NULL DEFAULT 1 COMMENT '0停运 1运行',
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_screening_no (screening_no)
) ENGINE = InnoDB COMMENT = '场次表';

-- 场次影厅表
CREATE TABLE IF NOT EXISTS hall (
    id           BIGINT  NOT NULL AUTO_INCREMENT,
    screening_id     BIGINT  NOT NULL,
    cinema_id   BIGINT  NOT NULL,
    hall_no   INT     NOT NULL COMMENT '影厅编号',
    open_time  TIME    DEFAULT NULL,
    close_time   TIME    DEFAULT NULL,
    cleanup_minutes INT     DEFAULT 0,
    row_count  INT     DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hall (screening_id, hall_no),
    KEY idx_cinema_id (cinema_id)
) ENGINE = InnoDB COMMENT = '场次影厅表';

-- 座位库存表（热点表：查询走 Redis 缓存 + Redisson 分布式锁）
CREATE TABLE IF NOT EXISTS screening_seat (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    screening_id    BIGINT      NOT NULL,
    show_date DATE        NOT NULL COMMENT '放映日期',
    ticket_type   VARCHAR(32) NOT NULL COMMENT '票种：普通票/学生票/IMAX厅票',
    total       INT         NOT NULL DEFAULT 0 COMMENT '总票数',
    remain      INT         NOT NULL DEFAULT 0 COMMENT '余票',
    price       BIGINT      NOT NULL DEFAULT 0 COMMENT '票价（分）',
    version     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    status      TINYINT     NOT NULL DEFAULT 1,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_screening_date_type (screening_id, show_date, ticket_type)
) ENGINE = InnoDB COMMENT = '座位库存表';

-- 订单表
CREATE TABLE IF NOT EXISTS movie_order (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    order_no         VARCHAR(32) NOT NULL COMMENT '订单号',
    user_id          BIGINT      NOT NULL,
    screening_id         BIGINT      NOT NULL,
    show_date      DATE        NOT NULL,
    cinema_id BIGINT      NOT NULL,
    hall_id   BIGINT      NOT NULL,
    ticket_type        VARCHAR(32) NOT NULL,
    ticket_count     INT         NOT NULL DEFAULT 1,
    total_amount     BIGINT      NOT NULL COMMENT '总金额（分）',
    status           TINYINT     NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3已出票 4已退票',
    pay_expire_time  DATETIME    DEFAULT NULL COMMENT '支付截止时间',
    pay_time         DATETIME    DEFAULT NULL,
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_train_date (screening_id, show_date)
) ENGINE = InnoDB COMMENT = '订单表';

-- 订单明细表（乘客）
CREATE TABLE IF NOT EXISTS movie_order_item (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    order_id       BIGINT      NOT NULL,
    passenger_name VARCHAR(64) NOT NULL,
    id_card_no     VARCHAR(32) NOT NULL,
    seat_no        VARCHAR(16) DEFAULT NULL COMMENT '出票后回填座位号',
    status         TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB COMMENT = '订单明细表';

-- ============================================================
-- 初始数据
-- ============================================================

INSERT INTO sys_role (role_code, role_name) VALUES ('ADMIN', '管理员'), ('USER', '普通用户');

INSERT INTO cinema (cinema_code, cinema_name, city) VALUES
('GZ001', '万达影城·天河店', '广州'),
('GZ002', '飞扬影城·正佳店', '广州'),
('SZ001', '百老汇影城·万象城店', '深圳'),
('BJ001', '万达影城·CBD店', '北京'),
('SH001', '百丽宫影城·环贸店', '上海');

INSERT INTO screening (screening_no, movie_name, cinema_id, hall_id, start_time, end_time) VALUES
('S1001', '流浪地球3', 1, 1, '10:00:00', '12:15:00'),
('S1002', '满江红2', 2, 3, '13:30:00', '15:40:00');

INSERT INTO hall (screening_id, cinema_id, hall_no, open_time, close_time, cleanup_minutes, row_count) VALUES
(1, 1, 1, '10:00:00', '12:15:00', 15, 12),
(1, 1, 2, '12:30:00', '14:45:00', 15, 10),
(1, 2, 3, '10:20:00', '12:35:00', 15, 14),
(1, 2, 4, '13:00:00', '15:15:00', 15, 10);

INSERT INTO screening_seat (screening_id, show_date, ticket_type, total, remain, price) VALUES
(1, '2026-11-01', '普通票', 200, 200, 4500),
(1, '2026-11-01', '学生票', 100, 100, 2500),
(1, '2026-11-01', 'IMAX厅票', 50, 50, 8800);

-- ============================================================
-- 演示账号（与前端登录页提示的账号一致）
--   demo  / 123456     普通用户
--   admin / admin123   管理员
--
-- 密码字段存的是 Hutool BCrypt 密文，与 AuthServiceImpl 里的
-- BCrypt.checkpw(明文, 密文) 兼容。
-- 用 INSERT IGNORE，重复导入不会因唯一索引报错。
-- ============================================================
INSERT IGNORE INTO sys_user (username, password, nickname, status) VALUES
('demo',  '$2a$10$MQgsti2JMFxnKWqkus.NF.Mg3W9hzWs5uMoMpe7OFy8HX.B9iY4Ue', '演示账号',   1),
('admin', '$2a$10$BHbYymQ0nZgDocr9V9UxiOFCcAt9dUNuzkE6JIX.OIKKpVpyBHjOy', '系统管理员', 1);

-- 绑定角色：按 role_code 关联，不写死 id（避免自增顺序变化导致绑错）
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r
WHERE (u.username = 'demo'  AND r.role_code = 'USER')
   OR (u.username = 'admin' AND r.role_code = 'ADMIN');
