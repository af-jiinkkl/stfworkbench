# stfworkbench

前后端分离的工作台项目，由两个**独立**的子项目组成（非 monorepo，无根级构建）：

| 目录 | 角色 | 技术栈 |
|---|---|---|
| `stfworkbench-vue/` | 前端 | Vue 3.5 + Vite 8 + TypeScript 6 |
| `workbench-server/` | 后端 | Spring Boot 4.1.1 + Java 17 + Maven |

## 当前状态

**六条链路已打通：注册 → 登录 → 首页、每日计划（增删改查 + 勾选 + 回顾）、
生日纪念日（增删改查 + 首页提前提醒）、备忘录（分页 + 关键词搜索 + 详情）、
每日消费（增删改查 + 区间/分类筛选 + 分类饼图 + 月度折线图）、
课程表（学期 + 每周网格 + 周次导航 + 单双周 + 首页今日课程）。
首页由 `GET /api/dashboard` 一次聚合今日计划 + 今日课程 + 临近生日 + 备忘条数 + 今日消费**
（分支 `feature/auth-login`，尚未合并到 `main`）。

后端：

- Web 层用 `spring-boot-starter-webmvc` —— Spring Boot 4 里 `spring-boot-starter-web` 已标记废弃
- 持久层 MyBatis-Plus 3.5.17。starter 名是 `mybatis-plus-spring-boot4-starter`，
  且**必须**另加 `mybatis-plus-jsqlparser`，否则分页与数据隔离插件运行时会找不到类
- **Jackson 用的是第 3 代**（包名 `tools.jackson`，定制器是 `JsonMapperBuilderCustomizer`）。
  唯一的例外是注解包，仍是 `com.fasterxml.jackson.annotation`。注意 `jjwt-jackson`
  会拖进一个 Jackson 2，那是 JWT 内部用的，别混用
- 认证为 JWT + BCrypt；数据隔离靠 `MybatisPlusConfig` 的租户插件自动注入 `user_id` 条件，
  业务代码里**不要**手写 `user_id` 过滤。拦截器顺序不能颠倒：隔离必须在分页之前
- **`@MapperScan` 必须留在 `MybatisPlusConfig` 上，不要挪到启动类。**
  切片测试（`@JsonTest` / `@WebMvcTest`）沿包向上会找到启动类当配置类，而切片不装配
  MyBatis；`@MapperScan` 一旦在启动类，切片就会因找不到 `SqlSessionFactory` 而启动失败，
  报错却是一句和被测内容无关的 "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required"。
  普通 `@Configuration` 会被切片排除，所以放在 `MybatisPlusConfig` 里是安全的
- 建表脚本 `workbench-server/src/main/resources/db/schema.sql`，**不会自动执行**，需手动运行
- **带 `user_id` 的实体一律不要写 `userId` 属性**（见 `PlanTask`）。租户插件只在
  INSERT 时补**列清单里没有**的列；实体一旦带 `userId`，MyBatis-Plus 会把它写进列清单，
  拦截器见状就不再补 —— 于是"谁把 `userId` 赋错值就写进谁名下"，成了绕过隔离的越权通道
- **清空字段必须用 `LambdaUpdateWrapper` 显式 `.set(..., null)`**，不能用 `updateById`。
  MyBatis-Plus 默认字段更新策略是 `NOT_NULL`，null 字段会被**跳过**而非写进 SQL ——
  每日计划取消勾选时 `completed_time` 正是靠这一点才能清回 null
- **`createTime` / `updateTime` 一律要带
  `@TableField(updateStrategy = FieldStrategy.NEVER)`**（五个实体全都要有）。
  列定义里写着 `ON UPDATE CURRENT_TIMESTAMP` 看着像"库里自己会维护"，但这条规则
  **只在那一列没被显式赋值时才生效**；而 `updateById(实体)` 会把每个非 null 字段都写进 SET，
  其中就有刚从库里读出来的旧时间戳 —— 于是 MySQL 把你给它的旧值写回去，自动更新轮不上。
  症状是"改完保存，列表上的时间纹丝不动"，用户以为没保存上；
  它不报错，现有的 `updateTime >= createTime` 断言也照样满足，所以**一条用例都不会红**。
  `EntityTimestampConventionTest` 是为此加的结构性守卫：扫 `@TableName` 注解找出全部实体，
  凡是声明了这两个字段的都必须带这个注解，漏一个就点名报出来
- 未查到（含"存在但属于别人"）统一返回 **404 而非 403**：403 等于确认该 id 存在，
  而主键连续自增，这就成了存在性探测点
- 全局异常处理器里**路径找不到、方法不对各有专门的出口**（404 `接口不存在` /
  405 `该接口不支持这个请求方法`，且 405 带 `Allow` 响应头）。不接住它们，
  两者都会落到兜底的 500 + 一条 ERROR 堆栈 —— 于是"前端把 URL 拼错了"看起来像后端崩了，
  排查方向直接反了。加这两个 `@ExceptionHandler` 之前实测过一次：
  `/api/nope` 与 `GET /api/semester/{id}` 都在 `run.log` 里各留了一条"未预期的异常"
