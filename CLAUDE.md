# stfworkbench

前后端分离的工作台项目，由两个**独立**的子项目组成（非 monorepo，无根级构建）：

| 目录 | 角色 | 技术栈 |
|---|---|---|
| `stfworkbench-vue/` | 前端 | Vue 3.5 + Vite 8 + TypeScript 6 |
| `workbench-server/` | 后端 | Spring Boot 4.1.1 + Java 17 + Maven |

## 当前状态

两个子项目**都还是初始化脚手架**，尚无业务代码：

- 前端只有 `src/App.vue`（Vue 默认欢迎页）和 `src/main.ts`
- 后端只有 `WorkbenchServerApplication.java` 和空的 `application.properties`
- 已在根目录 `git init`（`main` 分支），两个子项目纳入**同一个仓库**，详见 [Git 操作](#git-操作)

### 两个已知的"还没打通"

1. **后端目前起不了 Web 服务** —— `pom.xml` 只依赖 `spring-boot-starter`，没有 `spring-boot-starter-web`。在加这个依赖之前，写不了 `@RestController`，也没有内嵌 Tomcat。
2. **前后端尚未连接** —— 前端没有 HTTP 客户端封装（无 axios），`vite.config.ts` 里也没有 `server.proxy` 转发规则。两边目前完全独立。

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

> ⚠️ 本节是**目标状态**，不是现状。其中提到的 MySQL / MyBatis-Plus / Pinia / Axios / Element Plus
> **目前都还没装**（后端 pom 只有 `spring-boot-starter`，前端 package.json 只有 `vue`）。
> 规范先定下来，写代码时按此执行；但**不要假设这些依赖已经存在**。

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

1. **技术栈**：Vue3 + `<script setup>` + TypeScript + Pinia + Axios + Element Plus / Ant Design Vue
   > 除 Vue3 + TS + Vite 外，Pinia / Axios / 组件库**均未安装**；组件库二选一，定了要同步到本文件。
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
