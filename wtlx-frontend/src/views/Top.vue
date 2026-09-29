<template>
  <div class="page-container">
    <HeaderBar title="热门推荐" :show-back="true" />
    <div class="recommendation-card" v-for="(item, index) in recommendations" :key="index" @click="goToDetail(item)">
      <div class="top-tag">
        <div class="top-number">TOP {{ padNumber(index + 1) }}</div>
      </div>
      <img :src="item.coverUrl" :alt="item.title" class="card-image">
      <div class="location-name">{{ item.title }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElLoading } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'

const router = useRouter()

const recommendations = ref([])
const loading = ref(false)

onMounted(() => {
  loadRecommendations()
})

function padNumber(num) {
  return num.toString().padStart(2, '0')
}

function goToDetail(item) {
  router.push('/top-detail?id=' + item.id)
}

function loadRecommendations() {
  loading.value = true
  const loadingInstance = ElLoading.service({
    lock: true,
    text: '加载中...',
    background: 'rgba(255, 255, 255, 0.7)'
  })

  request.get('/place-recommendations')
    .then(res => {
      if (res.success) {
        recommendations.value = res.data.map(item => ({
          id: item.id,
          title: item.title,
          coverUrl: item.coverUrl || '/imgs/icons/default-icon.jpg'
        }))
      } else {
        ElMessage.error('加载失败')
      }
    })
    .catch(error => {
      console.error('加载推荐失败:', error)
      ElMessage.error('网络错误，请稍后重试')
    })
    .finally(() => {
      loading.value = false
      loadingInstance.close()
    })
}
</script>

<style scoped>
/* 样式迁移自原 TOP.html <style> 内联块 */
.page-container {
  background: #f8f8f8;
  min-height: 100vh;
  padding-bottom: 20px;
}
.recommendation-card {
  margin: 15px;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  position: relative;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
  cursor: pointer;
}
.top-tag {
  position: absolute;
  top: 20px;
  left: 20px;
  background: #FFE0B2;
  padding: 8px 16px;
  border-radius: 8px;
  z-index: 1;
}
.top-number {
  font-size: 24px;
  font-weight: bold;
  color: #FF6B00;
}
.card-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  display: block;
}
.location-name {
  font-size: 24px;
  font-weight: bold;
  color: #fff;
  position: absolute;
  bottom: 60px;
  left: 20px;
  text-shadow: 0 2px 4px rgba(0,0,0,0.3);
}
</style>