- body 里的 `code` **必须和 HTTP 状态码一致**（见 `ResultCode`）。第一版的
  `handleMethodNotSupported` 回了 HTTP 405 而 body 里写着 `400`（复用了 `BAD_REQUEST`），
  违反了本类开头的约定，所以补了 `METHOD_NOT_ALLOWED(405, ...)` 而不是将就
- **业务意义上的"今天"一律用 `common/util/WorkbenchTime.today()`**，别在模块里各写一个
  `ZoneId.of("Asia/Shanghai")`。各处自己定义时，每个模块单看都对，跨模块却会漂 —— 这种
  不一致极难查，因为没有任何一处是"错"的
- **生日/纪念日只存 `month` + `day`，不存年份**（每年重复，年份没有意义）。代价是"下一次
  是哪天"必须算：见 `common/util/YearlyRecurrence`，这件事在 SQL 里做不了 —— 12 月看 1 月的
  生日若按月份大小比较，会得出"已经过了 359 天"。2 月 29 日在平年**退到 2 月 28 日**，
  不跳到 3 月 1 日：提醒这个场景里"提前想到"比"事后想起"有用
- **`daysUntil` / `nextDate` 由后端算好返回**（`/api/anniversary/upcoming`），前端不许自己推。
  前端再算一遍就多出一个可能与后端分叉的口径，而生日差一天没人会当成 bug 报上来
- **分页接口返回 `PageResult`，不要直接返回 MyBatis-Plus 的 `Page`**（见 `common/result/PageResult`）。
  `Page` 的 JSON 里带着 `orders` / `optimizeCountSql` / `searchCount` 这些实现细节，
  一旦序列化出去就成了对外契约，以后想换持久层都换不掉
- **分页参数要自己夹到合法范围**，别指望 MyBatis-Plus 兜底：它对 `size < 0` 的处理是
  "不再改写 SQL"，也就是**不翻页、整表捞出来**，而不是"取 0 条"。
  这类"库替你容错"的行为方向恰好是危险的那一边（见 `MemoServiceImpl#page`）
- **写接口（POST / PUT）必须回读一次再返回**：凡是由数据库定的值，MyBatis-Plus 插完
  **不会**带回实体，直接把实体转 VO 就得到一份自相矛盾的数据（POST 说 `createTime: null`，
  紧接着 GET 同一个 id 却有值）。已知有两类：
  - 时间戳 —— `create_time` / `update_time` 由 `DEFAULT CURRENT_TIMESTAMP` 填。
    `MemoServiceImpl#create` 因此插完再查一次
  - **`DECIMAL(10,2)` 的小数位** —— 传 `10.5` 存进去是 `10.50`，而实体里那份仍是 scale=1 的
    `10.5`。这个比时间戳那条更隐蔽：**数值是相等的**，端到端跑一遍都不觉得哪里不对，
    只有 JSON 原文里才看得出两种写法。断言得用 `toPlainString()`，
    `isEqualByComparingTo` 会认为两者相等，正好把要验的放过去
- 备忘录的搜索条件是 `title LIKE ? OR content LIKE ?`。这里的 `and(w -> ...)` **不是**在
  堵一个现成的漏洞 —— 曾以为会被 AND/OR 优先级吃掉 `user_id`，实测不成立：
  MyBatis-Plus 的 `NormalSegmentList.childrenSqlSegment()` 无条件给整段条件套括号（3.5.1 起如此）。
  留着它是防"这层括号是没写进文档的实现细节"。完整说明见 `MemoServiceImpl#page` 的注释
- 关键词搜索**没有转义 LIKE 通配符**：搜 `%` 会命中自己的全部记录。
  隔离仍然成立（看到的还是自己的），所以当成已知行为记着即可，不算漏洞
- **`GET /api/dashboard` 一行 SQL 都不写**，四个字段分别调
  `PlanTaskService#listByDate` / `AnniversaryService#upcoming` /
  `MemoService#count` / `ExpenseService#sumOf`。
  在这一层自己拼 wrapper 就会有第二份"哪些日子算即将到来"的判断 —— 两份都能跑、
  都不会报错，只在某天悄悄给出不同的天数。聚合省的是**前端那几次 HTTP 往返**，
  不是后端的一次查询。往首页加卡片时往 `DashboardVO` 加字段，别让前端再发一个请求
- **`ExpenseService#sumOf` 是把 `summaryByCategory` 的结果加起来**，不另写一条
  `SUM(amount)`。分组本身就是一次查询，这里省的不是查询次数，而是"哪些行该被算进来"
  只有一处判断 —— 两条路径各写一遍，将来只给一侧加了条件，两边给出的合计数就会不一样，
  而差几毛钱没有人会当成 bug 报上来
