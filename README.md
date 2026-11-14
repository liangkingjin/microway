# Microway · 微途旅行

> 面向大学生的旅游平台：游记分享 · 攻略查找 · 行程规划 · AI 智能导览

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.5-42b883)
![Element Plus](https://img.shields.io/badge/Element%20Plus-2.9-409eff)
![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2-ff6a00)
![License](https://img.shields.io/badge/license-MIT-blue)

## 项目简介

Microway（微途旅行）是一个前后端分离的大学生旅游平台。用户可以浏览热门目的地、查看官方攻略、分享游记、点赞评论互动，还能与内置的 **AI 旅行助手** 对话，由大模型结合站内游记与攻略（RAG）生成个性化行程方案。

## 功能特性

### 🏝️ 首页 · 内容分发
- 轮播图 + 金刚区入口（美食 / 旅游清单 / 找攻略 / 看游记 / 当地游 / 打卡发现 / 组团匹配游 / 推荐榜 / 酒店 / AI 规划）
- 热门目的地推荐（TOP 榜单）、关注 / 推荐 / 发现多 Tab 信息流

### 🤖 AI 旅行助手
- 基于 **Spring AI Alibaba（ReactAgent）** 的多轮对话，SSE 流式输出、思考过程可视化
- 大模型可自主调用站内工具（RAG 检索、联网搜索等），支持中途停止生成
- 回答内嵌「参考游记与攻略」卡片，点击可跳转详情；支持多会话管理与历史记录
- 对话结果可保存为旅行计划：界面显示计划名称与 ID，提示可在「旅游清单」中查看与继续编辑，并提供「查看行程与路线」入口

### 📖 攻略与游记
- **找攻略**：按城市（北京 / 上海 / 川渝 / 云南 / 海南……）浏览官方攻略
- **看游记**：UGC 游记瀑布流，支持图文发布、视频发布
- **游记详情**：点赞（Redis 计数 + MQ 异步落库）、评论区（支持楼中楼回复、配图、等级展示）

### 🗺️ 行程规划（分类：行程管理）
- **我的旅行计划列表页**：以卡片形式管理多个计划，展示计划名称、起止日期、「N天N晚」时长，部分卡片显示金额；提供新建入口
- **计划详情页**：按天展示行程，含「行程 / 旅行账单 / 行李清单」页签；「当日路线」地图给出预计时长、里程与编号点位，行程按时间段列出交通、景点等条目
- **AI 生成 → 计划**：AI 对话产出的方案可保存为旅行计划，并跳转查看行程与路线
- 计划卡片金额字段的来源、账单 / 清单页签的现有完成度：**待确认**

### 🔔 其他
- 短视频浏览（上下滑切换、点赞）、消息通知、个人主页、地图导航（百度地图 GL）、扫码登录等

## 界面预览

> 以下 7 张截图均为最新一批移动端界面，按功能模块归类；旧版截图已从仓库移除。

| 内容分发 · 首页推荐 | AI 旅行助手 · 对话生成计划 |
| --- | --- |
| ![首页](docs/screenshots/ui-home.png) | ![AI 旅行助手](docs/screenshots/ui-ai-chat.png) |

| 攻略与游记 · 找攻略 | 攻略与游记 · 看游记 |
| --- | --- |
| ![找攻略](docs/screenshots/ui-guides.png) | ![看游记](docs/screenshots/ui-blogs.jpg) |

| 攻略与游记 · 游记详情 | 行程管理 · 我的旅行计划列表页 |
| --- | --- |
| ![游记详情](docs/screenshots/ui-blog-detail.png) | ![我的旅行计划列表](docs/screenshots/ui-trip-plan-list.png) |

### 行程管理 · 计划详情与当日路线地图

![计划详情](docs/screenshots/ui-trip-plan-detail.png)

### 截图归类与待确认项

| 分类名称 | 对应界面 | 截图文件 |
| --- | --- | --- |
| 内容分发 · 首页推荐 | 首页（推荐 / 关注、金刚区、为你推荐） | `ui-home.png` |
| AI 旅行助手 · 对话生成计划 | 对话页（方案保存为计划、查看行程与路线） | `ui-ai-chat.png` |
| 攻略与游记 · 找攻略 | 按城市浏览官方攻略 | `ui-guides.png` |
| 攻略与游记 · 看游记 | 游记瀑布流 | `ui-blogs.jpg` |
| 攻略与游记 · 游记详情 | 点赞、评论与楼中楼回复 | `ui-blog-detail.png` |
| **行程管理 · 我的旅行计划列表页** | 多计划卡片、日期 / 天数 / 金额、新建入口 | `ui-trip-plan-list.png` |
| 行程管理 · 计划详情与当日路线地图 | 行程 / 账单 / 清单页签、当日路线地图 | `ui-trip-plan-detail.png` |

- 截图中的计划名称、日期、金额是否为演示数据：**待确认**
- 「旅行账单」「行李清单」页签与计划卡片金额字段的现有完成度：**待确认**
- 未在本次截图出现的功能（短视频、消息通知、扫码登录等）是否仍属于当前版本能力：**待确认**
- 线上部署地址与可访问状态：**待确认**

## 技术栈

### 后端
| 分类 | 技术 |
| --- | --- |
| 基础框架 | Java 21 · Spring Boot 3.5 · Spring MVC · MyBatis-Plus |
| AI 能力 | Spring AI Alibaba 1.1（ReactAgent / Graph、Hook、Interceptor）、Spring AI OpenAI 兼容接入（阿里云百炼 DashScope）、MCP Client |
| RAG | pgvector（PostgreSQL）+ HNSW 向量索引 · DashScope text-embedding-v3 · 自研中文分片（chunk 500 / overlap 80） |
| 存储 | MySQL（业务库）· PostgreSQL（向量库）· Redis / Redisson（登录态、点赞计数、 feed 流） |
| 中间件 | RabbitMQ（点赞落库 / RAG 索引异步化）· XXL-Job（点赞对账） |
| 文档 | Knife4j（OpenAPI 3 / Swagger） |

### 前端
| 分类 | 技术 |
| --- | --- |
| 核心框架 | Vue 3.5（Composition API / `<script setup>`）· Vue Router 4（Hash 模式）· Pinia |
| UI 与构建 | Element Plus 2.9 · Vite 6 · Axios |
| 其他 | 百度地图 GL SDK · SSE 流式渲染 · 自定义设计令牌（`--wt-*`）体系 |

## 项目结构

```
wtlx/
├── src/main/java/com/wtlx/        # 后端源码
│   ├── controller/                 # 接口层（博客 / 评论 / 点赞 / AI / 地图…）
│   ├── service/                    # 业务层
│   ├── config/                     # ReactAgent、数据源、MVC 等配置
│   ├── infrastructure/             # 基础设施（RAG、MQ、取消令牌、拦截器…）
│   │   ├── rag/                    # 切片 / 打标 / 检索 / 卡片组装
│   │   ├── mq/                     # RabbitMQ 生产者与消费者
│   │   └── cancel/                 # AI 流式生成取消机制
│   └── tools/                      # Agent 工具注册
├── src/main/resources/             # 配置与提示词
├── wtlx-frontend/                  # 前端工程（Vue 3 + Vite）
│   ├── src/views/                  # 页面（40+ 视图）
│   ├── src/components/             # 公共组件
│   └── src/styles/                 # 设计令牌与全局样式
└── docs/                           # 文档与素材
    ├── screenshots/                # 7 张最新移动端界面截图
    └── promo/                      # 推广素材
```

## 快速开始

### 环境要求
- JDK 21+ / Maven 3.9+
- Node.js 18+（推荐 20+）
- MySQL 8 · Redis · RabbitMQ · PostgreSQL（含 pgvector 扩展）

### 后端启动
```bash
# 1. 配置数据库连接：编辑 src/main/resources/application.yaml
# 2. 配置大模型 Key（环境变量）
export DASHSCOPE_API_KEY=sk-xxxx

# 3. 启动（默认端口 9528）
mvn spring-boot:run
```

### 前端启动
```bash
cd wtlx-frontend
npm install
npm run dev        # 开发环境，默认 http://localhost:5173
npm run build      # 生产构建，产物输出 dist/
```

## 接口文档

后端启动后访问：`http://localhost:9528/doc.html`（Knife4j）

## License

[MIT](LICENSE)
