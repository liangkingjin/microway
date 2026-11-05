<template>
  <div class="guide">
    <HeaderBar title="攻略详情" :show-back="true" />
    <div class="progress" :style="{ transform: `scaleX(${progress})` }"></div>

    <div v-if="loading" class="skeleton">
      <div class="skeleton__cover"></div>
      <div class="skeleton__sheet">
        <i v-for="n in 5" :key="n" class="skeleton__line" :style="{ width: n === 5 ? '56%' : '100%' }"></i>
      </div>
    </div>

    <template v-else-if="guide">
      <figure v-if="coverSrc" class="cover">
        <img :src="coverSrc" :alt="guide.title" class="cover__img">
        <div class="cover__veil"></div>
        <figcaption class="cover__meta">
          <span class="kicker">Travel guide · No.{{ guideLabel }}</span>
          <h1 class="title">{{ guide.title }}</h1>
          <p v-if="guide.subtitle" class="subtitle">{{ guide.subtitle }}</p>
        </figcaption>
      </figure>

      <article class="sheet" :class="{ 'sheet--flat': !coverSrc }">
        <div class="byline">
          <span>{{ updatedText }}</span>
          <i class="byline__sep"></i>
          <span>约 {{ readingMinutes }} 分钟读完</span>
        </div>
        <div class="prose" v-html="bodyHtml"></div>
        <div class="endmark"><span>完</span></div>
      </article>
    </template>

    <div v-else class="empty">
      <p>这篇攻略走丢了</p>
      <button class="empty__btn" type="button" @click="goBack">返回上一页</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const guide = ref(null)
const loading = ref(true)
const progress = ref(0)

const INFO_START = /^[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}]/u

const guideLabel = computed(() => String(guide.value?.id ?? 0).padStart(2, '0'))

const coverSrc = computed(() => toAbsolute(guide.value?.images || guide.value?.image || ''))

const readingMinutes = computed(() => {
  const text = (guide.value?.content || '').replace(/<[^>]+>/g, '')
  return Math.max(1, Math.round(text.length / 400))
})

