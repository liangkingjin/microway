# 旅行 Agent 功能重构设计方案

> 分支：`refactor/trip-agent-rag-output`（自 `master`/`df81b2c` 拉出）
> 状态：**设计阶段，待确认后进入编码**
> 范围：后端 AI 链路（对话输出 / RAG / MQ 同步）+ 前端 `AI.vue` 会话页

---

## 一、需求拆解：现状 → 目标

### 1.1 现状盘点（代码级）

| 模块 | 现状 | 关键文件 |
|---|---|---|
| 对话输出 | `ReactAgent.stream()` 只取 `AGENT_MODEL_STREAMING` 的增量，累积成 `answer` 字符串；SSE 仅有 `delta` / `done` / `cancelled` / `error` 四类事件，`done` 携带整轮纯文本 | `TripAgentServiceImpl` |
| 输出产物 | 工具集含 `PDFGenerationTool`（`returnDirect=true`，落盘 PDF）、`MarkdownGenerationTool`（落盘 md），系统提示词要求"生成图文并茂的攻略" | `ToolRegistration`、`PDFGenerationTool`、`prompts/trip-planner-system.st` |
| RAG 检索源 | classpath 下 3 个静态 Markdown（`旅游常见问题和回答 - 准备/国内/国外篇.md`），启动时一次性灌入向量库，"表里有数据就跳过"，之后永不更新 | `TripDocumentLoader`、`TripKnowledgeInitializer` |
| 检索工具 | `topK=3`、`similarityThreshold=0.5`，返回拼接文本，无来源标识、无过滤 | `RagRetrievalTool` |
| 向量库 | PGVector，`vector_store(id uuid, content, metadata json, embedding vector(1024))`，HNSW + COSINE_DISTANCE | `schema-postgresql.sql`、`application.yaml` |
| 数据同步 | **无**。游记/攻略新增或修改后不会进入向量库 | — |
| 前端渲染 | 单条消息 = `{content, isUser, time, cancelled}`，简易正则 markdown → 单一气泡，无思考过程、无卡片 | `AI.vue`（1067 行） |

### 1.2 目标与差异（改动项）

| # | 维度 | 现状 | 目标 | 改动级别 |
|---|---|---|---|---|
| 1 | 输出形态 | 纯文本气泡 + 落盘 PDF | **正文（结构化）+ 思考步骤**分离返回，不再生成 PDF | 后端输出链路重写 + 前端消息模型扩展 |
| 2 | 检索源 | 静态 md 文档 | **站内已发布游记（`tb_blog`）+ 官方攻略（`guides`）** | 新增切片/向量化/索引服务 |
| 3 | 检索能力 | 全库无过滤检索 | 按来源类型过滤、返回来源元信息与相似度分数 | 新增检索服务 + 工具改造 |
| 4 | 数据同步 | 无 | 游记发布 → MQ → 自动切片入库（幂等/重试/更新） | 新增生产者 + 消费者 + 索引表 |
| 5 | 前端展示 | 单一文本气泡 | **文字正文 + 游记卡片**混合；无命中则不渲染卡片区 | 前端消息区改造 |

### 1.3 影响范围

- **后端新增包**：`com.wtlx.rag`（切片/索引/检索）、`com.wtlx.mq`（消息与消费者）；`com.wtlx.entity.rag`、`com.wtlx.entity.dto.ai`
- **后端修改**：`TripAgentServiceImpl`（分流）、`ReactAgentConfig`（提示词）、`ToolRegistration`（去 PDF）、`RabbitMQConfig`（新交换机/队列）、`BlogServiceImpl`（发消息）、`application.yaml`（RAG 参数）
- **后端移除**：`PDFGenerationTool`（及 iText 依赖，视决策 D）
- **前端修改**：`src/api/ai.js`、`src/views/AI.vue`，新增 `src/components/chat/*` 子组件
- **数据**：PG `vector_db` 新增 1 张索引表；`vector_store` 复用（按 metadata 过滤，不新建表）
- **不改动**：取消机制（GenerationRegistry/CancelService）、会话记忆（ConversationMemory*）、登录鉴权、游记/攻略自身 CRUD 与展示页

