<template>
  <div v-if="cards.length" class="blog-card-strip">
    <div class="strip-title">参考游记与攻略</div>
    <div class="strip-body">
      <div
        v-for="card in cards"
        :key="card.sourceType + '-' + card.sourceId"
        class="blog-card"
        @click="open(card)"
      >
        <div class="card-cover" :style="coverStyle(card)">
          <span class="card-type">{{ card.sourceType === 'GUIDE' ? '攻略' : '游记' }}</span>
        </div>
        <div class="card-info">
          <div class="card-title">{{ card.title }}</div>
          <div class="card-meta">
            <span v-if="card.authorName" class="card-author">{{ card.authorName }}</span>
            <span v-if="card.liked" class="card-liked">{{ card.liked }} 赞</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

// cards: [{ sourceType, sourceId, title, cover, authorName, liked, summary, score }]
// 约定：未检索到相关内容时 cards 为空数组，此时整块不渲染
const props = defineProps({
  cards: { type: Array, default: () => [] }
})

const router = useRouter()

function coverStyle(card) {
  if (!card.cover) return {}
  return { backgroundImage: 'url(' + card.cover + ')' }
}

function open(card) {
  if (card.sourceType === 'GUIDE') {
    router.push({ path: '/gonglue-detail', query: { id: card.sourceId } })
    return
  }
  router.push({ path: '/blog-detail', query: { id: card.sourceId } })
}
</script>

<style scoped>
/* 挂在回答气泡内部的附属分区：用一条细分隔线与正文隔开，属于同一条消息 */
.blog-card-strip {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #edf2f7;
  /* 气泡正文是 1.85 行高，卡片区要收回常规行高，否则标题与标签会被撑松 */
  line-height: 1.5;
}
.strip-title {
  font-size: 12px;
  color: #a0aec0;
  margin-bottom: 8px;
}
.strip-body {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
  /* 横向滑动停下时按卡片吸附，末端不会停在半张卡上 */
  scroll-snap-type: x proximity;
  -webkit-overflow-scrolling: touch;
}
/* 细滚动条，不抢正文视觉 */
.strip-body::-webkit-scrollbar {
  height: 4px;
}
.strip-body::-webkit-scrollbar-thumb {
  background: #e2e8f0;
  border-radius: 2px;
}
.blog-card {
  flex: 0 0 auto;
  width: 216px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  scroll-snap-align: start;
  transition: box-shadow 0.2s, transform 0.2s;
}
.blog-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
/* 封面收成小方图，横向条才够紧凑 */
.card-cover {
  position: relative;
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: 6px;
  background-color: #edf2f7;
  background-size: cover;
  background-position: center;
}
.card-type {
  position: absolute;
  left: 0;
  bottom: 0;
  padding: 0 4px;
  border-radius: 0 5px 0 6px;
  font-size: 9px;
  line-height: 13px;
  color: #fff;
  background: rgba(45, 55, 72, 0.78);
}
/* min-width: 0 是标题能省略号截断的前提（flex 子项默认不收缩） */
.card-info {
  min-width: 0;
}
.card-title {
  font-size: 12px;
  color: #2d3748;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta {
  margin-top: 3px;
  font-size: 10px;
  color: #a0aec0;
  display: flex;
  gap: 6px;
}
</style>
