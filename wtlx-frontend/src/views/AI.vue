<template>
  <div class="chat-layout">
    <!-- 移动端侧边栏遮罩，点击可收起 -->
    <div class="sidebar-backdrop" v-if="showSidebar" @click="showSidebar = false"></div>
    <!-- 会话侧边栏 -->
    <div class="sidebar" v-if="showSidebar">
      <div class="sidebar-header">
        <div class="sidebar-back-btn" @click="toggleSidebar">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回</span>
        </div>
        <div class="new-chat-btn" @click="createNewChat">
          <span>+</span> 新建会话
        </div>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: currentChatId === session.id }"
          @click="switchChat(session.id)"
        >
          <div class="session-title">{{ session.title || '新会话' }}</div>
          <div class="session-time">{{ formatDate(session.time) }}</div>
          <div class="session-delete" @click.stop="deleteChat(session.id)">×</div>
        </div>
      </div>
    </div>

    <div class="main-content">
      <div class="chat-container">
        <div class="header">
          <div class="header-back-btn" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
          </div>
          <div class="header-title">
            <span class="toggle-sidebar" @click="toggleSidebar">☰</span>
            {{ currentChatTitle }}
          </div>
        </div>

        <div class="messages" ref="messagesRef">
          <div v-if="messages.length === 0" class="welcome-section">
            <span class="welcome-icon">🌏</span>
            <h2 class="welcome-title">开启您的旅程规划</h2>
            <p class="welcome-desc">
              告诉我您的目的地、出行时间和偏好，我将为您量身定制完美的旅行计划
            </p>
            <div class="quick-actions">
              <button class="quick-action-btn" @click="sendQuickMessage('我想去北京旅游')">🏛️ 北京三日游</button>
              <button class="quick-action-btn" @click="sendQuickMessage('推荐海边度假胜地')">🏖️ 海边度假</button>
              <button class="quick-action-btn" @click="sendQuickMessage('帮我规划云南行程')">🏔️ 云南行程</button>
              <button class="quick-action-btn" @click="sendQuickMessage('推荐小众旅游目的地')">✨ 小众秘境</button>
            </div>
          </div>

          <template v-else>
            <div v-for="(msg, index) in messages" :key="index"
              class="message-item"
              :class="msg.isUser ? 'user-message-row' : 'ai-message-row'">
              <template v-if="!msg.isUser">
                <div class="avatar ai-avatar">AI</div>
                <div class="message-body">
                  <ThinkingPanel :steps="msg.thinking || []" :streaming="!!msg.streaming" />
                  <div v-if="msg.content" class="bubble ai-bubble" v-html="renderMarkdown(msg.content)"></div>
                  <BlogCardGrid :cards="msg.cards || []" />
                  <span class="timestamp">
                    {{ formatTime(msg.time) }}
                    <em v-if="msg.cancelled" class="cancelled-tag">已停止</em>
                  </span>
                </div>
              </template>
              <template v-else>
                <div class="message-body">
                  <div class="bubble user-bubble">{{ msg.content }}</div>
                  <span class="timestamp user-timestamp">{{ formatTime(msg.time) }}</span>
                </div>
                <div class="avatar user-avatar">我</div>
              </template>
            </div>
          </template>

          <div v-if="connectionStatus === 'connecting'" class="message-item ai-message-row">
            <div class="avatar ai-avatar">AI</div>
            <div class="message-body">
              <div class="bubble ai-bubble">
                <span class="typing-indicator">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                </span>
                <span class="cursor">▋</span>
              </div>
            </div>
          </div>
        </div>

        <div class="input-area">
          <div class="input-box-wrapper">
            <textarea
              v-model="inputMessage"
              class="input-field"
              placeholder="告诉我您想去哪里旅游..."
              @keydown.enter.prevent="handleEnter"
              :disabled="connectionStatus !== 'disconnected'"
              rows="1"
              ref="inputRef"
            ></textarea>
            <button
              v-if="connectionStatus === 'disconnected'"
              class="send-btn"
              @click="sendMessage"
              :disabled="!inputMessage.trim()">发送</button>
            <button
              v-else
              class="stop-btn"
              @click="stopGeneration"
              title="停止本次生成">停止</button>
          </div>
          <p class="input-hint">按 Enter 发送消息，Shift + Enter 换行</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { fetchHistory, deleteHistory, openChatStream, cancelChat } from '../api/ai'