### 1.4 技术风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| **双数据源**：业务数据在 MySQL，向量库在 PG | 切片服务需跨库读写 | 切片服务只依赖 MyBatis（MySQL 主库）+ Spring AI `VectorStore`（PG 独立数据源），不手写 PG SQL |
| **PGVector 主键必须是 UUID**，`vector_store.id` 为 `uuid` 列 | 自定义业务 id 会被 `UUID.fromString` 拒绝 | 切片 id 用 `UUID.nameUUIDFromBytes(("blog:123:3").getBytes())`，**确定性 UUID** → 天然幂等，可重复写入覆盖 |
| **DashScope embedding 单批上限 10 条** | 长游记切片数十片会超限 | 复用现有分批逻辑（批大小 10），切片服务内统一封装；超长内容限制最大切片数（默认 50） |
| **模型不遵守输出格式** | 正文/思考分离失效 | 兜底：整轮未出现标记时，全部 model streaming 归为正文；思考区为空则不渲染该区 |
| **旧文档向量与新数据混在同一张表** | 检索串味 | metadata 增加 `docType` 字段，检索时必带过滤；旧文档数据由开关决定是否清理 |
| **MQ 消息与数据库事务不一致** | 消息发出但事务回滚 → 索引脏数据 | 生产者放在事务提交后（`TransactionSynchronization.afterCommit`）；消费者侧再幂等校验一次 |
| **重复消费 / 并发消费** | 重复 embedding 调用（费钱） | 索引表 `rag_source_index` 记录 `content_hash`，哈希一致且已成功则直接跳过 |
| **SSE 事件协议变更** | 前端解析失败 | `done`/`cancelled` 改 JSON 时前端做 try/catch 兜底（非 JSON 按纯文本处理），保证灰度期不白屏 |

---

## 二、整体架构设计

### 2.1 分层

```
┌─ 前端 AI.vue ────────────────────────────────────────────┐
│  ChatMessage 组件：思考区（可折叠）/ 正文区 / 卡片网格    │
└─────────────── SSE(thinking·delta·cards·done) ───────────┘
                            │
┌─ 后端 ───────────────────────────────────────────────────┐
│ Controller  AiController      仅做参数校验与 SSE 返回     │
│ Orchestrator TripAgentService  编排：流订阅 → 分流 → 推送 │
│ Splitter    StreamEventRouter  OutputType 判定 + 标记解析 │
│ Agent       ReactAgent + Tools（RAG 检索 / 搜索 / …）     │
│ RAG 检索侧  RagSearchService   相似度检索 + 来源过滤      │
│ RAG 写入侧  RagIndexService    切片 → 向量化 → 入库       │
│ 同步        RagDocIndexConsumer ← MQ（游记发布事件）      │
└──────────────────────────────────────────────────────────┘
        │                                    │
   PG vector_store                      MySQL tb_blog / guides
```

**核心思路**：正文与思考的分离放在**服务端分流器**里做，前端只做三区渲染；卡片来源由**后端独立检索**决定（不依赖模型输出 id），保证"无命中就不渲染卡片"是确定性的。

### 2.2 后端模块清单

| 类型 | 类 | 职责 |
|---|---|---|
| 新增 | `rag/ChineseTextSplitter` | 中文友好递归切片（段→句→字，默认 500 字/片，重叠 80 字） |
| 新增 | `rag/RagDocumentFactory` | 业务实体（Blog / Guides）→ `Document` 列表，写 metadata、生成确定性 UUID |
| 新增 | `rag/RagIndexService` | 单个来源的「删除旧片 → 切片 → 分批向量化 → 入库 → 更新索引」 |
| 新增 | `rag/RagRebuildService` | 全量回填（启动时可选 / 管理接口触发） |
| 新增 | `rag/RagSearchService` | `search(query, topK, threshold, docTypes)` → `List<RagHit>` |
| 新增 | `rag/RagCardAssembler` | `RagHit` → 前端卡片 VO（同 sourceId 去重，保留最高分） |
| 新增 | `service/StreamEventRouter` | 流式事件分流（正文 / 思考 / 卡片） |
| 新增 | `mq/RagDocIndexMessage` | MQ 消息体 |
| 新增 | `mq/RagDocIndexConsumer` | 消费者：幂等校验 + 索引 + 失败标记 |
| 新增 | `entity/RagSourceIndex` + `mapper` | PG 索引表实体（MyBatis，挂在 ai 数据源） |
| 新增 | `controller/RagAdminController` | 检索调试 / 手动重建 / 失败重投 |
| 修改 | `TripAgentServiceImpl` | 接入分流器，推送新事件，`done` 改结构化 JSON |
| 修改 | `RagRetrievalTool` | 改为调用 `RagSearchService`，输出带来源标题 |
| 修改 | `ToolRegistration` | 移除 PDF 工具 |
| 修改 | `RabbitMQConfig` / `RabbitMQConstants` | 新增交换机、队列、路由键 |
| 修改 | `BlogServiceImpl` | 发布成功后事务提交 → 发消息 |
| 修改 | `prompts/trip-planner-system.st` | 增加"思考/正文"输出契约 |
| 移除 | `PDFGenerationTool` | 不再生成 PDF |

