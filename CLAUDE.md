# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

微途旅行（WeiTu）—— 旅游平台，单仓库多工程：

| 目录 | 内容 |
| --- | --- |
| 仓库根 | Spring Boot 3.5.10 / Java 21 后端，端口 **9528**。业务 CRUD + 已收口的 TripAgent AI 能力 |
| `wtlx-frontend/` | Vue 3 + Vite 6 SPA（36 个页面），开发端口 5173 |
| `image-search-mcp-server/` | 只有一个**预构建 fat jar**（无源码、未纳入 git、`target/` 被忽略），供 MCP 图片检索使用。全新 clone 后该 jar 不存在，MCP 启动会失败 |

代码注释、提交信息、设计文档全部为中文。新增代码请沿用中文注释；实体类保留 `@author 金小梁` 头注释风格。

## 常用命令

### 后端（仓库根目录）

```bash
# PATH 上的 mvn 是 3.6.1，跑不了 spring-boot 3.5 继承的 maven-compiler-plugin，必须先切到 3.9.9
MVN=/d/Maven/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin/mvn

$MVN clean package -DskipTests    # 构建
$MVN spring-boot:run              # 启动（依赖 MySQL / Redis / RabbitMQ / PostgreSQL 全部在线）
$MVN test                         # 全部测试
$MVN test -Dtest=GenerationCancelServiceTest          # 单个测试类
$MVN test -Dtest=GenerationCancelServiceTest#cancelShouldReturnCancelledWhenLocalHit   # 单个测试方法
```

测试只有两个：`GenerationCancelServiceTest` 是纯 Mockito 单测（Redis 用 deep stub 兜住），**无需任何外部依赖，日常验证优先跑它**；`WeiTuApplicationTests` 是空壳 `@SpringBootTest`，需要完整基础设施才能起。

### 前端（`wtlx-frontend/`）

```bash
npm run dev      # http://localhost:5173，/api、/imgs、/video 代理到 9528
npm run build    # 产出 dist/，交由 nginx 静态托管（hash 路由，零 nginx 配置）
```

本机 `npm install` 曾因 esbuild 二进制 spawn 失败而中断；只要 `node_modules` 已存在，直接 `npx vite build` 可完整跑通。无 lint 脚本。

## 后端架构

### 分层约定（严格遵循）

```
Controller → IXxxService(extends IService) → XxxServiceImpl(extends ServiceImpl) → XxxMapper(extends BaseMapper) → MySQL
com.wtlx.controller  com.wtlx.service        com.wtlx.service.impl                  com.wtlx.repository.mapper
```

- 业务逻辑**通常写在 ServiceImpl 里并直接返回 `Result`**，Controller 只做转发（如 `ScenicSpotsServiceImpl.listAll()`）。这是本项目的主流写法，不要"顺手"重构成 Controller 组装。
- `ServiceImpl` 一般不注入 mapper 字段，直接用继承来的 `query()` 链式调用或 `baseMapper`。
- `src/main/resources/mapper/*.xml` 全是空的骨架文件（无任何 SQL 语句），也没有配 `mapper-locations`。自定义 SQL 用注解：`VideosMapper` 的 `@Update`、`ThumbsUpRecordMapper` 的 `@Select`。
- 新增实体：`@Data` + `@EqualsAndHashCode(callSuper=false)` + `@Accessors(chain=true)` + `implements Serializable` + `@TableName` + `@TableId(type=IdType.AUTO)`。

### 统一响应

`com.wtlx.domain.entity.dto.Result` —— 字段就四个：`Boolean success` / `String errorMsg` / `Object data` / `Long total`。

- **没有 `code` 字段，也不用 HTTP 状态码表达错误**：失败一律 `Result.fail(msg)` + HTTP 200。
- 无返回值用 `Result.ok()`；查不到记录也返回 `Result.ok()`（不是 404）。
- `spring.jackson.default-property-inclusion: non_null`，null 字段不出现在 JSON 里，空成功响应就是 `{"success":true}`。
- 全局异常兜底 `WebExceptionAdvice` 只捕获 `RuntimeException` → `Result.fail`。

### 接口路径

