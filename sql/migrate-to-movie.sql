-- ============================================================
-- 铁路售票系统 → 电影票系统：表结构迁移（保留数据）
--
-- 前置：先备份！
--   mysqldump -uroot -p123456 railway > C:\Users\Public\railway-backup.sql
--
-- 说明：本次改造"只改名、不改逻辑"，所以列的类型/顺序/约束都不变，
--       只重命名表名和列名。数据一条不丢。
-- ============================================================

USE railway;   -- 历史脚本：迁移时旧库还叫 railway；现已整体更名 movie，此脚本仅存档

-- ---------- 1. 表改名 ----------
RENAME TABLE station          TO cinema,
             train            TO screening,
             train_station    TO hall,
             train_seat       TO screening_seat,
             train_order      TO movie_order,
             train_order_item TO movie_order_item;

-- ---------- 2. 列改名 ----------
-- 车站 → 影院
ALTER TABLE cinema
    CHANGE station_code station_code_old VARCHAR(16) NOT NULL,
    CHANGE station_name station_name_old VARCHAR(64) NOT NULL;
ALTER TABLE cinema
    CHANGE station_code_old cinema_code VARCHAR(16) NOT NULL COMMENT '影院编码',
    CHANGE station_name_old cinema_name VARCHAR(64) NOT NULL COMMENT '影院名称';

-- 车次 → 场次
ALTER TABLE screening
    CHANGE train_no         screening_no VARCHAR(32) NOT NULL COMMENT '场次号，如 S1001',
    CHANGE train_name       movie_name   VARCHAR(64) DEFAULT NULL COMMENT '影片名称',
    CHANGE start_station_id cinema_id    BIGINT NOT NULL COMMENT '影院 ID',
    CHANGE end_station_id   hall_id      BIGINT NOT NULL COMMENT '影厅编号';

-- 经停站 → 影厅
ALTER TABLE hall
    CHANGE train_id        screening_id    BIGINT NOT NULL COMMENT '场次 ID',
    CHANGE station_id      cinema_id       BIGINT NOT NULL COMMENT '影院 ID',
    CHANGE station_no      hall_no         INT    NOT NULL COMMENT '影厅编号',
    CHANGE arrive_time     open_time       TIME   DEFAULT NULL COMMENT '开场时间',
    CHANGE leave_time      close_time      TIME   DEFAULT NULL COMMENT '散场时间',
    CHANGE stop_minutes    cleanup_minutes INT    DEFAULT 0 COMMENT '散场清洁分钟',
    CHANGE distance_km     row_count       INT    DEFAULT 0 COMMENT '影厅排数';

-- 库存
ALTER TABLE screening_seat
    CHANGE train_id    screening_id BIGINT NOT NULL,
    CHANGE travel_date show_date    DATE   NOT NULL COMMENT '放映日期',
    CHANGE seat_type   ticket_type  VARCHAR(32) NOT NULL COMMENT '票种';

-- 订单
ALTER TABLE movie_order
    CHANGE train_id         screening_id BIGINT NOT NULL,
    CHANGE travel_date      show_date    DATE   NOT NULL,
    CHANGE start_station_id cinema_id    BIGINT NOT NULL,
    CHANGE end_station_id   hall_id      BIGINT NOT NULL,
    CHANGE seat_type        ticket_type  VARCHAR(32) NOT NULL;

-- ---------- 3. 索引改名（可选） ----------
-- 旧索引名如果不存在会报错，可整段跳过，不影响功能。
-- ALTER TABLE hall           RENAME INDEX uk_train_station    TO uk_hall;
-- ALTER TABLE screening_seat RENAME INDEX uk_train_date_seat  TO uk_screening_date_type;
-- ALTER TABLE movie_order    RENAME INDEX idx_train_date      TO idx_screening_date;

-- ---------- 4. 验证 ----------
SELECT '=== 迁移后表清单 ===' AS info;
SHOW TABLES;

SELECT '=== 数据量校验（应与迁移前一致）===' AS info;
SELECT 'cinema' AS t, COUNT(*) AS cnt FROM cinema
UNION ALL SELECT 'screening', COUNT(*) FROM screening
UNION ALL SELECT 'hall', COUNT(*) FROM hall
UNION ALL SELECT 'screening_seat', COUNT(*) FROM screening_seat
UNION ALL SELECT 'movie_order', COUNT(*) FROM movie_order
UNION ALL SELECT 'movie_order_item', COUNT(*) FROM movie_order_item;
