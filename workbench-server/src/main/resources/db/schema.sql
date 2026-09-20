-- ============================================================
-- stfworkbench 建表脚本
-- 依据：docs/数据模型.md §5（v0.3 已冻结）
-- 引擎 InnoDB / 字符集 utf8mb4 / 排序 utf8mb4_general_ci（见 CLAUDE.md 数据库规范）
--
-- 本文件不会自动执行（application.yml 里没开 spring.sql.init），
-- 需要手动运行。表会随各功能分支逐步补齐：
--   feature/auth-login  ：wb_user
--   feature/plan-task   ：wb_plan_task
--   feature/anniversary ：wb_anniversary
--   feature/memo        ：wb_memo
--   feature/expense     ：wb_expense
--   feature/course      ：wb_semester、wb_course
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
-- 生日与纪念日
-- ------------------------------------------------------------
-- 生日和纪念日**合表**，用 type 区分（见 docs/数据模型.md §4.3）。
-- 只存 month + day 不存完整日期：生日每年重复，年份没有意义。
-- 代价是跨年推算不能在 SQL 里做，取出来在 Java 里算 —— 一个人的
-- 这类记录通常只有几十条，全量取出的代价可以忽略。
CREATE TABLE `wb_anniversary` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL                COMMENT '所属用户',
  `name`        VARCHAR(50)  NOT NULL                COMMENT '姓名或名称',
  `type`        TINYINT      NOT NULL                COMMENT '类型：1 生日，2 纪念日',
  `relation`    VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT '关系：自己/家人/朋友',
  `month`       TINYINT      NOT NULL                COMMENT '月 1-12',
  `day`         TINYINT      NOT NULL                COMMENT '日 1-31（2 月可存 29）',
  `remind_days` TINYINT      NOT NULL DEFAULT 7      COMMENT '提前提醒天数 1-7',
  `remark`      VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIGINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生日与纪念日';

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

-- ------------------------------------------------------------
-- 备忘录
-- ------------------------------------------------------------
-- 本表**含 user_id**，隔离同样由拦截器负责。
-- `content` 用 TEXT 而不是 VARCHAR：备忘录正文天然可能很长，
-- 这里属于 CLAUDE.md "避免 text 滥用" 所允许的合理使用（见 docs/数据模型.md §4.4）。
-- 搜索走 `LIKE '%关键词%'`，用不上索引，但每人几百条的规模下不做过早优化。
CREATE TABLE `wb_memo` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL                COMMENT '所属用户',
  `title`       VARCHAR(100) NOT NULL DEFAULT ''     COMMENT '标题',
  `content`     TEXT         NULL                    COMMENT '正文',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIGINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='备忘录';