**没有 `/api` 前缀**（`/api` 只是 nginx / vite 反代时加上的，会被 rewrite 掉）。每个 Controller 自己映射 kebab-case 根路径，如 `/user`、`/scenic-spots`、`/shop-type`、`/ai`、`/ai/rag`。子路径习惯：`/of/user`、`/of/type`、`/me`。

### 鉴权

无 Spring Security、无 JWT。`MvcConfig` 注册两个拦截器：`RefreshTokenInterceptor`（order 0，读 **`authorization` 请求头原值，无 `Bearer` 前缀**，查 Redis hash `login:token:<token>` 得到 `UserDTO` 放进 `UserHolder` ThreadLocal，并滑动续期）→ `LoginInterceptor`（order 1，ThreadLocal 为空则 401）。

- 取当前用户一律 `UserHolder.getUser()`；在排除路径上取会 NPE。
- 登录返回的 `data` 就是裸 token 字符串。
- 排除路径（免登录）：`/shop/**`、`/voucher/**`、`/shop-type/**`、`/upload/**`、`/imgs/**`、`/video/**`、`/blog/hot`、`/user/code`、`/user/login`、`/guides/**`、`/scenic-spots/**`、`/ai/**`、`/doc.html`、`/webjars/**`。注意 `MvcConfig` 里 `"//provinces/**"` 多了一个斜杠，provinces 实际**未**被排除。

### 三套存储

| 存储 | 用途 | 访问方式 |
| --- | --- | --- |
| MySQL `wtlx` (localhost:3306) | 全部业务表 + `rag_source_index` | MyBatis-Plus |
| PostgreSQL `vector_db` (192.168.150.101) | `vector_store` 向量表 + `conversation_message` / `conversation_summary` | **只能**用 `@Resource(name="aiJdbcTemplate")` 或 Spring AI store |
| Redis | token、缓存、限流、取消信号 | `StringRedisTemplate` / `CacheClient` / Redisson |

AI 的 PG 数据源和 `PgVectorStore` 在 `DataSourceConfig` 里手工装配（`pgVectorStore` 显式绑定 `aiDataSource`）。**任何新加的 PG 相关 bean 都必须绑定 `aiDataSource`**，否则 Spring AI 自动配置会在 MySQL 里建表。

表名有两套并行风格，跟随所在表：老的 `tb_*`（`tb_user`、`tb_blog`、`tb_shop`、`tb_hotel_*`）与新的裸 snake_case（`orders`、`scenic_spots`、`guides`、`rag_source_index`）。没有 Flyway/Liquibase，`src/main/resources/db/` 下是两个手工执行的幂等 `IF NOT EXISTS` DDL。

### 限流与异步

- 限流是 `com.wtlx.utils.RateLimiter`（Guava 令牌桶，JVM 内、按业务 id 分桶），目前**只有 `OrdersServiceImpl.seckill()` 一处调用**，按景点 id 限流。`resources/lua/seckill.lua` 是秒杀的库存/一人一单脚本，`resources/unlock.lua` 是分布式锁释放脚本 —— 都**不是**限流。
- 秒杀：`UserHolder` 取用户 → Guava 限流 → 读 `tb_scenic_seckill` → `seckill.lua` 原子扣减 → 发 `OrderMessage` 到 MQ → 消费者落库。
- 点赞走反向模式：先写 Redis（Set + ZSet 计数），再由 xxl-job 任务 `syncLikedCountJob` 批量刷库。
- 定时任务全是 `@XxlJob`，**cron 配在 xxl-job admin（192.168.150.101:8880）上，不在代码里**。`@EnableScheduling` 开着但没有任何 `@Scheduled`。
- MQ 命名契约：`<biz>.exchange`（topic）/ `<biz>.queue`（durable）/ `<biz>.<action>` 路由键，常量集中在 `RabbitMQConstants`。三组：`thumbs.up.*`、`order.*`、`rag.doc.*`。**由数据库派生的消息必须在事务提交后发** —— 用 `RagDocIndexProducer.sendAfterCommit()`（`TransactionSynchronization.afterCommit`）。无 DLX、无 publisher confirm、无手动 ack；重试靠 `spring.rabbitmq.listener.simple.retry`（3 次指数退避）。

## AI 子系统（TripAgent）

这是本项目最复杂的部分，独立于上面的 CRUD 分层。