- `TodayPlanVO#of(tasks)` 里的 `total` / `completed` 由入参**推导**，不让调用方传。
  分开传就有"tasks 里 5 条、total 写着 4"的可能，而这种错不抛异常，
  只是首页的分母悄悄不对。由一处推导，矛盾在结构上就发生不了
- `MemoServiceImpl#count` 用的是 `selectCount(null)`，**代码里一个谓词都没有** ——
  隔离与逻辑删除全靠拦截器，和 `AnniversaryServiceImpl#upcoming` 属于同一类
  "看不见条件"的查询。这类路径在本仓库一律要单独钉一条用例
- 消费的分类：**写的时候严，筛的时候松**。写入时 `category` 必须在
  `common/ExpenseCategory` 里预置的 6 个内（trim 之后比对），
  非法值 400 且提示里列出**当前**的合法取值（从枚举现推，不抄字面量）；
  而筛选参数里的 `category` 不做校验，非法值就是"查不到"。这是个刻意的差异：
  写入的值要落库，筛选只是个查询条件，"没有匹配"本身就是正当答案。
  统一成一样的话，用户在下拉框里试错会一直撞红字
- 两张图的补零策略**恰好相反**，都是有意的：按月趋势**恒 12 项**、空月份补 `0.00`
  （折线图横轴要是完整时间轴，缺月份会把 3 月直接连到 7 月，看着像"稳步增长"）；
  按分类**不补零**（补了饼图会多出几块永远为 0 的扇区，图例被撑长，
  真正花过钱的分类反而挤在一起）。补零一律在后端做，前端不许自己拼月份
- `summaryByMonth` 的 `year` 越界（不在 `1900..9999`）要报**业务异常**，
  不能直接交给 `LocalDate.of` —— 那会抛 `DateTimeException`，归到 500 去
- 汇总 SQL 用 `MONTH(expense_date)` 而不是 `DATE_FORMAT(..., '%Y-%m')`：
  年份已由入参定死，SQL 里没必要再拼一次前缀，顺带让这段 wrapper 不含任何字面量
  （引号、`%`），而 MyBatis-Plus 对传进 wrapper 的字符串是做注入检查的
- **学期的 `startDate` 必须是第 1 周的周一**（`SemesterServiceImpl#requireMonday`）。
  整张课表按 `startDate + (第 N 周 - 1) * 7 + (星期几 - 1)` 换算每一天的日期，
  基准若不是周一，整个学期整体偏几天 —— 而它显示出来**仍然是一张看着完全正常的课表**。
  只报错、**不替用户挪到最近的周一**：日期被悄悄改掉比报错难查得多
- 跨表的两条"宁可让用户多操作一步"的规则都在 `SemesterServiceImpl`：
  **有课程时不许删学期**，以及**改小 `totalWeeks` 时若有课的 `endWeek` 落到范围外就拒绝**
  （提示里点名哪几门课）。第二条接口清单里原本没写，是照着第一条补的 ——
  不挡的话，排在第 18 周的课既没被删、也不再显示在任何一格上，数据还在库里但从此看不见
- **`CourseServiceImpl#requireOwnedSemester` 是本模块安全上最要紧的一处**：
  租户插件管 `wb_course.user_id`，但管不到 `semester_id` 指向谁。少了它，
  拿着别人的学期 id 就能把自己的课塞进别人的课表（`CourseIsolationTest#createRejectsAnotherUsersSemester` 钉的就是这条）
- **`CourseServiceImpl#listOnDate` 用 `semesterMapper.selectList(null)` 取全部学期**，
  再在 Java 里筛出包含今天的那一个 —— SQL 里**一个字都没有**，隔离全靠拦截器。
  和 `AnniversaryServiceImpl#upcoming` / `MemoServiceImpl#count` 属于同一类
  "看不见条件"的查询，所以单独有隔离用例
- **`CourseTime` 是"哪些课算这一周"的唯一后端实现**（`occursOnWeek`），
  前端 `types/course.ts` 里那份是它的镜像 —— 翻周是纯前端行为，
  后端只有一个 `listOnDate` 管"今天"。两处判反的表现是"单周的课在第 4 周显示出来"，
  界面不报错，只是那门课不该在。要改就两边一起改

测试（`./mvnw test`，需先设 `DB_PASSWORD` 与 `JWT_SECRET`，因为要连真实 MySQL）：

- `DataIsolationTest` —— **数据隔离的回归测试**，安全底线。用一张临时探针表
  （`wb_isolation_probe`，由测试自己建）验证：不写任何 `user_id` 条件时，
  增删查改是否仍被限定在当前用户内，包括"拿别人的主键查"这种越权场景。
  新增带 `user_id` 的表后，应照着它补用例
