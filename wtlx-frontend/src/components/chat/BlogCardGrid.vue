<template>
  <div v-if="cards.length" class="blog-card-grid">
    <div class="grid-title">相关内容</div>
    <div class="grid-body">
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
          <div v-if="card.summary" class="card-summary">{{ card.summary }}</div>
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
.blog-card-grid {
  margin-top: 10px;
  border-top: 1px solid #edf2f7;
  padding-top: 10px;
}
.grid-title {
  font-size: 12px;
  color: #a0aec0;
  margin-bottom: 8px;
}
.grid-body {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
}
.blog-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.blog-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.card-cover {
  position: relative;
  height: 84px;
  background-color: #edf2f7;
  background-size: cover;
  background-position: center;
}
.card-type {
  position: absolute;
  left: 6px;
  top: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: #fff;
  background: rgba(45, 55, 72, 0.75);
}
.card-info {
  padding: 8px;
}
.card-title {
  font-size: 13px;
  color: #2d3748;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta {
  margin-top: 4px;
  font-size: 11px;
  color: #a0aec0;
  display: flex;
  gap: 8px;
}
.card-summary {
  margin-top: 4px;
  font-size: 11px;
  color: #718096;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
@media (max-width: 480px) {
  .grid-body {
    grid-template-columns: 1fr;
  }
}
</style>