import ThinkingPanel from '../components/chat/ThinkingPanel.vue'
import BlogCardGrid from '../components/chat/BlogCardGrid.vue'

const router = useRouter()

const messages = ref([])
const inputMessage = ref('')
const connectionStatus = ref('disconnected')
const messagesRef = ref(null)
const inputRef = ref(null)
let eventSource = null
// 当前生成请求的唯一凭据：取消时靠它让服务端定位到持流实例（多实例下可能不是本机）
let currentRequestId = ''
// 当前正在流式生成的消息在 messages 数组中的下标；
// 用下标而不是对象引用，确保修改的是 messages 里的响应式代理，触发实时渲染。
let currentStreamingIndex = -1
// 为缓解高频 delta 带来的 DOM 抖动，把同一帧内到达的增量先缓冲，按 rAF 批量追加
let pendingDelta = ''
let deltaRafId = null
const sessions = ref([])
const currentChatId = ref('')
const currentChatTitle = ref('AI助手')
const showSidebar = ref(false)

onMounted(() => {
  nextTick(() => {
    scrollToBottom()
  })
  loadSessions()

  // 旧版单会话记录（ai_chat_id）在 loadSessions 中迁移为会话列表；无任何记录时新建
  if (currentChatId.value) {
    loadHistory(currentChatId.value)
  }
})

onBeforeUnmount(() => {
  closeEventSource()
  discardStreamingState()
})

// 输入内容变化时自适应高度
watch(inputMessage, () => {
  nextTick(() => adjustTextareaHeight())
})

function renderMarkdown(text) {
  if (!text) return ''
  let html = text
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
    // 列表块须先于斜体处理：连续的 "- xxx" 行整体包成 <ul>
    .replace(/((?:^[-*] .+$\n?)+)/gm, (block) =>
      '<ul>' + block.replace(/^[-*] (.+)$/gm, '<li>$1</li>') + '</ul>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br>')
  return html
}

// SSE data 兼容解析：新协议为 JSON，解析失败时按纯文本兜底
function parseJson(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (e) {
    return null
  }
}

function goBack() {
  router.back()
}