- `PlanTaskIsolationTest` —— 同一件事，但验的是**真实实体与 Mapper 接上拦截器之后**
  是否也有效（探针表证明机制可用，它证明本模块确实用上了）。数据用原生 `JdbcTemplate`
  带显式 `user_id` 种入，绕开 MyBatis —— 若改用 Mapper 插，拦截器一失效就会在**插入**
  阶段抛 NOT NULL，测试红了却红在错误位置，读改写删的越权断言根本没跑过
- `AnniversaryIsolationTest` —— 同 `PlanTaskIsolationTest`，另加一条本模块特有的用例。
  `upcoming` 是"把当前用户的全部记录取出来、再在 Java 里筛"，SQL 里**没有任何 WHERE**，
  隔离全靠拦截器，所以这条路径值得单独钉。验证方式：把表加进 `TABLES_WITHOUT_USER_ID`
  跑一遍，看别人的生日是不是当场出现在你的提醒里（会）
- `MemoIsolationTest` —— 同 `PlanTaskIsolationTest`，验 `wb_memo` 的隔离。
  它多钉了一条**搜索**路径：这是全仓库唯一一条 WHERE 由代码拼出来的查询
  （其余都是等值匹配或主键查），拼错了是能绕过隔离的
- `ExpenseIsolationTest` —— 同 `PlanTaskIsolationTest`，验 `wb_expense` 的隔离，
  另加两条本模块特有的：**两个汇总接口只统计自己的记录**（它们各自是一条
  `GROUP BY`，条件里没有 `user_id`，隔离全靠拦截器），以及**已逻辑删除的记录不计入汇总**
  —— 汇总走的是 `selectMaps`，不经过实体，逻辑删除是否被拼进去要单独确认
- `MemoServiceTest` —— 第一个**服务层**用例，验的是写接口的**响应形状**而不是隔离：
  POST / PUT 返回的 `createTime` / `updateTime` 必须非空且格式正确。
  这个 bug 上过线：数据库的 `DEFAULT CURRENT_TIMESTAMP` 填了值，
  但 MyBatis-Plus 不把它带回来，于是 POST 返回 `null`、紧接着 GET 却有值。
  **上一条隔离测试当时是全绿的** —— 它压根不看那两个字段。
  两个类盯的是不同的东西，缺一个就会漏掉这一类
- `ExpenseServiceTest` —— 消费的服务层用例，三条主线：**金额的精度与写法**
  （`0.10 + 0.20` 恰好是 `0.30`，把"金额不许用 FLOAT/DOUBLE"变成会红的断言；
  `10.5` 进、`10.50` 出）、**写路径的响应形状**、**入参校验**
  （分类合法性、区间颠倒、`year` 越界、以及"筛选用的非法分类不算错"这条刻意差异）。
  还有一条 `updateAdvancesUpdateTime` 钉 `ON UPDATE CURRENT_TIMESTAMP` 真的生效 ——
  它 `sleep(1100)`，因为 DATETIME 只到秒，不跨秒就分不清"变了"和"没变"
- `EntityTimestampConventionTest` —— **纯结构断言，不连库**。扫 `@TableName` 找出全部实体，
  凡是声明了 `createTime` / `updateTime` 的都必须带
  `@TableField(updateStrategy = FieldStrategy.NEVER)`。它盯的不是某一次行为，
  而是"新加实体时别忘了这个注解"—— 那件事没有任何运行时症状。
  **已验证过它逮得住**（摘掉 `Memo.updateTime` 上的注解，当场变红并点名报出来）。
  另有一条常驻用例 `scannerFindsEntities`：万一扫描器哪天扫不到东西，
  主用例会因为"没有违规项"而永远绿灯 —— 一条永远绿灯的守卫比没有守卫更糟
- `DashboardServiceTest` —— 首页聚合的**隔离 + 自洽**。它是全仓库唯一一次返回三张表，
  两类风险都聚在这里：三份数据是不是都只含自己的（`memoCount` 那条最要紧，
  它底下的 `selectCount(null)` 代码里没有 WHERE）；以及 `total` / `completed` /
  `tasks` 三个数对不对得上、是不是只统计今天。
  **已验证过它逮得住**：把 `wb_memo` 加进 `TABLES_WITHOUT_USER_ID`，
  本类 6 条里 4 条当场变红（`expected: 2L but was: 85L`）
- `TodayPlanVOTest` —— 纯单元（不连库）。钉的是"`total` / `completed` 必须由
  `tasks` 推导"这个结构，另有一条不变量式的用例扫一大片组合。
  它和 `DashboardServiceTest` 分工不同：那边验接了真实数据库之后三个数还对不对得上
- `YearlyRecurrenceTest` —— 跨年推算的纯单元测试（不连库，<1ms）。这条最值得写：
  生日算错的表现很隐蔽 —— 界面不报错，只是安静地不提醒，所以闰年、当天、跨年这些
  边界逐个钉死，另加两条不变量式的用例扫一大片