### 2.3 前端模块清单

| 类型 | 文件 | 职责 |
|---|---|---|
| 新增 | `components/chat/ThinkingPanel.vue` | 思考步骤折叠面板 |
| 新增 | `components/chat/BlogCardGrid.vue` | 游记卡片网格（空数组不渲染） |

> 实现说明：原计划再抽一层 `ChatMessage.vue` 消息容器。编码时为把改动限制在局部，
> 未引入该容器——AI.vue 的消息行结构保持原样，只在其内部挂载上面两个子组件。
| 修改 | `api/ai.js` | 事件协议注释与类型约定 |
| 修改 | `views/AI.vue` | 消息模型扩展 + SSE 解析 + 组装子组件 |

---

## 三、关键数据结构

### 3.1 切片 Document 与 metadata 约定

```java
// 确定性 id：同一来源同一片永远同一个 UUID，重放即覆盖，天然幂等
UUID chunkId = UUID.nameUUIDFromBytes((docType + ":" + sourceId + ":" + chunkIndex).getBytes(UTF_8));
```

| metadata key | 说明 | 示例 |
|---|---|---|
| `docType` | 来源类型：**BLOG**（游记）/ **GUIDE**（官方攻略）/ **DOC**（旧静态文档，待清理） | `BLOG` |
| `sourceId` | 业务主键（字符串） | `1024` |
| `title` | 标题，`BLOG.title` / `GUIDE.title` | `云南七日游` |
| `authorId` | 游记作者 id（GUIDE 为空） | `7` |
| `provinceId` | 攻略所属省份（GUIDE） | `12` |
| `cover` | 封面图（BLOG 取 `images` 首图；GUIDE 取 `image`） | `https://…/a.jpg` |
| `liked` | 点赞数（用于卡片展示与排序） | `23` |
| `chunkIndex` / `chunkTotal` | 片序 / 总片数 | `0` / `5` |
| `contentHash` | 该来源内容的哈希，用于变更判定 | `a1b2c3…` |
| `sourceUpdatedAt` | 源数据更新时间 | `2026-09-30T10:00:00` |

### 3.2 索引表（MySQL 业务库 `wtlx`）

> 实现说明：设计阶段计划放在 PG，编码时改到 MySQL。原因——MyBatis 主数据源即 MySQL，
> 索引表若放 PG 需要再配一套 `SqlSessionFactory`；它只是元数据，与主业务同库还能和
> `tb_blog` / `guides` 在同一事务语义下维护。建表脚本：`db/rag-source-index-mysql.sql`。

```sql
CREATE TABLE IF NOT EXISTS rag_source_index (
    id            BIGSERIAL PRIMARY KEY,
    doc_type      VARCHAR(16)  NOT NULL,          -- BLOG / GUIDE
    source_id     VARCHAR(64)  NOT NULL,
    content_hash  VARCHAR(64)  NOT NULL,          -- 内容指纹，变更判定
    chunk_count   INTEGER      NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING/SUCCESS/FAILED
    error_msg     TEXT,
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (doc_type, source_id)
);
CREATE INDEX IF NOT EXISTS idx_rag_source_status ON rag_source_index (status, updated_at);
```

### 3.3 SSE 事件协议（扩展后）

| event | data 格式 | 时机 | 兼容说明 |
|---|---|---|---|
| `thinking` | JSON：`{"seq":1,"type":"tool","label":"检索站内游记","text":"命中 3 篇"}` | 工具调用/检索/推理发生即推 | 新增 |
| `delta` | **裸文本增量**（现有格式不变） | 正文字段生成中 | 保持兼容 |
| `cards` | JSON 数组：`[{sourceType,sourceId,title,cover,authorName,liked,score}]` | 检索完成即推；无命中则不发 | 新增 |
| `done` | JSON：`{"content":"…","cards":[...],"thinking":[...]}` | 正常结束 | **变更**（原为裸文本，前端兜底兼容） |
| `cancelled` | JSON：`{"content":"…","thinking":[...]}` | 用户停止/断开/新流挤占 | **变更**（同上） |
| `error` | 文本 | 异常 | 保持兼容 |

