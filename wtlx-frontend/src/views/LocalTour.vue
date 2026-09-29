<template>
  <div class="page-container">
    <HeaderBar title="当地游" :show-back="true" />

    <div class="search-box">
      <el-input
        placeholder="搜索目的地/景点"
        v-model="searchQuery"
        @input="handleSearch"
      >
        <template #prefix><el-icon style="color: #909399; margin: 0 5px;"><Search /></el-icon></template>
      </el-input>
    </div>

    <div class="scenic-list">
      <div v-for="item in scenicSpots" :key="item.id" class="scenic-item" @click="goToDetail(item)">
        <div class="scenic-content">
          <img :src="item.imageUrl || '/imgs/icons/default-icon.jpg'" :alt="item.name" class="scenic-image">
          <div class="scenic-info">
            <div class="scenic-name">{{ item.name }}</div>
            <div class="scenic-rating">{{ item.level }} {{ item.rating }}分 {{ item.visitCount }}人评价</div>
            <div class="scenic-distance">距离：{{ item.distance }}km</div>
            <div class="scenic-price">¥{{ item.price }}起</div>
          </div>
        </div>
        <div class="action-buttons">
          <button class="action-button">可订明日</button>
          <button class="action-button">随时退</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'

const router = useRouter()

const searchQuery = ref('')
const scenicSpots = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(100)
const total = ref(0)
let searchTimer = null

function loadScenicSpots() {
  const params = {
    keyword: searchQuery.value,
    page: page.value,
    pageSize: pageSize.value
  }
  request.get('/scenic-spots', { params })
    .then(res => {
      const data = res.data
      scenicSpots.value = data.list || data
      total.value = data.total || (Array.isArray(data) ? data.length : 0)
    })
    .catch(err => {
      console.log(err)
      ElMessage.error(err?.response?.data?.errorMsg || err?.message || '加载失败')
    })
}

function handleSearch() {
  page.value = 1
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    loadScenicSpots()
  }, 300)
}

function goToDetail(item) {
  router.push(`/scenic-detail?id=${item.id}`)
}

onMounted(() => {
  loadScenicSpots()
})
</script>

<style scoped>
.page-container {
  background: #f8f8f8;
  min-height: 100vh;
}
.search-box {
  padding: 10px 15px;
  background: #fff;
}
.scenic-list {
  background: #f5f5f5;
  padding: 10px 0;
}
.scenic-item {
  background: #fff;
  margin: 0 10px 10px;
  border-radius: 8px;
  overflow: hidden;
}
.scenic-content {
  display: flex;
  padding: 12px;
  position: relative;
}
.scenic-image {
  width: 120px;
  height: 80px;
  border-radius: 4px;
  object-fit: cover;
}
.scenic-info {
  flex: 1;
  margin-left: 12px;
  position: relative;
}
.scenic-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}
.scenic-rating {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}
.scenic-distance {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.scenic-price {
  position: absolute;
  right: 0;
  bottom: 0;
  color: #f56c6c;
  font-size: 16px;
  font-weight: bold;
}
.action-buttons {
  display: flex;
  padding: 0 12px 8px;
  gap: 6px;
}
.action-button {
  flex: 1;
  font-size: 11px;
  padding: 2px 0;
  text-align: center;
  border-radius: 2px;
  border: 1px solid #409EFF;
  color: #409EFF;
  background: none;
  max-width: 60px;
}
</style>
