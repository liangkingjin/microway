# 微途旅行前端重构工程（wtlx-frontend）

> 原 39 个 Vue2 CDN + Element UI 静态多页 → Vue 3 + Vite + Vue Router + Pinia + Element Plus 单页应用（SPA）
> 位置：`D:\JavaProject\IdeaProject\TripAgent\wtlx-frontend`（建议 A，与后端源码同仓库平级）

---

## 一、结论先行（项目总览）

| 维度 | 结论 |
| --- | --- |
| 技术栈 | Vue 3.5 + Vite 6 + Vue Router 4（**hash 模式**）+ Pinia 2 + Element Plus 2.9 + axios 1.7 |
| 工程形态 | SPA 单页应用，`npm run build` 产出 `dist/`，可直接被 nginx 静态托管（**无需修改任何 nginx 配置**，hash 路由天然兼容） |
| 页面覆盖 | 36 个有效页面全部迁移（跳过：`test.html` 测试页、`页面框架.html` 模板页、`yourpage.html` 0 字节空文件） |
| 业务后端 | axios 统一实例 `src/api/request.js`：`/api` 前缀（nginx 反代 127.0.0.1:9528 并 rewrite 前缀）、2s 超时、`authorization` 头带 token、`success` 字段校验、401 自动跳登录 |
| AI 后端 | TripAgent `http://localhost:9999/api`，fetch/EventSource **直连**（独立封装 `src/api/ai.js`，不走 axios 拦截器），无鉴权、CORS 全放开 |
| 登录鉴权 | 路由守卫统一处理：未登录访问需鉴权页 → 跳 `/login`（原多页各页 mounted 手写判断已收敛） |
| 会话管理 | AI 会话列表无后端接口 → localStorage 维护（`ai_sessions`），兼容旧 `ai_chat_id` 单会话记录自动迁移 |
| 构建验证 | ✅ `npm run build` 通过（36 视图全部编译，dist 7.35s 产出）；✅ `vite preview` HTTP 冒烟通过（SPA 外壳 + 静态资源 200）；浏览器可视化渲染检查因本环境浏览器自动化不可用未执行 |

---

## 二、项目结构

```
wtlx-frontend/
├── index.html                  # SPA 入口（挂载 #app）
├── package.json
├── vite.config.js              # dev proxy: /api → http://localhost:8080（nginx）；构建输出 dist/
├── MIGRATION_SPEC.md           # 页面迁移规范（Vue2→Vue3 适配铁律、路由表、自检清单）
├── public/                     # 静态资源（构建时原样拷贝到 dist 根）
│   ├── imgs/                   # 全部图片素材（原 /imgs 全量迁移）
│   ├── video/                  # 视频素材（原目录为空，视频均为后端 URL）
│   └── favicon.ico
└── src/
    ├── main.js                 # 入口：Element Plus 全量 + 中文 locale + 全部图标全局注册 + Pinia + Router
    ├── App.vue                 # 根组件（router-view）
    ├── router/index.js         # 路由表（36 条路由 + 404 兜底 + 登录守卫 + 标题设置）
    ├── api/
    │   ├── request.js          # 业务后端 axios 实例（/api、2s 超时、token 头、success 校验、401 跳登录）
    │   ├── user.js             # /user/login、/user/code
    │   └── ai.js               # AI 后端 9999：fetchHistory / deleteHistory / openChatStream(SSE)
    ├── stores/
    │   └── auth.js             # Pinia 登录态（token 存 sessionStorage）
    ├── components/
    │   ├── HeaderBar.vue       # 标准返回头部（原各页内嵌 div.header 结构的统一组件）
    │   └── FootBar.vue         # 底部导航：首页/行程/我的（原 js/footer.js 全局组件迁移）
    ├── styles/
    │   ├── main.css            # 全局基础样式（原 css/main.css：foot 导航、el-tabs 等）
    │   └── header.css          # 公共头部样式（原 css/header.css）
    ├── utils/index.js          # getUrlParam（hash 路由 query 适配）/ formatPrice（原 common.js util）
    └── views/                  # 36 个页面视图（每个 = 原 html 模板 + 脚本 + 页面样式 scoped）
        ├── Index.vue / Login.vue / Login2.vue / AI.vue          # 种子页（先完成，冻结迁移模式）
        ├── BlogDetail.vue / BlogEdit.vue / Info.vue / InfoEdit.vue / OtherInfo.vue
        ├── Top.vue / TopDetail.vue / Watch.vue / VideoEdit.vue
        ├── Jingdian.vue / Map.vue / ScenicDetail.vue / TravelPlan.vue
        ├── LocalTour.vue / GroupTour.vue / ZhaoGonglue.vue / GonglueDetail.vue
        ├── HotelRoomDetail.vue / HotelOrderDetail.vue / TicketOrderDetail.vue
        ├── ShopDetail.vue / ShopList.vue / PublishSelect.vue
        ├── Video.vue / TravelList.vue / ListDetail.vue
        ├── Page1.vue / Page2.vue / Page3.vue / Page4.vue
        ├── PrivacyPolicy.vue / UserAgreement.vue
        └── NotFound.vue        # 404 兜底
```

