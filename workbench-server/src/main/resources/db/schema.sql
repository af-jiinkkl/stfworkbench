-- ============================================================
-- stfworkbench 建表脚本
-- 依据：docs/数据模型.md §5（v0.3 已冻结）
-- 引擎 InnoDB / 字符集 utf8mb4 / 排序 utf8mb4_general_ci（见 CLAUDE.md 数据库规范）
--
-- 本文件不会自动执行（application.yml 里没开 spring.sql.init），
-- 需要手动运行。表会随各功能分支逐步补齐：
--   feature/auth-login  ：wb_user
--   feature/plan-task   ：wb_plan_task
-- ============================================================

CREATE DATABASE IF NOT EXISTS `stfworkbench`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `stfworkbench`;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
-- 注意：本表不含 user_id —— 它本身就是用户表。
-- 数据隔离拦截器（MybatisPlusConfig）必须把本表排除，
-- 否则查用户时会被拼上 WHERE user_id = ? 而报表不存在。
CREATE TABLE `wb_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(50)  NOT NULL                COMMENT '登录名',
  `password`    VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 哈希）',
  `nickname`    VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '昵称',
  `avatar`      VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '头像地址',
  `qq_openid`   VARCHAR(64)  NULL                    COMMENT 'QQ 登录标识（预留）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIGINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
  PRIMARY KEY (`id`),
  -- deleted 用时间戳而非 0/1，是为了让本唯一键成立：
  -- 未删除的行 deleted 恒为 0，故 (username, 0) 唯一 —— 同时只允许一个同名活跃用户；
  -- 删除后 deleted 变成时间戳，不再占用那个用户名，可以重新注册。
  UNIQUE KEY `uk_username` (`username`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 每日计划任务
-- ------------------------------------------------------------
-- 本表**含 user_id**，故数据隔离拦截器会自动为每条 SQL 补 `user_id = ?`，
-- 业务代码里不要手写该条件（见 docs/数据模型.md §2.2）。
CREATE TABLE `wb_plan_task` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`        BIGINT       NOT NULL                COMMENT '所属用户',
  `plan_date`      DATE         NOT NULL                COMMENT '计划日期',
  `content`        VARCHAR(255) NOT NULL                COMMENT '任务内容',
  `completed`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否完成：0 否，1 是',
  `completed_time` DATETIME     NULL                    COMMENT '完成时间',
  `sort_order`     INT          NOT NULL DEFAULT 0      COMMENT '排序值',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        BIGINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
  PRIMARY KEY (`id`),
  -- (user_id, plan_date) 前导列正好覆盖"查某人某天"和"查某人某段日期"两种查询
  KEY `idx_user_date` (`user_id`, `plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='每日计划任务';