- `JacksonConfigTest` —— 无数据库，验证日期格式与 null 字段不被吞掉
- `CourseTimeTest` —— 周次与单双周的**纯单元测试**（不连库）。它和 `YearlyRecurrenceTest`
  是同一类：算错了界面上不会有任何提示，只是那门课安静地不出现在该在的格子里。
  单双周按**学期第几周**判而不是按日期（`dayOfWeek` 与奇偶无关），
  另有一条 `parityTable` 把第 1–6 周三种周类型的结果逐周写死，
  改判法时它会把"哪一周变了"直接列出来
- `SemesterIsolationTest` —— `wb_semester` 的隔离，外带两条**跨表**规则：
  **有课程时不许删学期**（并断言提示里的门数，只说"还有课程"用户不知道该删几门）、
  **改小 `totalWeeks` 时超范围的课会拦住这次修改**（断言里带课程名与它排到的周数）。
  各配一条"合法时必须放行"的用例 —— 否则"永远拒绝"也能全绿。
  另有 `deleteIsNotBlockedByAnotherUsersCourses`：别人学期里的课**不能**算在自己头上，
  否则 A 删自己那个空学期会被 B 的课挡住，提示还说"还有 3 门课"，用户无从下手
- `CourseIsolationTest` —— `wb_course` 的隔离，本模块特有的用例最多，因为
  **`semester_id` 是一条租户插件管不到的越权通道**：拿着别人的学期 id 建课 / 把自己的课挪进去，
  两条都钉了（前者断言"库里一行都不多"）。`listBySemester` / `listOnDate` 两条查询路径也各有一条，
  后者正是首页 `todayCourses` 底下那个 `selectList(null)`
- `CourseServiceTest` —— 课程的服务层用例，主线是**入参校验**（星期几 1–7、节次 1–6、
  开始节次不晚于结束、周次落在学期范围内、周类型只能是 0/1/2）与**写路径的响应形状**
  （`createReturnMatchesSubsequentRead`：新增返回的对象必须与随后查到的完全一致）。
  `updateClearsTeacherAndLocation` 钉的是"清空要真的写进库" ——
  MyBatis-Plus 默认跳过 null 字段，所以 `CourseServiceImpl#apply` 把可空的
  老师 / 地点 `trimToEmpty` 成**空串**（PUT 是全量替换，留成 null 的话那条 SET 会整条消失，
  界面上看着已清空、刷新一下旧值又回来了）

前端：

- Element Plus / Pinia / vue-router / Axios 均已安装
- `src/utils/request.ts` 统一封装（注入 token、处理 401 跳登录）
- `vite.config.ts` 已配 `server.proxy`，把 `/api` 转发到后端 8080
- **日期一律走 `src/utils/date.ts`，不要用 `new Date()` 那一套**。两处坑：
  `toISOString()` 取的是 UTC，在东八区上午 8 点前会得到前一天（白天看不出来，早上才炸）；
  `new Date('2026-09-19')` 按 UTC 午夜解析，同样会偏。另外 `el-date-picker` 的
  `value-format` 用的是 dayjs 记号（`YYYY-MM-DD`），和后端 Java 的 `yyyy-MM-dd` 长得像但不是一回事
- **`@Valid` 只管请求体，不管 URL 查询参数的类型转换**。`LocalDate` 查询参数必须
  自己带 `@DateTimeFormat(iso = ISO.DATE)`，`JacksonConfig` 覆盖不到 MVC 这一层
- 触屏/悬停之外的交互：`PlanView.vue` 里行内编辑用双击或铅笔图标进入，Enter 保存、
  Esc 取消。取消靠 `editingId` 置空挡掉随后那次 blur，否则"取消"会把改动存进去
- **`el-select` 放进 flex 行里会被压成一个箭头宽**，选中值随即被裁掉，看上去像没选上。
  得给那个 `el-form-item` 加 `flex: 1`（见 `AnniversaryView.vue` 的 `.date-row`）。
  直接放在 `el-form-item` 下的 select 不受影响，所以这个问题只在并排的日期选择器上冒出来
- 首页的提前提醒与纪念日页共用 `components/UpcomingAnniversaryList.vue`：
  "还有几天"的说法只该有一处实现，两边各写一遍迟早显示成两个不同的天数
- 首页的三块数据来自**一次** `GET /api/dashboard`（见 `api/dashboardApi.ts`），
  不要为了某一块单独再调一次 `/api/anniversary/upcoming` 之类的接口 ——
  那样又会退回到三个 loading 各亮各的，聚合就白做了
- 首页今日任务是**只读**的：不做勾选框、不进编辑，要改去每日计划页。
  两处都能改的话，同一件事就有了两个入口，出问题时不知道是哪边写的
- 今日任务一条都没有时显示"今天还没有安排"，而不是"已完成 0 / 0" ——
  后者看着像出了错。同理，生日没有临近记录时卡片上不给数字，空着比"0 条临近"自然。
  **今日消费的 `0` 要照实显示成 `¥0.00`**，正好相反：它是今天确实还没花钱，
  是个有意义的数字，空着反而像没取到数据