---

## 三、路由表（hash 模式，36 个有效页面）

| 原多页文件 | 路由 path | 视图文件 | 鉴权 |
| --- | --- | --- | --- |
| index.html | `/index`（`/` 重定向） | Index.vue | ✅ |
| login.html | `/login` | Login.vue | ❌（登录页） |
| login2.html | `/login2` | Login2.vue | ❌（密码登录） |
| AI.html | `/ai` | AI.vue | ✅ |
| jingdian.html | `/jingdian` | Jingdian.vue | ✅ |
| map.html | `/map` | Map.vue | ✅ |
| scenic-detail.html | `/scenic-detail` | ScenicDetail.vue | ✅ |
| travel-plan.html | `/travel-plan` | TravelPlan.vue | ✅ |
| 当地游.html | `/local-tour` | LocalTour.vue | ✅ |
| 组团游.html | `/group-tour` | GroupTour.vue | ✅ |
| zhaogonglue.html | `/zhaogonglue` | ZhaoGonglue.vue | ✅ |
| gonglue-detail.html | `/gonglue-detail` | GonglueDetail.vue | ✅ |
| hotel-room-detail.html | `/hotel-room-detail` | HotelRoomDetail.vue | ✅ |
| hotel-order-detail.html | `/hotel-order-detail` | HotelOrderDetail.vue | ✅ |
| ticket-order-detail.html | `/ticket-order-detail` | TicketOrderDetail.vue | ✅ |
| shop-detail.html | `/shop-detail` | ShopDetail.vue | ✅ |
| shop-list.html | `/shop-list` | ShopList.vue | ✅ |
| publish-select.html | `/publish-select` | PublishSelect.vue | ✅ |
| blog-detail.html | `/blog-detail` | BlogDetail.vue | ✅ |
| blog-edit.html | `/blog-edit` | BlogEdit.vue | ✅ |
| info.html | `/info` | Info.vue | ✅ |
| info-edit.html | `/info-edit` | InfoEdit.vue | ✅ |
| other-info.html | `/other-info` | OtherInfo.vue | ✅ |
| TOP.html | `/top` | Top.vue | ✅ |
| TOP-detail.html | `/top-detail` | TopDetail.vue | ✅ |
| watch.html | `/watch` | Watch.vue | ✅ |
| video-edit.html | `/video-edit` | VideoEdit.vue | ✅ |
| video.html | `/video` | Video.vue | ✅ |
| 旅游清单.html | `/travel-list` | TravelList.vue | ✅ |
| 清单详情.html | `/list-detail` | ListDetail.vue | ✅ |
| page1.html | `/page1` | Page1.vue | ✅ |
| page2.html | `/page2` | Page2.vue | ✅ |
| page3.html | `/page3` | Page3.vue | ✅ |
| page4.html | `/page4` | Page4.vue | ✅ |
| privacy-policy.html | `/privacy-policy` | PrivacyPolicy.vue | ❌（协议页） |
| user-agreement.html | `/user-agreement` | UserAgreement.vue | ❌（协议页） |
| — | `/:pathMatch(.*)*` | NotFound.vue | 404 兜底 |

> 原页面间互跳（如首页 → `/组团游.html`）已全部转为 `router.push('/group-tour')`，query 原样保留（如 `/top-detail?id=1`）。

---

## 四、组件与公共能力划分

| 模块 | 职责 | 说明 |
| --- | --- | --- |
| `components/HeaderBar.vue` | 标准返回头部（返回按钮 + 居中标题 + 可选右侧插槽） | 替代原各页内嵌 `div.header > .header-back-btn + .header-title`；首页/AI 页自定义头部保留内联 |
| `components/FootBar.vue` | 底部导航（首页/行程/我的，active 高亮） | 原 `footer.js` 的 `footBar` 全局组件，跳转映射一致（0→/video-edit、1→/index、2→/map、3→/video、4→/info） |
| `api/request.js` | 业务后端统一请求 | baseURL `/api`、timeout 2000、request 带 `authorization: token`、response 校验 `success`（false 则 reject errorMsg）、401 延时跳登录 |
| `api/ai.js` | AI 后端（9999）独立封装 | 裸响应无 success 包装，必须 fetch/EventSource，防误判 |
| `stores/auth.js` | 登录态（token） | 与 `sessionStorage` 同步 |
| `utils/index.js` | `getUrlParam` / `formatPrice` | 原 common.js util；getUrlParam 已适配 hash 路由 query |
| `styles/main.css` + `header.css` | 全局基础样式 | 原 css 直迁；**页面专属样式全部收进 SFC `<style scoped>`**（SPA 防串扰），Element Plus 内部类用 `:deep()` 覆盖 |