const updatedText = computed(() => {
  const raw = guide.value?.updatedAt || guide.value?.createdAt
  if (!raw) return '官方攻略'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return '官方攻略'
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} 更新`
})

const bodyHtml = computed(() => {
  const raw = guide.value?.content || ''
  if (!raw) return ''

  return raw
    .replace(/src=(['"])([^'"]+)\1/gi, (match, quote, path) => `src=${quote}${toAbsolute(path)}${quote}`)
    .split(/<br\s*\/?>/i)
    .map(chunk => chunk.trim())
    .filter(Boolean)
    .map(chunk => {
      if (/^<img/i.test(chunk)) return `<figure class="prose-figure">${chunk}</figure>`
      if (chunk === '·' || chunk === '•' || chunk === '・') return '<hr class="prose-hr">'
      if (INFO_START.test(chunk)) return `<p class="is-info">${chunk}</p>`
      return `<p>${chunk}</p>`
    })
    .join('')
})

function toAbsolute(path) {
  if (!path) return ''
  const fixed = String(path).replace(/\\/g, '/').trim()
  if (/^(https?:)?\/\//i.test(fixed) || fixed.startsWith('data:')) return fixed
  return fixed.startsWith('/') ? fixed : `/${fixed}`
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function onScroll() {
  const root = document.documentElement
  const max = root.scrollHeight - root.clientHeight
  progress.value = max > 0 ? Math.min(1, Math.max(0, root.scrollTop / max)) : 0
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/guides')
  }
}

function fetchGuide() {
  const guideId = getUrlParam('id')
  if (!guideId) {
    loading.value = false
    return
  }
  request.get('/guides', { params: { id: guideId } })
    .then(res => {
      guide.value = res.data || null
    })
    .catch(error => {
      console.error('获取攻略数据失败:', error)
    })
    .finally(() => {
      loading.value = false
      onScroll()
    })
}

onMounted(() => {
  fetchGuide()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.guide {
  --ink: #23211e;
  --ink-2: #56514a;
  --ink-3: #948d84;
  --paper: #f6f4ef;
  --card: #fffefb;
  --hair: rgba(35, 33, 30, 0.09);
  --accent: #2f7fd8;

  min-height: 100vh;
  padding-bottom: calc(40px + env(safe-area-inset-bottom, 0px));
  background: var(--paper);
  background-image: radial-gradient(rgba(35, 33, 30, 0.028) 1px, transparent 1px);
  background-size: 3px 3px;
  color: var(--ink);
}

.progress {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 1200;
  width: 100%;
  height: 2px;
  transform-origin: 0 50%;
  background: linear-gradient(90deg, var(--accent), #8ecae6);
}

/* ---------- 封面 ---------- */
.cover {
  position: relative;
  height: 58vh;
  min-height: 300px;
  max-height: 520px;
  margin: 0;
  overflow: hidden;
  background: #d9d4cb;
}

.cover__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scale(1.06);
  animation: coverSettle 1.4s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

@keyframes coverSettle {
  to {
    transform: scale(1);
  }
}

.cover__veil {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(24, 21, 18, 0.22) 0%, rgba(24, 21, 18, 0) 32%, rgba(24, 21, 18, 0.74) 100%);
}

.cover__meta {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 0 22px 30px;
  color: #fff;
}

.cover__meta > * {
  animation: rise 0.9s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.kicker {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  opacity: 0.9;
  animation-delay: 0.08s;
}

.kicker::before {
  width: 20px;
  height: 1px;
  background: currentColor;
  content: '';
  opacity: 0.7;
}

.title {
  margin: 10px 0 0;
  font-family: "Songti SC", "Noto Serif SC", "Source Han Serif SC", "STSong", SimSun, serif;
  font-size: clamp(27px, 7.4vw, 46px);
  font-weight: 700;
  line-height: 1.24;
  letter-spacing: 0.01em;
  text-shadow: 0 2px 24px rgba(0, 0, 0, 0.35);
  animation-delay: 0.16s;
}

.subtitle {
  margin: 10px 0 0;
  font-size: 13.5px;
  font-weight: 400;
  line-height: 1.6;
  letter-spacing: 0.02em;
  opacity: 0.86;
  animation-delay: 0.24s;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ---------- 正文 ---------- */
.sheet {
  position: relative;
  z-index: 2;
  width: min(calc(100% - 24px), 720px);
  margin: -26px auto 0;
  padding: 24px 20px 30px;
  background: var(--card);
  border-radius: 20px;
  box-shadow:
    0 18px 40px -24px rgba(35, 33, 30, 0.28),
    0 1px 0 rgba(255, 255, 255, 0.9) inset;
  animation: sheetUp 0.8s cubic-bezier(0.22, 1, 0.36, 1) 0.1s both;
}

.sheet--flat {
  margin-top: 20px;
}

@keyframes sheetUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.byline {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-bottom: 16px;
  margin-bottom: 22px;
  border-bottom: 1px dashed var(--hair);
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--ink-3);
}

.byline__sep {
  width: 3px;
  height: 3px;
  background: currentColor;
  border-radius: 50%;
  opacity: 0.6;
}

.prose {
  font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  font-size: 15.5px;
  line-height: 1.95;
  color: var(--ink-2);
  word-break: break-word;
}

.prose :deep(p) {
  margin: 0 0 15px;
}

.prose :deep(p:last-child) {
  margin-bottom: 0;
}

.prose :deep(p.is-info) {
  padding: 9px 14px;
  margin: 0 0 12px;
  background: linear-gradient(90deg, rgba(47, 127, 216, 0.07), rgba(47, 127, 216, 0));
  border-left: 2px solid rgba(47, 127, 216, 0.55);
  border-radius: 0 8px 8px 0;
  font-weight: 500;
  color: var(--ink);
}

.prose :deep(.prose-figure) {
  margin: 18px 0 20px;
}

.prose :deep(img) {
  display: block;
  width: 100%;
  max-height: 560px;
  object-fit: cover;
  border-radius: 12px;
  box-shadow: 0 10px 24px -14px rgba(35, 33, 30, 0.4);
}

.prose :deep(.prose-hr) {
  width: 44px;
  height: 1px;
  margin: 24px auto;
  border: 0;
  background: var(--hair);
}

.prose :deep(strong),
.prose :deep(b) {
  font-weight: 600;
  color: var(--ink);
}

.prose :deep(a) {
  color: var(--accent);
  text-decoration: none;
  border-bottom: 1px solid rgba(47, 127, 216, 0.3);
}

.endmark {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: center;
  margin-top: 32px;
  font-size: 11px;
  letter-spacing: 0.4em;
  color: var(--ink-3);
}

.endmark::before,
.endmark::after {
  width: 28px;
  height: 1px;
  background: var(--hair);
  content: '';
}

/* ---------- 骨架屏 ---------- */
.skeleton__cover {
  height: 46vh;
  min-height: 240px;
  background: linear-gradient(100deg, #e8e4dc 30%, #f1eee7 50%, #e8e4dc 70%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
}

.skeleton__sheet {
  position: relative;
  width: min(calc(100% - 24px), 720px);
  margin: -26px auto 0;
  padding: 26px 20px;
  background: var(--card);
  border-radius: 20px;
}

.skeleton__line {
  display: block;
  height: 12px;
  margin-bottom: 14px;
  background: #eeebe4;
  border-radius: 6px;
}

@keyframes shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

/* ---------- 空态 ---------- */
.empty {
  padding: 120px 24px;
  text-align: center;
  color: var(--ink-3);
}

.empty__btn {
  margin-top: 16px;
  padding: 9px 22px;
  border: 1px solid rgba(35, 33, 30, 0.16);
  border-radius: 999px;
  background: transparent;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
  transition: all 0.2s ease;
}

.empty__btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

@media (min-width: 768px) {
  .cover {
    height: 62vh;
  }
  .cover__meta {
    padding: 0 8px 40px;
    max-width: 720px;
    margin: 0 auto;
  }
  .sheet {
    padding: 34px 40px 44px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .cover__img,
  .cover__meta > *,
  .sheet,
  .skeleton__cover {
    animation: none;
  }
}
</style>
