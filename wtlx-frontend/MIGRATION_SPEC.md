# 微途旅行 wtlx-frontend 页面迁移规范（MIGRATION_SPEC）

> 本文档是页面迁移的**唯一规范**：所有迁移子代理必须先完整阅读本文档 + 三个种子视图（`src/views/Index.vue`、`Login.vue`、`AI.vue`），再开始迁移。规范冲突时以本文档为准。

## 0. 项目骨架（已就绪，禁止改动核心文件）

已存在的文件（**禁止修改**）：
- `package.json`、`vite.config.js`、`index.html`、`.gitignore`
- `src/main.js`（Element Plus 全量引入 + 全部图标全局注册 + Pinia + Router）
- `src/App.vue`
- `src/router/index.js` —— **路由表已冻结**（全部 36 个有效页面已注册，懒加载；登录守卫、标题设置已内置；你只需把对应视图文件建到 `src/views/<Name>.vue`）
- `src/api/request.js`（业务后端 axios 实例，`/api` 前缀、2s 超时、authorization 头、success 校验、401 跳登录）、`src/api/user.js`、`src/api/ai.js`（AI 后端 9999 的 fetch/EventSource 封装）
- `src/stores/auth.js`（Pinia 登录态）
- `src/components/HeaderBar.vue`（标准返回头部）、`src/components/FootBar.vue`（底部导航：首页/行程/我的）
- `src/styles/main.css`、`src/styles/header.css`（全局样式，已在 main.js 引入）
- `src/utils/index.js`（`getUrlParam` / `formatPrice`，原 common.js util 迁移）
- `public/imgs/`、`public/video/`、`public/favicon.ico`（静态资源已就位，页面引用路径保持 `/imgs/xxx` 不变）

**你的唯一任务**：在 `src/views/` 下创建分给你的页面视图文件（`.vue`），并完成自检。

## 1. 迁移铁律（Vue2 → Vue3 + Element Plus 差异对照）

### 1.1 组件/实例写法
| 原写法（Vue2） | 新写法（Vue3） |
| --- | --- |
| `new Vue({ el:'#app', data, methods, created })` | `<script setup>` 组合式 API（参考三个种子页） |
| `data(){ return {...} }` | `const xx = ref(...)` / `reactive({...})` |
| `methods: { fn() {} }` | 普通 `function fn() {}` |
| `created()` / `mounted()` | `onMounted(() => {...})`（import 自 vue） |
| `beforeDestroy()` | `onBeforeUnmount(() => {...})` |
| `this.xxx` | 直接变量；ref 变量在脚本中须 `.value`，模板中自动解包 |
| `this.$refs.xxx` | `const xxxRef = ref(null)` + 模板 `ref="xxxRef"`，脚本中 `xxxRef.value` |
| `this.$nextTick(cb)` | `nextTick(() => cb())`（import 自 vue） |
| `filters: {...}` | 普通函数 / `computed` |

### 1.2 Element UI → Element Plus API 对照
| 原写法 | 新写法 |
| --- | --- |
| `this.$message.error('x')` | `import { ElMessage } from 'element-plus'` → `ElMessage.error('x')` |
| `this.$message({message,type,duration})` | `ElMessage({ message, type, duration })` |
| `this.$alert(html, title, opts)` | `import { ElMessageBox } from 'element-plus'` → `ElMessageBox.alert(html, title, opts)`（dangerouslyUseHTMLString 保留） |
| `this.$confirm(msg, title, opts).then()` | `ElMessageBox.confirm(msg, title, opts).then()` |
| `this.$notify(...)` | `import { ElNotification }` → `ElNotification(...)` |
| `this.$loading({...})` | `import { ElLoading }` → `ElLoading.service({...})`，返回实例 `.close()` |
| 表单校验 `this.$refs.form.validate(cb)` | `await formRef.value.validate()`（返回 Promise，失败会 reject 并抛校验错误；参考：`try { await ... } catch(e) {}`） |
| `visible.sync` / `:visible.sync` | `v-model` |
| `@click.native` / `@keyup.enter.native` | 去掉 `.native`（Vue3 事件即原生） |
| `slot="prefix"` / `slot="xxx"` | `<template #prefix>` / `<template #xxx>` |
| `size="medium"` / `size="mini"` | 删除该属性，或 `size="default"` / `size="small"` |
| `<i class="el-icon-xxx"></i>` | `<el-icon><对应图标组件 /></el-icon>`（图标组件**已全局注册**，模板直接用组件名即可，无需 import） |
| `element.js` 的 `el-*` 组件（el-input/el-button/el-carousel/el-tabs/el-dialog/el-drawer/el-upload/el-select/el-radio/el-checkbox/el-form/el-rate/el-table/el-pagination 等） | 同名可用，用法基本不变 |

