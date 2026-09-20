# stfworkbench

前后端分离的工作台项目，由两个**独立**的子项目组成（非 monorepo，无根级构建）：

| 目录 | 角色 | 技术栈 |
|---|---|---|
| `stfworkbench-vue/` | 前端 | Vue 3.5 + Vite 8 + TypeScript 6 |
| `workbench-server/` | 后端 | Spring Boot 4.1.1 + Java 17 + Maven |

## 当前状态

**四条链路已打通：注册 → 登录 → 首页、每日计划（增删改查 + 勾选 + 回顾）、
生日纪念日（增删改查 + 首页提前提醒）、备忘录（分页 + 关键词搜索 + 详情）。
首页由 `GET /api/dashboard` 一次聚合今日计划 + 临近生日 + 备忘条数**
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
- 未查到（含"存在但属于别人"）统一返回 **404 而非 403**：403 等于确认该 id 存在，
  而主键连续自增，这就成了存在性探测点
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
- **写接口返回的时间戳必须回读一次再返回**。`create_time` / `update_time` 是数据库的
  `DEFAULT CURRENT_TIMESTAMP` 填的，MyBatis-Plus 插完**不会**把生成的值带回实体 ——
  直接把实体转 VO 返回，得到的是一份自相矛盾的数据：POST 说 `createTime: null`，
  紧接着 GET 同一个 id 却有值。`MemoServiceImpl#create` 因此插完再查一次
- 备忘录的搜索条件是 `title LIKE ? OR content LIKE ?`。这里的 `and(w -> ...)` **不是**在
  堵一个现成的漏洞 —— 曾以为会被 AND/OR 优先级吃掉 `user_id`，实测不成立：
  MyBatis-Plus 的 `NormalSegmentList.childrenSqlSegment()` 无条件给整段条件套括号（3.5.1 起如此）。
  留着它是防"这层括号是没写进文档的实现细节"。完整说明见 `MemoServiceImpl#page` 的注释
- 关键词搜索**没有转义 LIKE 通配符**：搜 `%` 会命中自己的全部记录。
  隔离仍然成立（看到的还是自己的），所以当成已知行为记着即可，不算漏洞
- **`GET /api/dashboard` 一行 SQL 都不写**，三个字段分别调
  `PlanTaskService#listByDate` / `AnniversaryService#upcoming` / `MemoService#count`。
  在这一层自己拼 wrapper 就会有第二份"哪些日子算即将到来"的判断 —— 两份都能跑、
  都不会报错，只在某天悄悄给出不同的天数。聚合省的是**前端那两次 HTTP 往返**，
  不是后端的一次查询
- `TodayPlanVO#of(tasks)` 里的 `total` / `completed` 由入参**推导**，不让调用方传。
  分开传就有"tasks 里 5 条、total 写着 4"的可能，而这种错不抛异常，
  只是首页的分母悄悄不对。由一处推导，矛盾在结构上就发生不了
- `MemoServiceImpl#count` 用的是 `selectCount(null)`，**代码里一个谓词都没有** ——
  隔离与逻辑删除全靠拦截器，和 `AnniversaryServiceImpl#upcoming` 属于同一类
  "看不见条件"的查询。这类路径在本仓库一律要单独钉一条用例

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
- `MemoServiceTest` —— 第一个**服务层**用例，验的是写接口的**响应形状**而不是隔离：
  POST / PUT 返回的 `createTime` / `updateTime` 必须非空且格式正确。
  这个 bug 上过线：数据库的 `DEFAULT CURRENT_TIMESTAMP` 填了值，
  但 MyBatis-Plus 不把它带回来，于是 POST 返回 `null`、紧接着 GET 却有值。
  **上一条隔离测试当时是全绿的** —— 它压根不看那两个字段。
  两个类盯的是不同的东西，缺一个就会漏掉这一类
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
  后者看着像出了错。同理，生日没有临近记录时卡片上不给数字，空着比"0 条临近"自然

### 启动前必须设置的环境变量

| 变量 | 说明 |
|---|---|
| `JWT_SECRET` | 至少 32 字符。**没有默认值**，不设置则后端启动即失败（见 `JwtUtil`） |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 账号密码，用户名默认 `root` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | 默认 `localhost` / `3306` / `stfworkbench` |

后端启动失败时先看是不是漏了 `JWT_SECRET`。前端开发时无需设置 —— 请求经 Vite 代理转发。

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