### 请求链路

```
GET /ai/react/trip/chat?message=&threadId=&requestId=   (SSE, 免登录)
  → AiController.doChatWithReactAgent
  → TripAgentServiceImpl.chatStream          返回 SseEmitter，整体丢进 CompletableFuture.runAsync
  → ReactAgent.stream(message, RunnableConfig)   Flux<NodeOutput>
  → StreamEventRouter / ThinkingTagParser    分流成 thinking / delta
  → SseEmitter
```

相关接口：`POST /ai/react/cancel?requestId=`、`GET|DELETE /ai/react/history?threadId=`；`RagAdminController`（`/ai/rag`，调试与补偿用）提供 `GET /search`、`POST /reindex|remove/{docType}/{sourceId}`、`POST /rebuild|rebuild-missing/{docType}`、`GET /index/status`。

会话 key 恒为 `"react_agent_" + threadId`。`requestId`/`threadId` 缺省时服务端补随机 UUID。

### SSE 事件协议

| event | data | 说明 |
| --- | --- | --- |
| `thinking` | JSON `{seq,type,label,text,append}` | 同 `seq` 表示覆盖更新（幂等）；`type` ∈ `tool`/`retrieval`/`reasoning`/`node` |
| `delta` | **裸文本**增量 | 非 JSON |
| `cards` | JSON `TripCardVO[]` | 由 RAG 工具检索产出，**模型未调用工具或未命中就完全不发这个事件**，前端靠"事件缺席"判断；重发即全量覆盖 |
| `done` | JSON `TripAnswerVO{content,cards,thinking}` | 正常结束 |
| `cancelled` | 同 `done` 结构，内容为已生成的部分 | 用户停止 / 断开 / 被新流挤占 |
| `error` | 裸文本 | 真实异常 |

没有 `[DONE]` 哨兵，emitter complete 即结束流。**`wtlx-frontend/src/api/ai.js` 的注释就是协议文档，改协议必须两边同步改。**

`done` 与 `cancelled` 的区分是踩过坑的地方：流内部触发的取消在 Reactor 里呈现为 `ON_ERROR` 而非 `SignalType.CANCEL`，因此终态判断必须同时看 `signalRef == CANCEL || cancelledByStream`，否则用户主动停止会被误报成正常完成（提交 `8df9299` 修的就是这个）。

### ReactAgent 装配（`config/ReactAgentConfig.java`）

`ReactAgent.builder()` → `.model(@Qualifier("openAiChatModel"))` + `.tools(allTools)` + `.instruction(prompts/trip-planner-system.st)` + `.saver(new MemorySaver())` + `.interceptors(ConversationMemoryInterceptor)` + `.hooks(ModelCallLimitHook.runLimit(20))`。

**为什么对话走 OpenAI 兼容模式而不是 DashScope**：`spring-ai-alibaba-starter-dashscope` 1.1.2.x 硬编码旧版 REST 协议（`model`+`input`+`parameters`），qwen3.x / deepseek-v4 系列会直接 `InvalidParameter: url error`，只改 `completions-path` 也不行。所以对话模型改用 `spring-ai-starter-model-openai` 指向百炼兼容模式端点（`model: qwen3.7-flash`），而 **Embedding 仍留在 DashScope**（`text-embedding-v3` 在旧路径正常）。两个 starter 并存后 bean 必须显式消歧：`@Qualifier("openAiChatModel")` / `@Qualifier("dashscopeEmbeddingModel")`，命名规律 `openAi*` vs `dashscope*`。删掉任一 qualifier 或把对话模型换回 DashScope 都会直接起不来。

- **工具是手工登记的**：全部列在 `tools/ToolRegistration.allTools`（本地工具 + MCP 工具 `mcpToolCallbackProvider.getToolCallbacks()`）。加工具只改这一个类。注意本地工具里 `FileOperationTool` / `WebScrapingTool` / `ResourceDownloadTool` / `TerminalOperationTool` / `TerminateTool` 是 `new` 出来的、非 Spring bean，只有 `WebSearchTool` 和 `RagRetrievalTool` 是 `@Component`。`TerminalOperationTool` 会执行 `cmd.exe /c`，且 `/ai/**` 免鉴权。
- **记忆两层**：框架层 `MemorySaver`（JVM 内、按 threadId，不跨实例）；应用层 `ConversationMemoryServiceImpl`（PG，摘要 + 最近 8 轮 = 16 条）。超过 9 条用户消息触发 `SummaryCompressorImpl` 压缩（也用 `openAiChatModel`）。
- **ThreadLocal 靠不住**：整条链路跑在 Reactor / ForkJoinPool 线程上，`ConversationContextHolder` 只是兜底，可靠通道是 `RunnableConfig` metadata 里的 `conversationId`（`ConversationMemoryInterceptor.CONVERSATION_ID_KEY`）。