### 1.3 图标对照表（el-icon-* → Element Plus 图标组件名）
`s-home→HomeFilled`、`map-location→MapLocation`、`user→User`、`arrow-left→ArrowLeft`、`arrow-right→ArrowRight`、`arrow-down→ArrowDown`、`arrow-up→ArrowUp`、`mobile-phone→Iphone`、`message→Message`、`sunny→Sunny`、`edit→EditPen`、`edit-outline→EditPen`、`thumb→Thumb`、`chat-dot-round→ChatDotRound`、`loading→Loading`、`close→Close`、`plus→Plus`、`search→Search`、`star-on→StarFilled`、`star-off→Star`、`view→View`、`location→Location`、`phone→Phone`、`check→Check`、`warning→Warning`、`info→Info`、`success→CircleCheck`、`delete→Delete`、`share→Share`、`refresh→Refresh`、`picture→Picture`、`camera→Camera`、`video-camera→VideoCamera`、`date→Calendar`、`time→Clock`、`upload→Upload`、`download→Download`、`setting→Setting`、`menu→Menu`、`more→MoreFilled`、`back→Back`、`right→ArrowRight`、`bottom→ArrowDown`、`top→ArrowUp`、`bell→Bell`、`mic→Microphone`、`folder→Folder`、`folder-opened→FolderOpened`、`document→Document`、`tickets→Tickets`、`position→LocationInformation`、`rank→TrendCharts`、`trophy→Trophy`、`medal→Medal`、`crown→Crown`、`place→Location`、`food→KnifeFork`、`hotel→House`、`ticket→Ticket`、`ship→Ship`、`plane→Aim`、`bicycle→Bicycle`、`guide→Compass`、`map→MapLocation`、`school→School`、`umbrella→Umbrella`、`moon→Moon`、`cloudy→PartlyCloudy`、`rainy→Cloudy`、`sunny→Sunny`。
> 找不到精确对应时选用语义最接近的图标组件名（保持视觉近似即可），不要自定义 class。

### 1.4 网络请求
- 业务接口一律 `import request from '../api/request'`，用法 `request.get/post/put/delete(...)`。
- **成功判定已由拦截器完成**：`res` 即响应体 `{success, data, errorMsg}`，取数据用 `res.data`（与原来 `axios.post(...).then(({data})=>...)` 语义一致）。拦截器已处理 401 跳登录。
- **禁止**直接用全局 `axios`（未安装为全局）、禁止绕过 request 写裸 axios。
- AI 后端（9999）不走 request，用 `src/api/ai.js` 提供的 `fetchHistory/deleteHistory/openChatStream`（SSE 直连，CORS 已放开）。

### 1.5 跳转（最重要的一条）
- `location.href = '/xxx.html'` → `const router = useRouter()` + `router.push('/xxx')`
- `location.href = '/xxx.html?id=1&name=xx'` → `router.push('/xxx?id=1&name=xx')`
- `window.location.href = './xxx.html?id=1'` → `router.push('/xxx?id=1')`
- `history.back()` → `router.back()`
- `<a href="/xxx.html">` → `<router-link to="/xxx">`（样式上 `color: inherit; text-decoration: none;` 视原样式而定）
- 页面内 `if(!sessionStorage.getItem("token")){ location.href="/login.html" }` → **直接删除**（路由守卫已统一处理）
- 原中文文件名互跳（`/当地游.html` 等）→ 按第 3 节路由表转英文路径
- hash 路由下 query 读取：`src/utils/index.js` 的 `getUrlParam(name)` 已适配（先查 hash 内 query 再回退 search），直接复用，勿自行解析 location.search

