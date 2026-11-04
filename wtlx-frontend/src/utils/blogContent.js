/**
 * 游记正文的格式处理。
 *
 * 背景：正文以纯文本存库（textarea 输入，保留 \n 与空格），而详情页曾直接用
 * v-html 渲染纯文本，HTML 会把连续空白折叠、把 \n 当普通空白 —— 于是换行和
 * 缩进全丢。历史数据里又存有早期写入的 <br> 标记，所以渲染要分两条路：
 * 已是富文本的保持原样，纯文本的转义后再把 \n、连续空格还原成 HTML。
 */

// 只识别"块级/换行类"标签，避免把用户正文里的 "a < b > c" 误判成 HTML
const HTML_TAG = /<\/?(?:br|p|div|span|img|strong|b|em|i|u|s|h[1-6]|ul|ol|li|blockquote|a|font|section)\b[^>]*>/i

export function isRichHtml(text) {
  return HTML_TAG.test(text || '')
}

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * 提交前归一化：统一换行符，去掉首尾空行，保留中间的换行与空格。
 */
export function normalizeContent(text) {
  return String(text ?? '')
    .replace(/\r\n?/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

/**
 * 正文 → 可安全放进 v-html 的 HTML 片段。
 * - 历史富文本（含 <br> 等标签）：原样返回，避免二次转义变成字面标签
 * - 纯文本：转义后把换行变 <br>、连续空格变 &nbsp;，保住编辑时的排版
 */
export function toDisplayHtml(text) {
  const raw = String(text ?? '').replace(/\r\n?/g, '\n')
  if (!raw) return ''
  if (isRichHtml(raw)) return raw
  return escapeHtml(raw)
    .replace(/\n/g, '<br>')
    .replace(/ {2,}/g, spaces => '&nbsp;'.repeat(spaces.length))
}

/**
 * 卡片摘要用：剥掉标签、折叠空白，避免列表里露出 <br> 之类字面标记。
 */
export function toPlainSummary(text, limit = 0) {
  const plain = String(text ?? '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (limit > 0 && plain.length > limit) return plain.slice(0, limit) + '…'
  return plain
}