- **金额一律走 `src/utils/money.ts` 的 `money()`**，别各处 `toFixed(2)` 拼字符串 ——
  它带千分位，且首页和消费页都要用。这个模块**故意没有 `add()` / `sum()`**：
  汇总在后端做（见后端一节），前端把列表里的金额加起来就会多出一份可能分叉的口径
- 图表统一走 `components/EChart.vue`，不要在页面里各写一份 `echarts.init`：
  实例用 `shallowRef`（`ref` 会给 echarts 内部几百个对象套 Proxy）；
  用 `ResizeObserver` 而不是 `window.resize`（侧栏折叠、路由切换也会改变容器宽度）；
  每次 `setOption(option, true)` 走 notMerge（默认合并会让消失的图例留在屏幕上）；
  销毁顺序是 `observer.disconnect()` 先于 `chart.dispose()`
- **图表容器要给固定 `height`，`min-height` 不行** —— echarts 初始化时量到 0 高度，
  之后不会自己长回来，画出来的是一张高度为 0 的空白。见 `ExpenseView.vue` 的 `.chart-box`
- echarts 按需引入（`echarts/core` + 各 `echarts/charts`、`components`、`renderers`），
  消费页因此是**独立懒加载 chunk**（500 kB 量级）。别改成整包引入 ——
  那笔体积会摊到首屏，而首页只显示一个"今日 ¥xx.xx"的数字，用不上任何图表
- 消费页保存后，**若这一笔落在当前筛选范围之外就重置筛选**并说明原因
  （"已保存；这一笔不在当前筛选范围内，已重置筛选"）。不重置的话，
  补录一笔上个月的账，界面看上去像没保存上 —— 而它其实已经写进库了
- 课程表用 **CSS Grid**，靠显式 `grid-column` / `grid-row` 定位每一块课。
  **第 1 列是时段带，7 个星期是第 2..8 列**；表头在第 1 行、节次行从第 2 行起。
  写选择器或验证脚本时差 1 的表现是"每一列都匹配不上"，而不是"整体偏一列"，
  很容易误判成布局没生效
- 课程表保存后，**若这一周不在课程的周次范围内就跳到那门课的第一周**并说明原因
  （"已保存；这门课从第 3 周开始，已跳到那一周"）。和消费页那条是同一个道理：
  不跳的话，把一门课改成 3–4 周时正看着第 1 周，界面上什么都不会变，像没保存上
- 周次与单双周的判断（`occursOnWeek`）在 `types/course.ts` 里是后端 `CourseTime` 的**镜像**。
  翻周纯在前端做，后端只有一个 `listOnDate` 管"今天"，所以这份镜像省不掉 ——
  但改判法时必须两边一起改
- `sectionText` / `weekRangeText` 这类文案函数只有一处实现（`types/course.ts`），
  课表格子、列表、首页今日课程都调它。各写一遍的话，同一门课在两处会显示成
  "第 1-2 节" 和 "1-2 节"，没人会当成 bug 报上来
- **列表页的写操作（`save` / `remove` / `init`）必须自己 `catch`**，哪怕只是 `catch {}`。
  后端拒绝（400/404）时 axios 抛的是 rejection，不接住就冒成一条 `unhandled rejection`，
  在浏览器里表现为一条来源不明的 [`pageerror`] —— 真正有用的那句提示已经在
  `ElMessage` 里给过用户了，这条噪音只会把排查方向带偏
- **`el-dialog__footer` 是 `text-align: right` 的行内布局**，往按钮上加
  `margin-right: auto` **不管用**（auto 外边距只对块级/弹性项生效）。
  要把"删除"单独推到最左边，得先用 `:deep(.el-dialog__footer) { display: flex }` 把它变成 flex。
  它是 el-dialog 渲染的、不在本组件模板里，scoped 选择器够不到，必须 `:deep()`

### 启动前必须设置的环境变量

| 变量 | 说明 |
|---|---|
| `JWT_SECRET` | 至少 32 字符。**没有默认值**，不设置则后端启动即失败（见 `JwtUtil`） |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 账号密码，用户名默认 `root` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 默认 `localhost` / `3306` / `stfworkbench` |

后端启动失败时先看是不是漏了 `JWT_SECRET`。前端开发时无需设置 —— 请求经 Vite 代理转发。

> **改完后端要重启，别对着旧进程验证。** 现象是"新写的接口一律 500 / 404，
> 日志里每条都落在兜底分支"，很容易误判成新代码写错了。
> 这个坑踩过一次：`/api/semester` 明明刚写完，却一路报 500，
> 查了半天才发现那个 JVM 是**课程模块之前**启动的，`GET /api/dashboard`
> 连 `todayCourses` 这个 key 都没有。前端跑的浏览器脚本更是分不清
> "后端没重启"和"后端有 bug"——它只会把差异报成界面问题。
> 排查顺序固定为：先重启后端，再看日志，最后才怀疑代码。