`thinking.type` 取值：`tool`（工具调用）、`retrieval`（检索）、`reasoning`（模型推理文本）、`node`（图节点）。

### 3.4 结构化结果 DTO

```java
public class TripAnswerVO {
    private String content;              // 最终结论正文（markdown）
    private List<TripCardVO> cards;      // 关联游记卡片，无命中为空集合
    private List<ThinkingStepVO> thinking;
}

public class TripCardVO {
    private String sourceType;           // BLOG / GUIDE
    private Long   sourceId;
    private String title;
    private String cover;
    private String authorName;
    private Integer liked;
    private String  summary;             // 命中切片摘要，截取 80 字
    private Double  score;               // 相似度
}

public class ThinkingStepVO {
    private Integer seq;
    private String  type;                // tool / retrieval / reasoning / node
    private String  label;
    private String  text;
}
```

### 3.5 前端消息模型

```js
{
  isUser: false,
  time: 1759...,
  content: '',        // 正文（流式追加）
  thinking: [],       // 思考步骤（流式追加，默认折叠）
  cards: [],          // 游记卡片（一次性）
  cancelled: false
}
```

---

## 四、接口定义

### 4.1 内部服务接口（Java）

```java
public interface RagIndexService {
    /** 单个来源：切片 → 向量化 → 入库；contentHash 未变且已成功则跳过 */
    RagIndexResult index(String docType, Long sourceId);
    /** 删除某来源全部切片与索引记录 */
    void remove(String docType, Long sourceId);
}

public interface RagSearchService {
    /** 相似度检索；docTypes 为空表示不过滤 */
    List<RagHit> search(String query, int topK, double threshold, List<String> docTypes);
}

public interface RagRebuildService {
    /** 全量回填：返回 (成功数, 失败数)，分页遍历避免长事务 */
    RagRebuildResult rebuildAll(String docType);
}
```

`RagHit`：`{String text, double score, Map<String,Object> metadata}`

### 4.2 REST 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/ai/react/trip/chat` | 保持不变（新增事件） |
| POST | `/ai/react/cancel` | 保持不变 |
| GET/DELETE | `/ai/react/history` | 保持不变 |
| GET | `/ai/rag/search?query=&topK=&threshold=&docTypes=BLOG,GUIDE` | 检索调试，返回 `RagHit` 列表 |
| POST | `/ai/rag/reindex/{docType}/{sourceId}` | 单个来源重建（补偿用） |
| POST | `/ai/rag/rebuild/{docType}` | 全量回填 |
| GET | `/ai/rag/index/status?docType=&sourceId=` | 查询索引状态 |

### 4.3 MQ 消息契约

```java
public class RagDocIndexMessage implements Serializable {
    private String docType;      // BLOG / GUIDE
    private Long   sourceId;
    private String contentHash;  // 生产者快照，消费者二次校验
    private String eventType;    // PUBLISHED / UPDATED / DELETED
    private Long   occurredAt;
}
```

- Exchange：`rag.doc.exchange`（topic，durable）
- Queue：`rag.doc.index.queue`（durable）
- Routing key：`rag.doc.index`
- 重试：沿用 `spring.rabbitmq.listener.simple.retry`（3 次，指数退避）；最终失败写 `status=FAILED` + `error_msg`，由管理接口补偿

### 4.4 前端调用

`api/ai.js` 保持 `openChatStream` 签名不变；新增 `fetchRagStatus`（可选）。

---

## 五、前端渲染方案

1. **思考区**：默认折叠为一行「已思考 N 步」，点击展开为步骤列表；生成中显示当前步骤。无步骤则整块不渲染。
2. **正文区**：沿用现有 markdown 渲染（`renderMarkdown`），流式追加。
3. **卡片区**：`cards.length > 0` 才渲染，横向滚动卡片（移动端）/ 网格（桌面端）；卡片含封面、标题、作者、点赞数；点击跳 `/blog-detail?id={sourceId}`。
4. **未命中**：后端不发 `cards` 事件，前端 `cards` 保持空数组 → 不渲染卡片区，正文照常显示。
5. 组件拆分遵循既有规范：`components/chat/` + PascalCase 命名 + BEM 样式 + `--wt-*` 令牌。

