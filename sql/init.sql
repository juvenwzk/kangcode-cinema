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

-- 车站表
CREATE TABLE IF NOT EXISTS station (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    station_code VARCHAR(16) NOT NULL COMMENT '车站电报码/站码',
    station_name VARCHAR(64) NOT NULL,
    city         VARCHAR(64) DEFAULT NULL,
    status       TINYINT     NOT NULL DEFAULT 1,
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_station_code (station_code)
) ENGINE = InnoDB COMMENT = '车站表';

-- 车次表
CREATE TABLE IF NOT EXISTS train (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    train_no         VARCHAR(32) NOT NULL COMMENT '车次号，如 G1001',
    train_name       VARCHAR(64) DEFAULT NULL,
    start_station_id BIGINT      NOT NULL,
    end_station_id   BIGINT      NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    status           TINYINT     NOT NULL DEFAULT 1 COMMENT '0停运 1运行',
    create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_no (train_no)
) ENGINE = InnoDB COMMENT = '车次表';

-- 车次经停站表
CREATE TABLE IF NOT EXISTS train_station (
    id           BIGINT  NOT NULL AUTO_INCREMENT,
    train_id     BIGINT  NOT NULL,
    station_id   BIGINT  NOT NULL,
    station_no   INT     NOT NULL COMMENT '到站顺序，1 为始发',
    arrive_time  TIME    DEFAULT NULL,
    leave_time   TIME    DEFAULT NULL,
    stop_minutes INT     DEFAULT 0,
    distance_km  INT     DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_station (train_id, station_no),
    KEY idx_station_id (station_id)
) ENGINE = InnoDB COMMENT = '车次经停站表';

-- 座位库存表（热点表：查询走 Redis 缓存 + Redisson 分布式锁）
CREATE TABLE IF NOT EXISTS train_seat (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    train_id    BIGINT      NOT NULL,
    travel_date DATE        NOT NULL COMMENT '乘车日期',
    seat_type   VARCHAR(32) NOT NULL COMMENT '席别：二等座/一等座/商务座/硬卧/软卧',
    total       INT         NOT NULL DEFAULT 0 COMMENT '总票数',
    remain      INT         NOT NULL DEFAULT 0 COMMENT '余票',
    price       BIGINT      NOT NULL DEFAULT 0 COMMENT '票价（分）',
    version     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    status      TINYINT     NOT NULL DEFAULT 1,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_date_seat (train_id, travel_date, seat_type)
) ENGINE = InnoDB COMMENT = '座位库存表';

-- 订单表
CREATE TABLE IF NOT EXISTS train_order (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    order_no         VARCHAR(32) NOT NULL COMMENT '订单号',
    user_id          BIGINT      NOT NULL,
    train_id         BIGINT      NOT NULL,
    travel_date      DATE        NOT NULL,
    start_station_id BIGINT      NOT NULL,
    end_station_id   BIGINT      NOT NULL,
    seat_type        VARCHAR(32) NOT NULL,
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
    KEY idx_train_date (train_id, travel_date)
) ENGINE = InnoDB COMMENT = '订单表';

-- 订单明细表（乘客）
CREATE TABLE IF NOT EXISTS train_order_item (
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

INSERT INTO station (station_code, station_name, city) VALUES
('BJP', '北京', '北京'),
('JNK', '济南', '济南'),
('NJH', '南京', '南京'),
('SHH', '上海', '上海'),
('GZQ', '广州', '广州');

INSERT INTO train (train_no, train_name, start_station_id, end_station_id, start_time, end_time) VALUES
('G1001', '北京-上海高铁', 1, 4, '08:00:00', '13:30:00'),
('G1002', '广州-北京高铁', 5, 1, '09:00:00', '16:00:00');

INSERT INTO train_station (train_id, station_id, station_no, arrive_time, leave_time, stop_minutes, distance_km) VALUES
(1, 1, 1, '08:00:00', '08:00:00', 0, 0),
(1, 2, 2, '09:10:00', '09:12:00', 2, 419),
(1, 3, 3, '11:40:00', '11:42:00', 2, 1023),
(1, 4, 4, '13:30:00', '13:30:00', 0, 1318);

INSERT INTO train_seat (train_id, travel_date, seat_type, total, remain, price) VALUES
(1, '2026-09-01', '二等座', 500, 500, 55300),
(1, '2026-09-01', '一等座', 100, 100, 93300),
(1, '2026-09-01', '商务座', 20, 20, 174800);

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