function formatTime(timestamp) {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatDate(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'

  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function loadSessions() {
  // 后端未提供会话列表接口，会话列表在前端本地维护（localStorage）
  let list = []
  try {
    list = JSON.parse(localStorage.getItem('ai_sessions') || '[]')
  } catch (e) {
    list = []
  }
  sessions.value = list

  // 兼容旧版单会话记录（ai_chat_id）迁移到会话列表
  const legacyChatId = localStorage.getItem('ai_chat_id')
  if (legacyChatId && !sessions.value.some(s => s.id === legacyChatId)) {
    sessions.value.unshift({
      id: legacyChatId,
      title: getChatTitleFromId(legacyChatId),
      time: Date.now()
    })
    localStorage.removeItem('ai_chat_id')
    saveSessions()
  }
  sessions.value.sort((a, b) => b.time - a.time)

  const savedChatId = localStorage.getItem('ai_chat_id')
  if (savedChatId && sessions.value.some(s => s.id === savedChatId)) {
    currentChatId.value = savedChatId
  } else if (sessions.value.length > 0) {
    currentChatId.value = sessions.value[0].id
    localStorage.setItem('ai_chat_id', currentChatId.value)
  } else {
    currentChatId.value = generateUUID()
    addSessionToLocal(currentChatId.value)
    localStorage.setItem('ai_chat_id', currentChatId.value)
  }
  const session = sessions.value.find(s => s.id === currentChatId.value)
  currentChatTitle.value = session ? session.title : 'AI助手'
}

function getChatTitleFromId(id) {
  if (!id) return '新会话'
  if (id.length > 12) {
    return id.substring(0, 8) + '...'
  }
  return id
}

function saveSessions() {
  localStorage.setItem('ai_sessions', JSON.stringify(sessions.value))
}

function addSessionToLocal(id, title) {
  if (sessions.value.some(s => s.id === id)) return
  sessions.value.unshift({
    id: id,
    title: title || getChatTitleFromId(id),
    time: Date.now()
  })
  saveSessions()
}

function createNewChat() {
  closeEventSource()
  cancelCurrentGeneration()
  discardStreamingState()
  messages.value = []
  inputMessage.value = ''
  currentChatId.value = generateUUID()
  addSessionToLocal(currentChatId.value)
  localStorage.setItem('ai_chat_id', currentChatId.value)
  currentChatTitle.value = 'AI助手'
}

function switchChat(chatId) {
  closeEventSource()
  cancelCurrentGeneration()
  discardStreamingState()
  currentChatId.value = chatId
  localStorage.setItem('ai_chat_id', chatId)
  messages.value = []
  const session = sessions.value.find(s => s.id === chatId)
  currentChatTitle.value = session ? session.title : 'AI助手'
  loadHistory(chatId)
}

async function loadHistory(threadId) {
  if (!threadId) return
  try {
    const data = await fetchHistory(threadId)
    // 响应结构: { threadId, context: [{role, content}] }，过滤系统/工具消息
    const context = (data && data.context) || []
    const history = context.filter(m => m.role === 'USER' || m.role === 'ASSISTANT')
    if (history.length === 0) {
      messages.value = []
      return
    }
    // 历史只存结论正文（思考步骤与卡片不落库），故这两项恢复为空集合
    messages.value = history.map(m => ({
      content: m.content || '',
      isUser: m.role === 'USER',
      time: Date.now(),
      thinking: [],
      cards: []
    }))
    nextTick(() => scrollToBottom())
  } catch (err) {
    console.error('加载历史记录失败:', err)
  }
}

function deleteChat(chatId) {
  if (!confirm('确定要删除这个会话吗？')) return

  // 清除后端该线程对话记忆
  deleteHistory(chatId).catch(err => {
    console.error('清除后端会话记忆失败:', err)
  })

  // 移除本地会话记录
  const index = sessions.value.findIndex(s => s.id === chatId)
  if (index > -1) {
    sessions.value.splice(index, 1)
    saveSessions()
  }
  if (currentChatId.value === chatId) {
    createNewChat()
  }
}

function sendQuickMessage(text) {
  inputMessage.value = text
  sendMessage()
}

function adjustTextareaHeight() {
  const textarea = inputRef.value
  if (textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px'
  }
}

function handleEnter(e) {
  if (!e.shiftKey) {
    sendMessage()
  }
}

function addMessage(content, isUser) {
  messages.value.push({
    content,
    isUser,
    time: new Date().getTime(),
    thinking: [],
    cards: []
  })
  scrollToBottom()
}

// 思考步骤与卡片可能先于正文到达，统一在这里确保 AI 消息气泡已创建
function ensureStreamingMessage() {
  if (currentStreamingIndex < 0) {
    messages.value.push({
      content: '',
      isUser: false,
      time: Date.now(),
      thinking: [],
      cards: [],
      streaming: true
    })
    currentStreamingIndex = messages.value.length - 1
  }
  return currentStreamingIndex
}

// 按 seq 幂等写入思考步骤：同序号为"更新该步骤"（工具参数逐步补全）
function upsertThinkingStep(step) {
  if (!step || step.seq == null) return
  const idx = ensureStreamingMessage()
  const list = messages.value[idx].thinking
  const existed = list.find(item => item.seq === step.seq)
  if (existed) {
    existed.type = step.type
    existed.label = step.label
    existed.text = step.text
  } else {
    list.push({ seq: step.seq, type: step.type, label: step.label, text: step.text })
  }
  connectionStatus.value = 'streaming'
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 把缓冲中的增量真正追加到当前 AI 消息，并滚动到底部
function flushDelta() {
  deltaRafId = null
  if (pendingDelta && currentStreamingIndex >= 0) {
    messages.value[currentStreamingIndex].content += pendingDelta
    pendingDelta = ''
    scrollToBottom()
  }
}

// 同步刷新缓冲（用于 done/error/cancelled/stop 等收尾场景）
function flushDeltaSync() {
  if (deltaRafId) {
    cancelAnimationFrame(deltaRafId)
    deltaRafId = null
  }
  flushDelta()
}

// 切换/新建会话时丢弃未追加的缓冲，避免把旧流的残片挂到新会话
function discardStreamingState() {
  if (deltaRafId) {
    cancelAnimationFrame(deltaRafId)
    deltaRafId = null
  }
  pendingDelta = ''
  if (currentStreamingIndex >= 0 && messages.value[currentStreamingIndex]) {
    messages.value[currentStreamingIndex].streaming = false
  }
  currentStreamingIndex = -1
}

function closeEventSource() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

// 收尾一次流式会话：退出生成态并关闭连接
function finishStreaming() {
  if (deltaRafId) {
    cancelAnimationFrame(deltaRafId)
    deltaRafId = null
  }
  pendingDelta = ''
  if (currentStreamingIndex >= 0 && messages.value[currentStreamingIndex]) {
    messages.value[currentStreamingIndex].streaming = false
  }
  currentStreamingIndex = -1
  connectionStatus.value = 'disconnected'
  currentRequestId = ''
  closeEventSource()
}

// 通知服务端停止当前生成：接口可能打到任意实例，由服务端内部广播给持流实例
function cancelCurrentGeneration() {
  const requestId = currentRequestId
  currentRequestId = ''
  if (!requestId) return
  cancelChat(requestId).catch(err => {
    // 即便请求失败，连接断开后服务端也会自行停止，这里只记录
    console.error('取消生成请求失败:', err)
  })
}

// 点击"停止"：先本地收尾保证界面立刻退出生成态，再通知服务端中断
function stopGeneration() {
  flushDeltaSync()
  if (currentStreamingIndex >= 0 && messages.value[currentStreamingIndex]) {
    messages.value[currentStreamingIndex].cancelled = true
    messages.value[currentStreamingIndex].streaming = false
  }
  closeEventSource()
  connectionStatus.value = 'disconnected'
  cancelCurrentGeneration()
}

function sendMessage() {
  const message = inputMessage.value.trim()
  if (!message) return

  addMessage(message, true)

  // 首次发送时用用户消息作为会话标题
  const session = sessions.value.find(s => s.id === currentChatId.value)
  if (session && (!session.title || session.title === '新会话')) {
    session.title = message.length > 12 ? message.substring(0, 12) + '...' : message
    saveSessions()
    currentChatTitle.value = session.title
  }

  inputMessage.value = ''

  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }

  // 上一条流如果还在生成，先让它停下（切换会话/连点发送时避免多条流并行烧 token）
  flushDeltaSync()
  closeEventSource()
  cancelCurrentGeneration()
  connectionStatus.value = 'connecting'

  // requestId 由前端生成并透传，服务端靠它定位这条流（多实例下取消请求未必落到同一台机器）
  currentRequestId = generateUUID()
  currentStreamingIndex = -1
  pendingDelta = ''

  // SSE 走统一 /api（收口后 AI 接口已并入业务服务 9528），协议：delta 增量 + done/cancelled 完整 + error 异常
  eventSource = openChatStream(message, currentChatId.value, currentRequestId)

  eventSource.onopen = () => {
    console.log('SSE 连接已打开')
  }

  // delta：结论正文增量，按 rAF 批量追加到当前 AI 消息，既实时又避免逐字重排
  eventSource.addEventListener('delta', (event) => {
    const data = event.data
    if (!data) return
    ensureStreamingMessage()
    pendingDelta += data
    connectionStatus.value = 'streaming'
    if (!deltaRafId) {
      deltaRafId = requestAnimationFrame(flushDelta)
    }
  })

  // thinking：思考步骤（工具调用 / 检索 / 推理），JSON，按 seq 幂等覆盖
  eventSource.addEventListener('thinking', (event) => {
    upsertThinkingStep(parseJson(event.data))
  })

  // cards：本轮关联的游记 / 攻略卡片，JSON 数组；后端未检索到相关游记时不发此事件
  eventSource.addEventListener('cards', (event) => {
    const cards = parseJson(event.data)
    if (!Array.isArray(cards) || cards.length === 0) return
    const idx = ensureStreamingMessage()
    messages.value[idx].cards = cards
  })

  // done：本轮完整结果，JSON { content, cards, thinking }；解析失败按旧协议纯文本兜底
  eventSource.addEventListener('done', (event) => {
    flushDeltaSync()
    const payload = parseJson(event.data)
    if (payload && typeof payload.content === 'string') {
      const idx = ensureStreamingMessage()
      messages.value[idx].content = payload.content.trim() || messages.value[idx].content
      if (Array.isArray(payload.cards)) messages.value[idx].cards = payload.cards
    } else if (event.data && event.data.trim()) {
      const idx = ensureStreamingMessage()
      messages.value[idx].content = event.data
    }
    finishStreaming()
  })

  // cancelled：本次生成被取消（用户停止 / 连接断开 / 新流挤占），同样为 JSON
  eventSource.addEventListener('cancelled', (event) => {
    flushDeltaSync()
    const payload = parseJson(event.data)
    const partial = payload && typeof payload.content === 'string' ? payload.content : event.data
    const idx = ensureStreamingMessage()
    if (partial && partial.trim()) {
      messages.value[idx].content = partial
    }
    if (Array.isArray(payload && payload.cards)) messages.value[idx].cards = payload.cards
    messages.value[idx].cancelled = true
    finishStreaming()
  })

  // 后端异常以名为 error 的 SSE 事件返回（MessageEvent 带 data）
  eventSource.addEventListener('error', (event) => {
    if (event.data) {
      flushDeltaSync()
      // 若半成品消息为空则移除，避免出现空气泡
      if (currentStreamingIndex >= 0 && !messages.value[currentStreamingIndex].content.trim()) {
        messages.value.splice(currentStreamingIndex, 1)
      }
      addMessage('抱歉，' + event.data, false)
      finishStreaming()
    }
  })

  eventSource.onerror = (error) => {
    if (error && error.data) {
      return
    }
    console.error('SSE Error:', error)
    let errorMsg = '连接失败，请稍后重试'
    if (eventSource && eventSource.readyState === EventSource.CLOSED) {
      errorMsg = '连接已关闭，请稍后重试'
    }
    addMessage('抱歉，' + errorMsg, false)
    connectionStatus.value = 'disconnected'
    eventSource.close()
  }

  scrollToBottom()
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

function toggleSidebar() {
  showSidebar.value = !showSidebar.value
}
</script>

<style scoped>
/* 样式合并自原 css/AI.css；body 与 #app 级全局重置不迁移（避免污染 SPA 其他页面），
   主体布局由 .chat-layout 固定 100vh 承担 */
.chat-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-primary, #faf9f6);
  font-family: 'Noto Sans SC', -apple-system, BlinkMacSystemFont, sans-serif;
}
.sidebar {
  width: 220px;
  min-width: 220px;
  background: #2d3748;
  color: #fff;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #4a5568;
  transition: all 0.3s ease;
}
.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #4a5568;
  display: flex;
  align-items: center;
  gap: 12px;
}
.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: #4a5568;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 14px;
  width: 100%;
}
.new-chat-btn:hover {
  background: #5a6578;
}
.new-chat-btn span {
  font-size: 18px;
  font-weight: bold;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-item {
  position: relative;
  padding: 12px;
  margin-bottom: 4px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.session-item:hover {
  background: #4a5568;
}
.session-item.active {
  background: #4a5568;
}
.session-title {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}
.session-time {
  font-size: 12px;
  color: #a0aec0;
}
.session-delete {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 4px;
  font-size: 16px;
  opacity: 0;
  transition: opacity 0.2s, background 0.2s;
}
.session-item:hover .session-delete {
  opacity: 1;
}
.session-delete:hover {
  background: #e53e3e;
}
/* 侧边栏内返回/收起入口 */
.sidebar-back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  padding: 4px 0;
  color: #a0aec0;
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s;
}
.sidebar-back-btn:hover {
  color: #fff;
}
/* 移动端遮罩：桌面端隐藏 */
.sidebar-backdrop {
  display: none;
}
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  background: linear-gradient(180deg, #faf9f6 0%, #f0efe9 100%);
}
.header {
  position: relative;
  display: flex;
  align-items: center;
  padding: 0 16px;
  height: 44px;
  background: #fff;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}
.header-back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #666;
  font-size: 20px;
  cursor: pointer;
  position: absolute;
  left: 16px;
  z-index: 10;
}
.header-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 17px;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}
.toggle-sidebar {
  cursor: pointer;
  font-size: 18px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
  color: #666;
}
.toggle-sidebar:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #333;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-height: 0;
}
.message-item {
  animation: fade-in 0.3s ease-out;
}
@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.ai-message-row {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}
.user-message-row {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 0.5rem;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.5rem;
  font-weight: 600;
}
.ai-avatar {
  background: linear-gradient(135deg, #1a5f7a, #87ceeb);
  color: #ffffff;
}
.user-avatar {
  background: linear-gradient(135deg, #1a5f7a, #2d7a99);
  color: #ffffff;
}
.message-body {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 70%;
}
.bubble {
  padding: 1rem 1.5rem;
  border-radius: 1rem;
  word-wrap: break-word;
  line-height: 1.6;
  font-size: 14px;
}
.ai-bubble {
  background: #ffffff;
  color: #1f2937;
  border-bottom-left-radius: 0.375rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(0, 0, 0, 0.08);
}
.user-bubble {
  background: linear-gradient(135deg, #1a5f7a, #2d7a99);
  color: #ffffff;
  border-bottom-right-radius: 0.375rem;
}
.timestamp {
  font-size: 12px;
  color: #9ca3af;
  padding: 0 0.5rem;
}
.user-timestamp {
  text-align: right;
}
.typing-indicator {
  display: inline-flex;
  align-items: center;
  margin-right: 8px;
}
.typing-indicator .dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #999;
  margin: 0 2px;
  animation: typing 1.4s infinite ease-in-out;
}
.typing-indicator .dot:nth-child(1) {
  animation-delay: 0s;
}
.typing-indicator .dot:nth-child(2) {
  animation-delay: 0.2s;
}
.typing-indicator .dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-4px); }
}
.cursor {
  display: inline-block;
  animation: blink 1s infinite;
  margin-left: 2px;
}
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
.input-area {
  padding: 1rem;
  background: #ffffff;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}
.input-box-wrapper {
  display: flex;
  gap: 1rem;
  max-width: 900px;
  margin: 0 auto;
}
.input-field {
  flex: 1;
  min-height: 44px;
  max-height: 120px;
  padding: 1rem 1.5rem;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 0.75rem;
  font-size: 14px;
  font-family: inherit;
  color: #1f2937;
  background: #faf9f6;
  resize: none;
  outline: none;
  transition: all 150ms ease;
  line-height: 1.5;
  box-sizing: border-box;
}
.input-field:focus {
  border-color: #1a5f7a;
  box-shadow: 0 0 0 3px rgba(26, 95, 122, 0.1);
}
.input-field::placeholder {
  color: #9ca3af;
}
.input-field:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.send-btn {
  padding: 1rem 1.5rem;
  background: linear-gradient(135deg, #1a5f7a, #2d7a99);
  color: #ffffff;
  border: none;
  border-radius: 0.75rem;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms ease;
}
.send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(26, 95, 122, 0.3);
}
.send-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.stop-btn {
  padding: 1rem 1.5rem;
  background: #ffffff;
  color: #b42318;
  border: 1px solid rgba(180, 35, 24, 0.35);
  border-radius: 0.75rem;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms ease;
  white-space: nowrap;
}
.stop-btn:hover {
  background: #fef3f2;
  border-color: #b42318;
}
.cancelled-tag {
  margin-left: 6px;
  padding: 1px 6px;
  font-size: 11px;
  font-style: normal;
  color: #b42318;
  background: #fef3f2;
  border-radius: 4px;
}
.input-hint {
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  margin-top: 0.5rem;
}
.welcome-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  text-align: center;
}
.welcome-icon {
  font-size: 64px;
  margin-bottom: 1.5rem;
}
.welcome-title {
  font-size: 24px;
  color: #1f2937;
  margin-bottom: 1rem;
}
.welcome-desc {
  font-size: 14px;
  color: #9ca3af;
  max-width: 400px;
  line-height: 1.6;
}
.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 2rem;
  justify-content: center;
}
.quick-action-btn {
  padding: 0.5rem 1rem;
  background: #ffffff;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 0.75rem;
  font-size: 13px;
  color: #1f2937;
  cursor: pointer;
  transition: all 150ms ease;
}
.quick-action-btn:hover {
  border-color: #1a5f7a;
  color: #1a5f7a;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.bubble :deep(pre) {
  background-color: #2d2d2d;
  color: #f8f8f2;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 10px 0;
}
.bubble :deep(code) {
  font-family: 'Courier New', Courier, monospace;
  background-color: rgba(0,0,0,0.05);
  padding: 2px 4px;
  border-radius: 3px;
}
.bubble :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
}
.bubble :deep(th),
.bubble :deep(td) {
  border: 1px solid #ddd;
  padding: 8px;
  text-align: left;
}
.bubble :deep(th) {
  background-color: #f2f2f2;
}
.bubble :deep(ul),
.bubble :deep(ol) {
  padding-left: 20px;
}
.bubble :deep(li) {
  margin-bottom: 5px;
}
.bubble :deep(h1),
.bubble :deep(h2),
.bubble :deep(h3),
.bubble :deep(h4) {
  margin-top: 16px;
  margin-bottom: 8px;
  color: #333;
}

/* 移动端：侧边栏以抽屉形式浮出，并加遮罩 */
@media (max-width: 767px) {
  .chat-layout {
    position: relative;
  }
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 1000;
  }
  .sidebar-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.45);
    z-index: 999;
  }
}
</style>