### RAG

配置全在 `trip-agent.rag.*`（`RagProperties`）：chunkSize 500 / overlap 80 / maxChunks 50 / embeddingBatchSize 10。

- **卡片由 RAG 工具产出，没有后端前置检索**：`retrieveKnowledge` 用模型改写后的查询词做一次宽召回（`candidate-top-k: 8` + `search-threshold: 0.5`），其中前 `search-top-k: 5` 条给模型当上下文、得分 `>= card-threshold: 0.55` 的前 `card-top-k: 4` 条组装成前端卡片——**一次检索两处产出，不重复花 embedding 与向量检索的钱**。之所以由模型改写词驱动卡片而非用户原话：多轮追问（"那住宿呢"）原话缺主题词，会命中大量只共享泛词的无关来源。阈值按实测标定（相关查询 0.62~0.78，无关查询 ≤0.47），**换 embedding 模型必须重新标定**。
- **卡片只能来自向量库 metadata，绝不能来自模型输出**：`RagCardAssembler` 负责组装，模型只决定检索时机和改写查询词。这是"卡片不产生幻觉 id"的根本保证。
- 切片：`ChineseTextSplitter`（手写中文递归切分，非 Spring AI 的 TokenTextSplitter）→ `RagDocumentFactory` 生成**确定性 UUID** 切片 id（`UUID.nameUUIDFromBytes("wtlx-rag:"+docType+":"+sourceId+":"+idx)`）→ 先按 id 删旧片再批量 embedding 入库。确定性 id + 先删后加就是幂等的全部依靠（PGVector 主键必须是 UUID，所以不能用业务 id）。`RagIndexServiceImpl` 用 `content_hash` 短路，内容没变直接跳过，不重复花 embedding 的钱。
- **来源只有站内游记（BLOG）与官方攻略（GUIDE）**，旧的静态 Markdown 知识库已按设计文档决策 B1 移除。游记发布/删除通过 MQ 自动建索引；**攻略没有任何生产者**，只能靠 `/ai/rag/rebuild*` 手工回填。
- 向量库表 `public.vector_store(id uuid, content, metadata json, embedding vector(1024))`，HNSW + COSINE_DISTANCE，`initialize-schema: true`，DDL 在 `db/schema-postgresql.sql`。索引台账在 **MySQL** 的 `rag_source_index`。
- **卡片出口挂在 `RunnableConfig` 的 metadata 上，不要改回全局注册表**：RAG 工具执行在 Reactor 线程上够不到请求级 `SseEmitter`，所以 `TripAgentServiceImpl` 每轮 `new` 一个 `CardSink`（普通对象，非 Spring bean）放进 config metadata（键 `CardSink.METADATA_KEY`），工具用 `ToolContextHelper.getConfig(ToolContext)` 取回 config 再取出 sink——框架会给 `MethodToolCallback` 注入 `_AGENT_CONFIG_`，这条路是通的。`CardSink` 按 `sourceType:sourceId` 去重保留最高分，每次变化后**广播全量集合**（`cards` 是覆盖语义，发全量即幂等）。出口随请求走，就不需要按 `conversationId` 注册/注销的生命周期管理，并发会话天然隔离。注意它挂在 metadata 上依赖 `MemorySaver` 不做序列化，换成持久化 Saver 时必须改成传一个可序列化标识再反查。

### 取消机制（`infrastructure/cancel/`）

前端生成 `requestId` 放在 SSE query 上 → 点停止时 `POST /ai/react/cancel`（可打到任意实例）。

