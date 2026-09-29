// AI 能力已收口进业务服务（WeiTu，端口 9528），统一走 /api 前缀：
//   dev 环境经 vite proxy：/api/ai/react/* → http://localhost:9528/ai/react/*
//   生产环境经 nginx 反代：/api/ai/react/* → 9528/ai/react/*（去前缀，与业务接口一致）
// 注意：AI 接口返回裸对象/字符串，无 success 包装，绝不能复用 request 实例（axios 拦截器会误判失败）
// 一律使用 fetch / EventSource
const AI_BASE = '/api'

export function getAIBase() {
  return AI_BASE
}

// GET /ai/react/history?threadId= → { threadId, context: [{role, content}] }
// role 为 USER/ASSISTANT/SYSTEM（大写），按时间正序，可能含摘要 SystemMessage
export async function fetchHistory(threadId) {
  if (!threadId) return { context: [] }
  const res = await fetch(`${AI_BASE}/ai/react/history?threadId=${encodeURIComponent(threadId)}`)
  return res.json()
}

// DELETE /ai/react/history?threadId= 清空该线程记忆
export function deleteHistory(threadId) {
  return fetch(`${AI_BASE}/ai/react/history?threadId=${encodeURIComponent(threadId)}`, { method: 'DELETE' })
}

// SSE GET /ai/react/trip/chat?message=&threadId=&requestId=
// requestId 由前端生成，是取消这条流的唯一凭据（多实例下服务端靠它定位到持流实例）
// 协议（ReactAgent 流式）：
//   event: delta     —— 模型增量文本，边生成边推送（可多次）
//   event: done      —— 正常结束，data 携带本轮完整回答
//   event: cancelled —— 被取消（用户停止/断开/新流挤占），data 携带已生成部分
//   event: error     —— 异常信息（MessageEvent 带 data）
// 连接会在服务端 complete 后关闭（无 [DONE] 哨兵）
export function openChatStream(message, threadId, requestId) {
  const qs = `message=${encodeURIComponent(message)}&threadId=${encodeURIComponent(threadId)}&requestId=${encodeURIComponent(requestId || '')}`
  return new EventSource(`${AI_BASE}/ai/react/trip/chat?${qs}`)
}

// POST /ai/react/cancel?requestId= 取消一次正在进行的生成（可打到任意实例，服务端内部广播）
// 返回 { requestId, status }，status: cancelled=已取消 / forwarded=已广播 / rejected=ID 非法
export async function cancelChat(requestId) {
  if (!requestId) return { status: 'rejected' }
  const res = await fetch(`${AI_BASE}/ai/react/cancel?requestId=${encodeURIComponent(requestId)}`, {
    method: 'POST'
  })
  return res.json()
}