-- ------------------------------------------------------------
-- 消费记录
-- ------------------------------------------------------------
-- 本表**含 user_id**，隔离由拦截器负责。
--
-- `amount` 用 DECIMAL(10,2) 而**绝不能用 FLOAT/DOUBLE**：浮点数存钱会丢精度
-- （0.1 + 0.2 != 0.3），这个是经典事故。10,2 表示最多 10 位、其中 2 位小数，
-- 即上限 99,999,999.99 —— 对个人记账足够。
-- 注意 DECIMAL 的"2 位小数"是**四舍五入写入**而不是拒绝：传 0.005 进来会被
-- 静默存成 0.01。所以入参那边用 @Digits 挡在前面，让客户端拿到一个 400
-- 而不是一份自己都没察觉的被改过的数据（见 ExpenseDTO）。
--
-- `category` 第一版是**预置**分类，用 VARCHAR 存分类名而不是外键表。
-- 将来要自定义分类时再抽表，届时是一张新表 + 一次数据迁移，不影响本表结构。
-- 合法取值由后端校验（见 common/ExpenseCategory），非法值返回 400。
--
-- `idx_user_date (user_id, expense_date)` 前导列覆盖"查某人某天"和"查某人某段日期"，
-- 按月趋势（`WHERE user_id = ? AND expense_date BETWEEN ? AND ?` 再
-- `GROUP BY MONTH(expense_date)`）也用得上它做过滤。
-- 只有分组那一步用不上索引的有序性：函数包在列上，MySQL 得先取出来再排一次。
-- 按用户分区后每人的行数很少，这里不做过早优化。
-- 用 MONTH() 而不是 DATE_FORMAT(..., '%Y-%m') 是因为年份已由入参定死（区间只落在一年内），
-- SQL 里没必要再拼一次前缀，顺带让这段 wrapper 不含引号和 % ——
-- 而 MyBatis-Plus 对传进 wrapper 的字符串是做注入检查的。12 个月的名字由 Java 补全。
CREATE TABLE `wb_expense` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT        NOT NULL                COMMENT '所属用户',
  `amount`       DECIMAL(10,2) NOT NULL                COMMENT '金额（元）',
  `category`     VARCHAR(20)   NOT NULL                COMMENT '消费分类（预置值之一）',
  `expense_date` DATE          NOT NULL                COMMENT '消费日期',
  `remark`       VARCHAR(255)  NOT NULL DEFAULT ''     COMMENT '备注',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      BIGINT        NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
  PRIMARY KEY (`id`),
  -- (user_id, expense_date) 前导列正好覆盖"查某人某天"和"查某人某段日期"两种查询
  KEY `idx_user_date` (`user_id`, `expense_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消费记录';

-- ------------------------------------------------------------
-- 学期
-- ------------------------------------------------------------
-- 本表**含 user_id**，隔离由拦截器负责。
--
-- `start_date` 是**第 1 周的周一**，这一点是整个课程表的地基：
-- 课表上的一切（第 N 周星期几）都要靠它换算成真实日期，
-- 基准偏一天，整张表就整体偏一天，而显示出来仍然是一张"看着对"的课表。
--
-- 正因为错得安静，这个约定**不能只写在文档里**：接口在写入时校验
-- （不是周一就返回 400，见 SemesterServiceImpl），否则前端随便选个日期
-- 也能存进去，之后没有任何一步会报错。
--
-- `total_weeks` 是"这门课最多能排到第几周"的上界，课程那边要用它对
-- start_week / end_week 做范围校验。
CREATE TABLE `wb_semester` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT      NOT NULL                COMMENT '所属用户',
  `name`        VARCHAR(50) NOT NULL                COMMENT '学期名称',
  `start_date`  DATE        NOT NULL                COMMENT '第 1 周的周一',
  `total_weeks` TINYINT     NOT NULL                COMMENT '总周数',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP                COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIGINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学期';

-- ------------------------------------------------------------
-- 课程
-- ------------------------------------------------------------
-- 本表**含 user_id**，隔离由拦截器负责。
--
-- 注意 `semester_id` 只是个外键**值**，没有任何约束能保证它指向的学期
-- 属于同一个人。租户插件管的是 wb_course 自己的 user_id，管不到这一列 ——
-- 所以"新增课程时先确认那个学期是自己的"必须在 Service 里显式做
-- （见 CourseServiceImpl）。漏了不会报错，只会留下一条指向别人学期的悬空记录。
--
-- 一天 **6 节**：1 早晨 / 2-3 上午 / 4-5 下午 / 6 晚上。
-- 时段归属**不存库**，由前端按节次推导 —— 存一份就是给"上午"这种说法
-- 留了两个可能不一致的定义。
--
-- 一门课在课表上占多个格子（周一 1-2 节、周三 3-4 节），就在表里存**多条记录**，
-- 不设计"多时段"字段。代价是同一门课的若干条要一起改；好处是每一格都是
-- 一条独立、可单独移动的记录，课表这种"天天在微调"的东西按格子存更顺手。
--
-- `week_type`：0 每周 / 1 单周 / 2 双周。单双周是相对**学期的第几周**而言的，
-- 不是自然周的奇偶 —— 换算时要用 (第 N 周)，别拿日历周的周数去判。
CREATE TABLE `wb_course` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT      NOT NULL                COMMENT '所属用户',
  `semester_id`   BIGINT      NOT NULL                COMMENT '所属学期',
  `name`          VARCHAR(50) NOT NULL                COMMENT '课程名',
  `teacher`       VARCHAR(50) NOT NULL DEFAULT ''     COMMENT '任课老师',
  `location`      VARCHAR(50) NOT NULL DEFAULT ''     COMMENT '上课地点',
  `day_of_week`   TINYINT     NOT NULL                COMMENT '星期几 1-7',
  `start_section` TINYINT     NOT NULL                COMMENT '开始节次 1-6',
  `end_section`   TINYINT     NOT NULL                COMMENT '结束节次 1-6',
  `start_week`    TINYINT     NOT NULL                COMMENT '起始周',
  `end_week`      TINYINT     NOT NULL                COMMENT '结束周',
  `week_type`     TINYINT     NOT NULL DEFAULT 0      COMMENT '周类型：0 每周，1 单周，2 双周',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP                COMMENT '创建时间',
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       BIGINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，非 0 为删除时间戳',
  PRIMARY KEY (`id`),
  -- 前导列覆盖"查某人某学期的全部课"，外加 user_id 本身
  KEY `idx_user_semester` (`user_id`, `semester_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程';
