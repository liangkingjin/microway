<template>
  <div class="spot-page">
    <HeaderBar :title="spot?.name || name || '景点详情'" :show-back="true" />

    <div class="spot-body">
      <!-- 首次生成需要 20-60 秒（文案 + 文生图），给足过程反馈避免被当成卡死 -->
      <div v-if="loading" class="spot-loading">
        <span class="spot-loading__ring"></span>
        <p class="spot-loading__title">AI 正在生成「{{ name }}」的介绍与图片</p>
        <p class="spot-loading__sub">首次生成约需 30-60 秒，生成一次后即永久保存</p>
      </div>

      <div v-else-if="spot" class="spot-content">
        <div v-if="spot.images && spot.images.length" class="spot-gallery">
          <img
            v-for="(img, i) in spot.images"
            :key="i"
            :src="img"
            :alt="spot.name + ' 实景图 ' + (i + 1)"
            loading="lazy"
            @error="onImgError(i)"
          >
        </div>
        <div v-else class="spot-cover-placeholder">
          <span class="spot-cover-placeholder__char">{{ coverChar }}</span>
          <span class="spot-cover-placeholder__hint">站内暂无该景点的实拍图</span>
        </div>

        <h1 class="spot-name">{{ spot.name }}</h1>

        <section class="spot-section">
          <h2 class="spot-section__title">景点介绍</h2>
          <p class="spot-section__text">{{ spot.intro }}</p>
        </section>

        <section v-if="spot.tips && spot.tips.length" class="spot-section">
          <h2 class="spot-section__title">游览建议</h2>
          <ul class="spot-section__list">
            <li v-for="(t, i) in spot.tips" :key="i">{{ t }}</li>
          </ul>
        </section>
      </div>

      <div v-else class="spot-error">
        <p class="spot-error__text">{{ errorMsg || '加载失败' }}</p>
        <button type="button" class="spot-error__btn" @click="load">重试</button>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 景点 AI 详情页：从总体路线动线或行程条目进入，内容（介绍 / 游览建议 / 图片）
 * 全部由后端调大模型与文生图生成并缓存，本页只负责轮询与展示。
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index.js'

const POLL_INTERVAL = 3000
const POLL_MAX = 40

const name = String(getUrlParam('name') || '').trim()
const loading = ref(true)
const spot = ref(null)
const errorMsg = ref('')
// 没有配图时用景点名首字 + 渐变做占位，比一块空白更像"确实缺图"而不是页面坏了
const coverChar = computed(() => (name ? name.slice(0, 1) : '景'))
let disposed = false
let pollTimer = null

function sleep(ms) {
  return new Promise((resolve) => {
    pollTimer = setTimeout(resolve, ms)
  })
}

async function load() {
  if (!name) {
    loading.value = false
    spot.value = null
    errorMsg.value = '缺少景点名称'
    return
  }
  loading.value = true
  spot.value = null
  errorMsg.value = ''
  try {
    for (let i = 0; i < POLL_MAX && !disposed; i++) {
      const res = await request.get('/ai/scenic-info', { params: { name } })
      const data = res.data || {}
      if (data.status === 'DONE') {
        spot.value = data
        loading.value = false
        return
      }
      if (data.status === 'FAILED') {
        errorMsg.value = data.intro || 'AI 生成暂时不可用，请稍后再试'
        loading.value = false
        return
      }
      await sleep(POLL_INTERVAL)
    }
    if (!disposed) {
      errorMsg.value = '生成超时，请稍后刷新重试'
      loading.value = false
    }
  } catch (e) {
    if (disposed) return
    errorMsg.value = typeof e === 'string' ? e : '加载失败，请检查网络'
    loading.value = false
  }
}

function onImgError(index) {
  // 单张图片加载失败就移除，避免留一整块破图
  if (spot.value && Array.isArray(spot.value.images)) {
    spot.value.images.splice(index, 1)
  }
}

onMounted(load)
onBeforeUnmount(() => {
  disposed = true
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<style scoped>
.spot-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding-bottom: 32px;
  background: #f5f7fa;
}
.spot-body {
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
}
/* ---- 加载态 ---- */
.spot-loading {
  padding: 64px 16px;
  text-align: center;
}
.spot-loading__ring {
  display: inline-block;
  width: 36px;
  height: 36px;
  border: 3px solid #dbe7f3;
  border-top-color: var(--wt-color-brand, #409eff);
  border-radius: 50%;
  animation: spot-spin 0.9s linear infinite;
}
@keyframes spot-spin {
  to { transform: rotate(360deg); }
}
.spot-loading__title {
  margin: 16px 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: #2c3e50;
}
.spot-loading__sub {
  font-size: 12px;
  color: #909399;
}
/* ---- 图片 ---- */
.spot-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}
.spot-gallery img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 10px;
  background: #e8edf2;
}
/* 无图占位：渐变底 + 景点首字，保持与图片卡片一致的视觉体量 */
.spot-cover-placeholder {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 7;
  border-radius: 10px;
  background: linear-gradient(135deg, #e8f2fb 0%, #d6e6f7 55%, #cfe0f5 100%);
  overflow: hidden;
}
.spot-cover-placeholder__char {
  font-size: clamp(56px, 16vw, 120px);
  font-weight: 700;
  color: rgba(64, 158, 255, 0.35);
  letter-spacing: 4px;
  user-select: none;
}
.spot-cover-placeholder__hint {
  position: absolute;
  bottom: 10px;
  font-size: 11px;
  color: rgba(64, 158, 255, 0.7);
}
/* ---- 文字 ---- */
.spot-name {
  margin: 18px 0 4px;
  font-size: 22px;
  color: #1f2937;
}
.spot-section {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e8edf2;
}
.spot-section__title {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--wt-color-brand, #409eff);
}
.spot-section__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #374151;
  white-space: pre-wrap;
}
.spot-section__list {
  margin: 0;
  padding-left: 18px;
  font-size: 14px;
  line-height: 1.9;
  color: #374151;
}
.spot-section__list li { margin-bottom: 4px; }
/* ---- 失败态 ---- */
.spot-error {
  padding: 64px 16px;
  text-align: center;
}
.spot-error__text {
  font-size: 14px;
  color: #909399;
}
.spot-error__btn {
  margin-top: 14px;
  padding: 8px 24px;
  border: 1px solid var(--wt-color-brand, #409eff);
  border-radius: 18px;
  background: #fff;
  color: var(--wt-color-brand, #409eff);
  font-size: 13px;
  cursor: pointer;
}
.spot-error__btn:hover { background: #ecf5ff; }
</style>