---

## 六、分阶段实施计划

| 阶段 | 内容 | 产出 | 验收标准 |
|---|---|---|---|
| **P0 设计** | 现状梳理、方案设计、接口定义 | 本文档 | 设计确认 |
| **P1 输出形态改造** | 分流器、SSE 新事件、去 PDF、提示词契约 | 后端可推 `thinking`/`delta`/`cards`；`done` 结构化 | 单元级：模拟流验证三类事件齐全；PDF 工具不再注册 |
| **P2 RAG 基础** | 切片器、Document 工厂、索引服务、检索服务、索引表 DDL | `RagIndexService` / `RagSearchService` 可用 | 单条游记能切片入库，`/ai/rag/search` 能召回 |
| **P3 检索接入** | `RagRetrievalTool` 改造、卡片装配、`cards` 事件产出 | 检索来源切换为 BLOG/GUIDE | 提问能召回站内内容并产出卡片 |
| **P4 数据同步** | MQ 生产者（游记发布）、消费者、幂等/重试、全量回填 | 发布游记 → 自动入库 | 发布后索引表 SUCCESS；重复消费不重复 embedding；失败可补偿 |
| **P5 前端** | 消息模型 + 三个子组件 + SSE 解析 | 三区渲染 | 有卡片显示卡片，无卡片不渲染卡片区 |
| **P6 联调与收尾** | 端到端验证、构建、合并 master | 完整链路 | 后端编译通过 + 前端 `vite build` 通过 + 流式与卡片真实可见 |

---

## 七、决策结论（已确认）

| 编号 | 决策点 | 结论 |
|---|---|---|
| A | 正文/思考分离方式 | **A1 标记分流 + 兜底**：`<thinking>` 块内为思考，块外为正文；整轮无标记时全部归正文 |
| B | 旧静态 Markdown 知识 | **B1 移除**：删除 `TripDocumentLoader`、`TripKnowledgeInitializer` 与 `document/*.md` |
| C | 卡片来源 | **C1 后端独立检索** → 已由 **C2 模型检索驱动** 修订，见下 |
| D | 文件生成类工具 | **D2 两者都删**：`PDFGenerationTool` / `MarkdownGenerationTool` 下线，iText 依赖一并移除 |
| E | 历史消息是否持久化思考与卡片 | **E1 只存正文**：`conversation_message` 结构不变，历史回放时思考与卡片为空集合 |

### C 的修订：C1 → C2 模型检索驱动

**C1（初版）**：卡片由后端按用户原话做一次独立检索，不依赖模型输出 id。

**C2（现行）**：卡片改由 `RagRetrievalTool` 在一次宽召回中与模型上下文**同源产出**——工具用模型改写后的查询词检索
`candidate-top-k` 条，其中前 `search-top-k` 条留给模型当上下文，得分 `>= card-threshold` 的前 `card-top-k` 条
经 `RagCardAssembler` 组装后写入 `CardSink` 推给前端。

修订理由：

1. **召回更准**。C1 用用户原话检索，多轮追问（如"那住宿呢"）缺主题词，会命中大量只共享泛词的无关来源；
   模型改写后的查询词自带对话上下文。
2. **省一半检索成本**。C1 下每轮要检索两次（原话一次、工具一次）并做两次 embedding，C2 合并为一次。
3. **阈值只剩一套标定量**。C1 的卡片阈值与工具阈值分属两条链路，换 embedding 模型时要分别标定。

代价与边界：**卡片产出绑定模型调用率**——模型整轮未调用检索工具时没有卡片（寒暄类问题本就不应有卡片）。
若实测产出率不达标，可再把 C1 以"仅随 `done` 兜底、不提前广播"的形式加回。

实现要点：卡片出口（`CardSink`）挂在 `RunnableConfig` 的 metadata 上随请求下发给工具，
工具用 `ToolContextHelper.getConfig(ToolContext)` 取回——**不要改用全局注册表按 `conversationId` 寻址**，
出口随请求走就不需要注册/注销的生命周期管理。

前端协议不变：`cards` 事件仍是全量覆盖语义，未命中则完全不发该事件。