## 常用命令

前端（在 `stfworkbench-vue/` 下，用 pnpm）：

```bash
pnpm dev          # 启动开发服务器
pnpm build        # 类型检查 + 构建
pnpm type-check   # 仅类型检查（vue-tsc）
pnpm preview      # 预览构建产物
```

后端（在 `workbench-server/` 下）：

```bash
./mvnw spring-boot:run    # 启动
./mvnw test               # 跑测试
./mvnw clean package      # 打包
```

> Windows 上也可用 `mvnw.cmd`。注意本仓库的 Claude Code 会话中，Bash 工具走的是 **Git Bash**，请用 POSIX 语法与正斜杠路径。

## 环境要求

- Node `^22.18.0 || >=24.12.0`（见 `package.json` 的 `engines`）
- JDK 17

## 目录约定

前端：

- `@` 别名指向 `src/`（已在 `vite.config.ts` 配置），导入统一用 `@/...`
- 使用 `<script setup lang="ts">` + Composition API
- 构建流程包含 `vue-tsc` 类型检查，因此类型错误会导致构建失败

后端：

- 基础包 `org.example.workbenchserver`
- 含 Maven wrapper，统一用 `./mvnw` 而非系统 maven

---

## Git 操作

仓库已在根目录 `E:\project\stfworkbench` 初始化，两个子项目位于**同一个仓库**内，长期分支为 `main`。

### 分支模型：简单分支流

- `main` 始终保持可运行，**不直接在上面开发**
- 每个改动开独立分支，完成后合并回 `main`
- 分支命名沿用「命名约定」：`feature/xxx`、`fix/xxx`、`refactor/xxx`、`hotfix/xxx`、`docs/xxx`
- 分支短命：一个分支只做一件事，合并后立即删除

### 日常流程

```bash
# 1. 从最新的 main 切出新分支
git switch main && git pull
git switch -c feature/user-list

# 2. 开发中提交（add 明确指定文件，勿用 git add .）
git add stfworkbench-vue/src/views/UserList.vue
git commit -m "feat(user): 新增用户列表页"

# 3. 推送分支，然后在 GitHub 网页端开 PR
git push -u origin feature/user-list

# 4. PR 合并后回到 main 并清理分支
git switch main && git pull
git branch -d feature/user-list
```

> PR 默认在 GitHub 网页端创建。如需命令行创建，需先安装 GitHub CLI（`gh`）——当前**尚未安装**；装好后可用 `gh pr create --base main --fill`。

### 提交要点