## 五、关键技术适配（Vue2 → Vue3）

1. **UI 库**：Element UI（Vue2 版 element.js）→ Element Plus；`this.$message`→`ElMessage`、`this.$alert/$confirm`→`ElMessageBox`、`this.$loading`→`ElLoading.service`、表单校验 `this.$refs.form.validate(cb)`→`await formRef.value.validate()`、`visible.sync`→`v-model`、`slot="xxx"`→`<template #xxx>`、`.native` 修饰符删除、`size="medium/mini"` 移除。
2. **图标**：`<i class="el-icon-xxx">` → `<el-icon><图标组件 /></el-icon>`（全部图标在 `main.js` 全局注册，模板直接用）。
3. **实例写法**：`new Vue({...})` → `<script setup>` 组合式 API（ref/reactive/onMounted/onBeforeUnmount）。
4. **路由**：多页 `.html` 互跳 → hash 路由（`router.push` / `<router-link>`）；登录守卫收敛到 `router.beforeEach`；页面标题由 `router.afterEach` 设置。
5. **网络**：`axios` 全局 → `request` 实例；AI 链路 SSE 直连 9999（`event:message` 一次性返回后关闭、`event:error` 异常）。
6. **样式**：多页各自 `<style>` → SFC scoped；`* reset / body / #app` 全局规则不迁移（main.css 统一管理）。

## 六、构建与部署

```powershell
# 安装依赖（已就绪，node_modules 存在）
npm install

# 本地开发（需 nginx 8080 或业务后端在 9528；AI 后端需 9999 在线）
npm run dev        # http://localhost:5173  （/api → proxy → http://localhost:8080）

# 生产构建
npm run build      # 产出 dist/
```

**nginx 静态托管（http://localhost:8080）**——hash 模式零配置：

```nginx
server {
    listen 8080;
    # 将 dist 目录设为根目录即可（任选其一）
    # 方案1：把 wtlx-frontend/dist 内容拷入现有 html 目录
    root  D:/JavaProject/IdeaProject/TripAgent/wtlx-frontend/dist;
    index index.html;
}
```

> 访问入口 `http://localhost:8080/#/index`。若后续想切换 history 路由模式，需在 nginx 加 `try_files $uri $uri/ /index.html;` 回退，并把 `createWebHashHistory` 改为 `createWebHistory`（本次未启用，保持零配置）。

## 七、已知行为差异（迁移汇总）

1. **缺图兜底**：原页面引用的 `/imgs/icons/default-icon.png`、`default-shop.jpg`、`hotel-room1~3.jpg`、`user5-icon.png`、`./imgs/default.png` 等素材库中本不存在（原页面即坏链），统一改用真实存在的 `/imgs/icons/default-icon.jpg` 兜底。
2. **头部组件化**：标准 `div.header > .header-back-btn + .header-title` 结构页统一替换为 `<HeaderBar>` 组件（首页/AI 页自定义头部保留内联）；自定义头部页（订单详情 `.detail-header`、协议页 `.policy-header`、blog-edit/video-edit 发布头）保留原结构。
3. **登录校验收敛**：原各页 `mounted` 中 `token` 检查 + `location.href='/login.html'` 跳转已删除，由路由守卫与 request 拦截器 401 统一处理。
4. **AI 链路**：会话列表 localStorage 维护（`ai_sessions`）、旧 `ai_chat_id` 自动迁移、SSE 直连 9999（fetch/EventSource 绕过 axios 拦截器）。
5. **页面级小修（行为等价或修复原缺陷）**：
   - `tel:` / `weixin://` 唤起：`window.location.href` 改为创建临时 `<a>` 触发 click；
   - 轮播/倒计时 `setInterval` 增加 `onBeforeUnmount` 清理（原页面离开后不清理，属内存泄漏修复）；
   - 订单状态标签：原非法 `el-tag :type` 值映射为 Element Plus 合法类型（0 待支付→warning / 1 已支付→primary / 2 已确认→success / 3 已取消→info / 4 已完成→success）；
   - 清单详情：`el-time-picker` 的 Vue2 `step` 配置改为 EP `el-time-select`（15 分钟步进等价）；添加账单/行李后不再整页 reload，改为就地刷新并停留当前 tab（修复原页 reload 后 tab 硬编码跳回行程的缺陷）；
   - shop-detail：原模板未绑定的 seckill 死代码裁剪，`queryVoucher` 网络调用保留；
   - TravelPlan：分享弹窗由脆弱 querySelector 反查改为类选择器绑定；百度地图 GL 脚本改为组件 onMounted 动态注入（两个页面各自的 ak 原值保留）；
   - video-edit：原 `util.getUserId()` 在 common.js 中本不存在（恒 undefined），迁移后提交体不再带 userId（后端从 token 解析）；
   - page1~4：原 `.article-content` 无任何 CSS 定义，补最小可读样式（padding/行高/图片圆角），文案结构不变。
