<template>
  <div class="page-container">
    <HeaderBar :title="typeName" />

    <div class="search-box">
      <el-input
        placeholder="搜索店铺名称/地址"
        v-model="searchQuery"
        @input="handleSearch"
        clearable>
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <div class="shop-list" @scroll="onScroll">
      <div class="shop-box" v-for="s in shops" :key="s.id" @click="toDetail(s.id)">
        <div class="shop-img">
          <img :src="s.images || '/imgs/icons/default-icon.jpg'" :alt="s.name" :title="s.name">
        </div>
        <div class="shop-info">
          <div class="shop-title">{{ s.name }}</div>
          <div class="shop-area">
            <span>{{ s.area }}</span>
            <span v-if="s.distance">{{ s.distance < 1000 ? s.distance.toFixed(1) + 'm' : (s.distance / 1000).toFixed(1) + 'km' }}</span>
          </div>
          <div class="shop-avg">￥{{ s.avgPrice }}/人</div>
          <div class="shop-address">
            <el-icon style="color: #8ecae6;"><MapLocation /></el-icon>
            <span>{{ s.address }}</span>
          </div>
        </div>
      </div>

      <div class="empty-state" v-if="shops.length === 0">
        <el-icon :size="48" style="color: #ddd;"><Shop /></el-icon>
        <p>暂无相关店铺</p>
      </div>

      <div class="load-more" v-if="shops.length > 0">
        <template v-if="isLoading">
          <el-icon class="loading-spinner" style="margin-right: 6px;"><Loading /></el-icon>
          <span>加载中...</span>
        </template>
        <template v-else-if="!hasMore">
          <span>没有更多了</span>
        </template>
        <template v-else>
          <span>上拉加载更多</span>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const isLoading = ref(false)
const hasMore = ref(true)
const types = ref([])
const shops = ref([])
const typeName = ref('')
const searchQuery = ref('')
const params = reactive({
  typeId: 0,
  current: 1,
  sortBy: ''
})

onMounted(() => {
  params.typeId = Number(getUrlParam('type')) || 0
  typeName.value = getUrlParam('name')
  queryTypes()
  queryShops()
})

function queryTypes() {
  request.get('/shop-type/list')
    .then(res => {
      types.value = res.data || []
    })
    .catch(err => {
      console.error(err)
      ElMessage.error(typeof err === 'string' ? err : '加载分类失败')
    })
}

async function queryShops() {
  if (isLoading.value || !hasMore.value) return

  isLoading.value = true
  try {
    const res = await request.get('/shop/of/type', { params })
    const data = res.data
    if (!data || data.length === 0) {
      hasMore.value = false
      if (params.current === 1) {
        shops.value = []
      }
    } else {
      data.forEach(s => {
        if (s.images) s.images = s.images.split(',')[0]
      })
      shops.value = shops.value.concat(data)
    }
  } catch (err) {
    console.error(err)
    ElMessage.error(typeof err === 'string' ? err : '加载店铺失败')
  } finally {
    isLoading.value = false
  }
}

function sortAndQuery(sortBy) {
  params.sortBy = sortBy
  queryShops()
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/home')
  }
}

function toDetail(id) {
  router.push('/shop-detail?id=' + id)
}

function handleSearch() {
  params.current = 1
  hasMore.value = true
  shops.value = []

  if (!searchQuery.value || searchQuery.value.trim() === '') {
    delete params.name
    queryShops()
    return
  }

  const searchParams = {
    typeId: params.typeId,
    current: params.current,
    name: searchQuery.value.trim()
  }

  request.get('/shop/search', { params: searchParams })
    .then(res => {
      const data = res.data
      if (!data) {
        shops.value = []
        return
      }
      const shopList = Array.isArray(data) ? data : (data.list || [])
      if (!Array.isArray(shopList) || shopList.length === 0) {
        shops.value = []
        return
      }
      shopList.forEach(s => {
        if (s.images) {
          s.images = s.images.split(',')[0]
        }
      })
      shops.value = shopList
    })
    .catch(err => {
      console.error(err)
      shops.value = []
      ElMessage.error('搜索失败：' + (typeof err === 'string' ? err : ''))
    })
}

function onScroll(e) {
  if (isLoading.value) return

  const scrollTop = e.target.scrollTop
  const clientHeight = e.target.clientHeight
  const scrollHeight = e.target.scrollHeight
  const threshold = 50

  if (scrollTop + clientHeight + threshold >= scrollHeight && hasMore.value) {
    params.current++
    queryShops()
  }
}
</script>

<style scoped>
.page-container {
  background: #f8f8f8;
  min-height: 100vh;
}
.search-box {
  padding: 10px 16px;
  background: #fff;
  position: sticky;
  top: 56px;
  z-index: 99;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.search-box :deep(.el-input__inner) {
  border-radius: 20px;
  background: #f5f5f5;
  border: none;
  padding-left: 35px;
  height: 36px;
}
.search-box :deep(.el-input__prefix) {
  left: 12px;
}
.shop-list {
  padding: 12px;
  height: calc(100vh - 110px);
  overflow-y: auto;
  background: #f8f8f8;
  -webkit-overflow-scrolling: touch;
}
.shop-box {
  display: flex;
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
  transition: all 0.3s ease;
  cursor: pointer;
}
.shop-box:active {
  transform: scale(0.98);
  background: #fafafa;
}
.shop-img {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  overflow: hidden;
  margin-right: 12px;
  flex-shrink: 0;
}
.shop-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.shop-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.shop-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 6px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
.shop-area {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 13px;
  color: #666;
}
.shop-avg {
  color: #f63;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 6px;
}
.shop-address {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #999;
}
.shop-address .el-icon {
  font-size: 14px;
  margin-right: 4px;
}
.shop-address span {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.empty-state {
  text-align: center;
  padding: 40px 0;
  color: #999;
}
.empty-state p {
  font-size: 14px;
  margin-top: 12px;
}
.load-more {
  text-align: center;
  padding: 16px 0;
  color: #999;
  font-size: 13px;
  height: 45px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.loading-spinner {
  animation: rotate 1s linear infinite;
}
@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