遵循 Angular 规范 `type(scope): message`（见 [提交规范](#提交规范git-commit-message)）。补充要求：

- **一次提交只做一件事**，不混入无关的格式化改动
- `git add` 明确指定文件，避免 `git add .` / `git add -A` 误纳入无关文件
- 提交信息用中文描述业务含义，如 `feat(user): 新增用户分页查询接口`

### Claude Code 会话中的约定

以下几点请严格遵守：

- **提交 / 推送仅在明确要求时执行**，不要自作主张 `commit`
- 动手改代码前先 `git status` 确认工作区状态，避免覆盖未提交的改动
- **禁止 `git push --force`**，尤其禁止对 `main` 强推
- **禁止 `git reset --hard` / `git checkout -- .`** 丢弃改动，除非明确要求
- 前后端改动若成对，放在同一条分支上

### 不应提交的内容

`.env*`、密钥、`node_modules/`、`dist/`、`target/`、IDE 配置目录 —— 已由根级与子项目的 `.gitignore` 覆盖；个人本地记忆 `CLAUDE.local.md` 亦已忽略。

---

## 开发规范

> 本节所列依赖**均已安装**（2026-09-19）。但本节仍是**规范**：写代码时按此执行。
> 若发现代码与本节冲突，以本节为准并回头修正代码。

### 命名约定

**后端 Java**

1. **包名**：全小写，反向域名，分层划分

```
org.example.workbenchserver
├── controller
├── service
├── service.impl
├── mapper
├── entity
├── dto
├── vo
└── common
    ├── result
    ├── exception
    └── util
```

> 基础包沿用脚手架已有的 `org.example.workbenchserver`（对应 pom 的 `groupId` = `org.example`），不改成 `com.xxx.workbench`。

2. **类名**：大驼峰 UpperCamelCase —— `UserController`、`UserServiceImpl`、`UserDTO`
3. **方法名 / 变量**：小驼峰 lowerCamelCase —— `getUserById()`、`userName`
4. **常量**：全大写，下划线分隔 —— `MAX_PAGE_SIZE`
5. **数据库表名 / 字段**：小写 + 下划线 —— `sys_user`、`user_name`，禁止关键字

> 表前缀可选：业务模块前缀，如 `wb_`（workbench）

**前端 Vue3 + TS**

1. **文件**
   - 页面组件：大驼峰 `UserList.vue`
   - 通用组件：大驼峰 `TableSearch.vue`
   - ts / 工具文件：小驼峰 `request.ts`、`userApi.ts`
2. **变量 / 函数**：小驼峰 —— `userList`、`fetchUserList()`
3. **类型 interface / type**：大驼峰 —— `UserInfo`
4. **CSS class**：短横线命名 —— `user-card`

**Git 分支**

- `feature/xxx` 新功能
- `fix/xxx` bug 修复
- `refactor/xxx` 重构
- `hotfix/xxx` 线上紧急修复
- `docs/xxx` 纯文档改动（需求、设计、说明文档）

### 后端分层规范（SpringBoot + MyBatis/MyBatis-Plus）

> 标准四层架构：Controller → Service → Mapper → Entity

1. **Controller**
   - 只负责接收参数、调用 service、封装返回结果，**不写业务逻辑**
   - 参数校验：`@Valid`，异常统一交给全局异常处理器
   - 禁止直接操作数据库
2. **Service / ServiceImpl**
   - 写核心业务逻辑、事务控制（`@Transactional`）
   - 多个 Mapper 联动、数据组装放在 Service 层
3. **Mapper**
   - 数据库 CRUD 操作，MyBatis / MyBatis-Plus
   - 复杂 SQL 写在 xml 文件
4. **Entity**：数据库实体，和表一一对应
5. **DTO**：入参对象（前端传给后端）
6. **VO**：出参对象（后端返回前端）

> ❗禁止直接把 Entity 返回前端，防止字段泄露

**公共包 `common`**

- `Result<T>`：统一返回包装类
- `GlobalExceptionHandler`：全局异常捕获
- 自定义业务异常 `BusinessException`
- 工具类：日期、加密、文件等

### 接口约定 RESTful

1. **请求方法**
   - `GET` 查询资源（安全、幂等）
   - `POST` 新增
   - `PUT` 全量更新
   - `PATCH` 局部更新
   - `DELETE` 删除
2. **URL**：资源名词，小写，短横线分隔

```
GET    /api/user        查询用户列表
GET    /api/user/{id}   查询单个用户
POST   /api/user        创建用户
PUT    /api/user/{id}   修改用户
DELETE /api/user/{id}   删除用户
```

> 统一前缀 `/api`

3. **统一返回体**

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

- `code=200`：成功；`code=500`：业务异常；`code=401`：未登录；`code=403`：无权限

4. **分页统一参数**

```
pageNum: 1
pageSize: 10
```

返回包含 `total`、`records`

5. **入参**
   - 查询：url 参数
   - 新增 / 修改：`application/json`

### 前端规范 Vue3 + TS + Vite

1. **技术栈**：Vue3 + `<script setup>` + TypeScript + Pinia + Axios + **Element Plus**
   > 组件库已定为 **Element Plus**（2026-09-19 确认）。Pinia / Axios / vue-router / Element Plus 均已安装。
2. **目录结构**

```
src
├── api          接口请求，按模块拆分 userApi.ts
├── assets       静态资源
├── components   公共组件
├── views        页面
├── router       路由
├── store        Pinia 状态
├── utils        工具函数 request.ts、auth.ts
├── types        ts 类型定义
└── App.vue
```

3. **编码规范**
   - 优先使用 `<script setup lang="ts">`
   - 禁止硬编码接口地址，统一封装 axios 请求拦截器
   - 统一处理 token，放在请求头 `Authorization: Bearer xxx`
   - 页面组件拆细，公共组件抽离到 `components`
   - 表单校验：用组件库自带校验规则，统一错误提示
4. **样式**：使用 `scoped`，避免样式污染；尽量使用组件库样式

### 提交规范（Git Commit Message）

采用 Angular 规范：`type(scope): message`

- `feat`：新增功能
- `fix`：修复 bug
- `refactor`：重构，无功能变化
- `docs`：文档修改
- `style`：格式调整（空格、分号，不影响代码）
- `test`：新增 / 修改测试
- `chore`：构建、依赖、工具改动

示例：

```
feat(user): 新增用户分页查询接口
fix(login): 修复token过期判断
refactor: 重构统一返回结果类
```

> 仓库已初始化（`main` 分支），但目前**尚无任何提交**。完整的分支与提交流程见 [Git 操作](#git-操作)。

### 数据库 MySQL 规范

1. **引擎**：InnoDB，字符集 `utf8mb4`，排序 `utf8mb4_general_ci`
2. **必须字段**：`id` 主键自增；`create_time`；`update_time`；`deleted`（逻辑删除，不要物理删数据）
3. **索引**：主键必建；查询条件建立合适索引，禁止过度索引
4. **字段**：尽量 NOT NULL；字符串长度合理；避免 text 滥用
5. **SQL**：禁止 `select *`；大表禁止不带索引查询