### 1.6 样式迁移规则（硬性）
- 页面专属样式（原 `<style>` 内联块 + 对应 `css/*.css` 文件）→ **全部放进 SFC 的 `<style scoped>`**。
- 覆盖 Element Plus 内部类的选择器（`.el-input__inner`、`.el-dialog`、`.el-message-box`、`.el-carousel__...`、`.el-radio__...` 等）→ 必须用 `:deep(.el-input__inner)` 包裹。
- **禁止** `<style>`（无 scoped）——SPA 中多个页面的全局样式会互相污染。
- **禁止**拷贝 `* { margin:0 }`、`body{}`、`#app{}` 等全局 reset（main.css 已管理）。
- 原 html 中多个 `<style>` 块出现重复选择器时合并去重（保留一份即可）。
- 图片路径保持不变：`/imgs/xxx.png`、`/video/xxx.mp4`（public 已就位）。
- 原页面 css 里针对 `el-*` 组件的旧类名若在 Element Plus 中不存在，删除或换成 EP 等效选择器，不要强留无效规则。

## 2. 三种标准页面骨架（模式）

### 2.1 带标准返回头部的页面
```vue
<template>
  <div class="page-container">
    <HeaderBar title="页面标题" :show-back="true" />
    <!-- 原页面内容 -->
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
const router = useRouter()
// ... 原逻辑
</script>
<style scoped>
/* 页面专属样式 */
</style>
```
> 原页面头部结构若为 `div.header > .header-back-btn + .header-title`（header.css 全局样式）→ 直接用 `<HeaderBar>` 组件（登录/登录2/种子页已示范）。首页/AI 页等自定义头部不适用，保留原内联结构。

### 2.2 带底部导航的页面
在模板末尾加 `<FootBar :active-btn="n" />`（n 取原页 footBar activeBtn 值，默认 1；样式已在 main.css）。

### 2.3 纯内容页（协议/政策）
普通 `router-link` + 内容，无需鉴权（路由 meta 已配 `requiresAuth: false`）。

## 3. 路由/视图文件对应表（各分片子代理按自己的表执行）

| 原文件 | 路由 path | 视图文件（src/views/） |
| --- | --- | --- |
| index.html | /index（已由种子页完成） | Index.vue ✅ |
| login.html | /login（已由种子页完成） | Login.vue ✅ |
| login2.html | /login2（已由种子页完成） | Login2.vue ✅ |
| AI.html | /ai（已由种子页完成） | AI.vue ✅ |
| jingdian.html | /jingdian | Jingdian.vue |
| map.html | /map | Map.vue |
| scenic-detail.html | /scenic-detail | ScenicDetail.vue |
| travel-plan.html | /travel-plan | TravelPlan.vue |
| 当地游.html | /local-tour | LocalTour.vue |
| 组团游.html | /group-tour | GroupTour.vue |
| zhaogonglue.html | /zhaogonglue | ZhaoGonglue.vue |
| gonglue-detail.html | /gonglue-detail | GonglueDetail.vue |
| hotel-room-detail.html | /hotel-room-detail | HotelRoomDetail.vue |
| hotel-order-detail.html | /hotel-order-detail | HotelOrderDetail.vue |
| ticket-order-detail.html | /ticket-order-detail | TicketOrderDetail.vue |
| shop-detail.html | /shop-detail | ShopDetail.vue |
| shop-list.html | /shop-list | ShopList.vue |
| publish-select.html | /publish-select | PublishSelect.vue |
| blog-detail.html | /blog-detail | BlogDetail.vue |
| blog-edit.html | /blog-edit | BlogEdit.vue |
| info.html | /info | Info.vue |
| info-edit.html | /info-edit | InfoEdit.vue |
| other-info.html | /other-info | OtherInfo.vue |
| TOP.html | /top | Top.vue |
| TOP-detail.html | /top-detail | TopDetail.vue |
| watch.html | /watch | Watch.vue |
| video-edit.html | /video-edit | VideoEdit.vue |
| video.html | /video | Video.vue |
| 旅游清单.html | /travel-list | TravelList.vue |
| 清单详情.html | /list-detail | ListDetail.vue |
| page1.html | /page1 | Page1.vue |
| page2.html | /page2 | Page2.vue |
| page3.html | /page3 | Page3.vue |
| page4.html | /page4 | Page4.vue |
| privacy-policy.html | /privacy-policy | PrivacyPolicy.vue |
| user-agreement.html | /user-agreement | UserAgreement.vue |