- `GenerationCancelService.cancel()`：先查本地 `GenerationRegistry`，**无论如何**都写 Redis 标记 `ai:generation:cancel:flag:{id}`（TTL 60s）并向 Pub/Sub 主题广播；每个实例启动时 `CancelBroadcastListener` 订阅，只有本地持有该 requestId 的实例才动作。返回 `CANCELLED`/`FORWARDED`/`REJECTED`。requestId 用 `SAFE_ID = [A-Za-z0-9_-]{8,64}` 校验，防 Redis key 注入。
- 中断手段是保存 `Disposable` 并 `dispose()`（切断到 DashScope 的 HTTP 连接），不是协议级 stop。`doOnNext` 每 chunk 查本地标记、每 2s 查一次 Redis 兜底。
- **已知限制**：非流式节点用 `Mono.fromFuture`，无法中断 —— 阻塞工具（WebSearch / Terminal 等）执行期间没有 chunk，取消要等下一个 Flux 事件才生效。

### MCP

`mcp-servers.json`（`spring.ai.mcp.client.stdio.servers-configuration`）配了两个 stdio server：`baidu-map`（npx）和 `yu-image-search-mcp-server`（`java -jar image-search-mcp-server/target/...jar`）。**jar 路径是相对路径**，所以 JVM 工作目录必须是仓库根，否则 MCP 起不来。

## 前端架构（`wtlx-frontend/`）

Vue 3.5 + Vite 6 + Vue Router 4（**hash 模式**）+ Pinia + Element Plus，`<script setup>` 组合式 API。

- **两套网络出口，不能混**：业务接口走 `src/api/request.js`（axios 实例，`baseURL: '/api'`，拦截器已校验 `success` 并处理 401，调用方取 `res.data`；上传用导出的 `uploadRequest`，超时 0）；AI 接口走 `src/api/ai.js`（**裸 `fetch` / `EventSource`**，因为 AI 返回裸对象、没有 `success` 包装，复用 axios 拦截器会误判为失败）。
- 路由表已冻结在 `src/router/index.js`（视图文件名 = 路由 name，PascalCase，懒加载）；页面标题由 `afterEach` 设置。`MIGRATION_SPEC.md` 是原 Vue2 多页 → Vue3 的迁移规范，含 Element UI → Element Plus 的 API/图标对照表，改动老页面时可查。
- **`router/index.js` 里有一段 `if (import.meta.env.DEV) return true` 的临时放行**：`npm run dev` 时完全跳过登录校验，生产构建不受影响。预览完需要自行删除。
- AI 会话列表**没有后端接口**，存在 localStorage 的 `ai_sessions` 里（并兼容迁移旧的单会话 `ai_chat_id`）。
- 样式令牌在 `src/styles/variables.css`（`--wt-*`）+ `src/styles/common.css`（`.wt-page` / `.wt-card` / `.wt-tabs` 等），组件内不要再写死颜色/间距。页面专属样式一律收进 SFC `<style scoped>`（SPA 防串扰），覆盖 Element Plus 内部类用 `:deep()`。
- AI 页的消息区由 `components/chat/ThinkingPanel.vue`（思考折叠面板）和 `components/chat/BlogCardGrid.vue`（卡片网格，空数组不渲染）组成。

## 其他

- 仓库**没有配置 git remote**，`git pull`/`push` 不可用，合并只能在本地 `git merge --no-ff`。
- 根目录有一个删不掉的 0 字节 `nul` 文件，是 Windows 下的历史遗留噪音，`git status` 里会一直显示为未跟踪，不要管它。
- 只有一份 `application.yaml`，没有 profile、没有 `application-dev/prod.yml`；Redis / RabbitMQ / PostgreSQL / xxl-job 都硬编码在 `192.168.150.101`，各类 key 明文写在 YAML 里（AI key 用 `${DASHSCOPE_API_KEY}` 占位）。Redis 还有个坑：`RedissonConfig` 里的地址是**另外硬编码**的，不读 `application.yaml`，改 Redis 地址要改两处。
- `docs/` 下四份设计文档：`旅行Agent重构设计方案.md` 是 AI 链路的权威设计（第七节决策表 A–E 是已确认结论）；`令牌桶流量控制方案总结.md` 讲限流选型；`项目时序图.md` / `项目类图.md` 写在包结构重构之前，包名已过时，只能当业务语义参考。
