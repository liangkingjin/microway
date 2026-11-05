<template>
  <div class="page-container">
    <HeaderBar title="找攻略" :show-back="true" />

    <div class="content">
      <div class="sidebar">
        <div
          v-for="province in provinces"
          :key="province.id"
          class="sidebar-item"
          :class="{ active: activeSidebar === province.id }"
          @click="changeSidebar(province.id)"
        >
          {{ province.name }}
        </div>
      </div>

      <div class="main-content">
        <div class="section-title">
          推荐
          <i class="section-title-icon">🌸</i>
        </div>

        <div class="card-grid">
          <div
            class="card"
            v-for="(item, index) in currentCards"
            :key="index"
            @click="goToDetail(item)"
          >
            <img :src="item.image" alt="">
            <div class="card-tag">官方攻略</div>
            <div class="card-content">
              <h3 class="card-title">{{ item.title }}</h3>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'

const router = useRouter()

const activeSidebar = ref('')
const provinces = ref([])
const currentCards = ref([])

function fetchProvinces() {
  request.get('/provinces')
    .then(res => {
      provinces.value = res.data || []
      if (provinces.value.length > 0) {
        activeSidebar.value = provinces.value[0].id
        fetchProvinceGuides(provinces.value[0].id)
      }
    })
    .catch(error => {
      console.error('获取省份列表失败:', error)
    })
}

function changeSidebar(sidebar) {
  activeSidebar.value = sidebar
  fetchProvinceGuides(sidebar)
}

function fetchProvinceGuides(province) {
  request.get(`/guides/${province}`)
    .then(res => {
      currentCards.value = res.data || []
    })
    .catch(error => {
      console.error(`获取${province}攻略失败:`, error)
    })
}

function goToDetail(item) {
  router.push(`/guide-detail?id=${item.id}`)
}

onMounted(() => {
  fetchProvinces()
})
</script>

<style scoped>
.page-container {
  background-color: #e6f2ff;
  min-height: 100vh;
}
.content {
  display: flex;
  height: calc(100vh - 50px);
  overflow: hidden;
}
.sidebar {
  width: 80px;
  background-color: #fff;
  padding: 10px 0;
  overflow-y: auto;
  box-shadow: 1px 0 5px rgba(0, 0, 0, 0.05);
}
.sidebar-item {
  padding: 10px 0;
  text-align: center;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  border-left: 3px solid transparent;
}
.sidebar-item.active {
  color: #4d9de0;
  font-weight: bold;
  border-left: 3px solid #4d9de0;
}
.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 10px 15px;
  background-color: #e6f2ff;
}
.section-title {
  font-size: 16px;
  font-weight: bold;
  margin: 15px 0 10px;
  display: flex;
  align-items: center;
  color: #333;
}
.section-title-icon {
  margin-left: 5px;
  font-size: 14px;
  color: #ff6b81;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 20px;
}
.card {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: relative;
  height: 180px;
  background-color: #fff;
}
.card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card-tag {
  position: absolute;
  top: 10px;
  left: 10px;
  background-color: #4d9de0;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  display: flex;
  align-items: center;
}
.card-content {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 10px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
  color: #fff;
}
.card-title {
  font-size: 14px;
  margin-bottom: 5px;
}
</style>