跳过不迁移：`test.html`（683B 测试页）、`页面框架.html`（模板页）、`yourpage.html`（0 字节空文件）。

## 4. 已知陷阱（务必先读）

1. **`/imgs/icons/default-icon.png` 不存在**：实际文件是 `/imgs/icons/default-icon.jpg`。原页面引用 png 本身就是坏链，迁移时**统一改用 `/imgs/icons/default-icon.jpg`**。
2. **`public/video/` 为空**：本地无视频素材，video 相关 `src` 均为后端返回的 `videoUrl`（接口字段），不要写死本地路径。
3. **请求超时 2s、成功判定 `res.success`**：接口路径/参数名/字段名一律保持原样，不要"优化"或改名。
4. **`formatPrice` / `getUrlParam` 复用 `src/utils/index.js`**，勿重复实现。
5. 原页面中 `this.$message.error`（忘了加括号的 bug 式写法）→ 按错误处理意图处理（提示或 console），不要原样照抄。
6. 模板中的 `v-for` 必须配 `:key`；动态 ref（Vue2 `:ref="'video'+i"`）→ Vue3 函数式 ref（参考 Index.vue 的 `videoRef(index)`）。
7. 中文文件名页面间的互跳（如 当地游→组团游）必须换成英文路由路径。
8. 不要创建任何新的全局组件/全局样式/路由；不要改 router/index.js。

## 5. 交付与自检（每个页面必做）

### 5.1 完成定义
- 模板：原页面结构、文案、布局完整还原（Element Plus 组件等价替换）。
- 脚本：原逻辑完整迁移（API 调用、条件判断、交互、格式化函数）。
- 样式：原页面全部样式进入 `<style scoped>`（含 `:deep` 适配）。
- 每个视图文件是**完整可运行页面**，禁止占位/半成品/TODO。

### 5.2 自检清单（逐页过一遍）
- [ ] 无 `location.href` / `window.location` / `history.back` 残留（除注释说明）
- [ ] 无 `this.$`（`this.$message` / `this.$refs` / `this.$router` 等）
- [ ] 无 `<i class="el-icon-` 残留
- [ ] 无 `visible.sync` / `.sync` / `.native` / `slot="`（属性式 slot）
- [ ] 无 `axios.` 全局调用、无 `new Vue`、无 `Vue.component`
- [ ] 所有 import 语句有真实来源（vue / element-plus / 项目内模块）
- [ ] 模板内 `v-for` 均有 `:key`
- [ ] 图片引用均存在于 `public/imgs/`（缺图用 `default-icon.jpg` 或等价图兜底，并在交付说明中注明）

### 5.3 交付报告
完成后报告：
1. 每个页面的视图文件**绝对路径**（如 `D:\JavaProject\IdeaProject\TripAgent\wtlx-frontend\src\views\Jingdian.vue`）
2. 自检清单结果（逐页）
3. 与原页面**有意的行为差异**（如有，如实列出，例如某接口在联调时不可用而做了容错）

## 6. 技术栈与验证
- Vue 3.5 + Vite 6 + vue-router 4（hash 模式）+ Pinia 2 + Element Plus 2.9 + axios 1.7
- 依赖已安装完毕（node_modules 就绪），可随时在 `D:\JavaProject\IdeaProject\TripAgent\wtlx-frontend` 下运行 `npm run dev`（需业务后端/nginx 时联调）。
- 完整构建验证由组织者统一执行（`npm run build`），发现你分片内页面的编译/逻辑问题会回传给你修复。
