<template>
  <div class="page-container">
    <HeaderBar :show-back="true">
      <template #default>{{ placeDetail.name }}</template>
    </HeaderBar>

    <div class="banner">
      <div class="slide-indicator">{{ currentImageIndex + 1 }}/{{ placeDetail.images.length }}</div>
      <img :src="placeDetail.images[currentImageIndex]" class="banner-image" alt="景点图片">
      <div class="banner-overlay">
        <div class="location-name">{{ placeDetail.name }}</div>
        <div class="location-desc">{{ placeDetail.shortDesc }}</div>
      </div>
    </div>

    <div class="rating-bar">
      <div class="rating-item">
        <div class="rating-value">{{ placeDetail.rating }}</div>
        <div class="rating-label">评分</div>
      </div>
      <div class="rating-item">
        <div class="rating-value">{{ placeDetail.priceType }}</div>
        <div class="rating-label">费用</div>
      </div>
      <div class="rating-item" @click="showPlanDialog">
        <div class="rating-value">AI</div>
        <div class="rating-label">AI规划</div>
      </div>
    </div>

    <!-- AI规划弹窗 -->
    <el-dialog
      title="AI旅行规划"
      v-model="planDialogVisible"
      width="90%"
      :show-close="true"
      :close-on-click-modal="false">
      <div class="plan-form">
        <div class="form-item">
          <div class="form-title">
            <span class="blue-line"></span>
            出行天数
          </div>
          <div class="form-input" @click="showDaysSelector">
            <span>{{ travelDays || '点击选择天数' }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>

        <div class="form-item">
          <div class="form-title">
            <span class="blue-line"></span>
            出行人数
          </div>
          <div class="form-input" @click="showPeopleSelector">
            <span>{{ travelPeople || '点击选择人数' }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>

        <div class="form-item">
          <div class="form-title">
            <span class="blue-line"></span>
            旅行预算
          </div>
          <div class="form-input" @click="showBudgetInput">
            <span>{{ travelBudget || '点击填写预算' }}</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" class="confirm-btn" @click="generatePlan" :disabled="!canGenerate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 天数选择器 -->
    <el-dialog
      title="选择出行天数"
      v-model="daysDialogVisible"
      width="90%"
      append-to-body>
      <div class="selector-list">
        <div class="selector-item" v-for="day in [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]" :key="day" @click="selectDays(day)">
          {{ day }}天
        </div>
      </div>
    </el-dialog>

    <!-- 人数选择器 -->
    <el-dialog
      title="选择出行人数"
      v-model="peopleDialogVisible"
      width="90%"
      append-to-body>
      <div class="selector-list">
        <div class="selector-item" v-for="num in [1,2,3,4,5,6,7,8,9,10]" :key="num" @click="selectPeople(num)">
          {{ num }}人
        </div>
      </div>
    </el-dialog>

    <!-- 预算输入 -->
    <el-dialog
      title="填写旅行预算"
      v-model="budgetDialogVisible"
      width="90%"
      append-to-body>
      <el-input v-model="travelBudget" placeholder="请输入预算金额" type="number">
        <template #prepend>¥</template>
      </el-input>
      <div style="margin-top: 15px; text-align: center;">
        <el-button type="primary" @click="confirmBudget">确定</el-button>
      </div>
    </el-dialog>

    <div class="content-section">
      <div class="section-title">
        <el-icon><Compass /></el-icon> 导览
      </div>
      <div class="ai-guide">
        {{ placeDetail.aiGuide }}
      </div>
    </div>

    <div class="content-section">
      <div class="section-title">
        <el-icon><Camera /></el-icon> 拍照点位
      </div>
      <div class="photo-spots">
        <div v-for="(spot, index) in placeDetail.photoSpots" :key="index">
          <div class="spot-item" @click="toggleSpotDetail(index)">
            <div class="spot-name">{{ spot.name }}</div>
            <el-icon><component :is="spot.showDetail ? 'ArrowDown' : 'ArrowRight'" /></el-icon>
          </div>
          <div class="spot-detail" v-show="spot.showDetail">
            <div class="spot-description">{{ spot.description }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="content-section">
      <div class="info-item">
        <div class="info-label">
          <el-icon><Clock /></el-icon>
          <span>开放时间</span>
        </div>
        <div class="info-value">{{ placeDetail.openingHours }}</div>
      </div>

      <div class="info-item">
        <div class="info-label">
          <el-icon><Phone /></el-icon>
          <span>联系电话</span>
        </div>
        <div class="info-value">
          <a :href="'tel:' + placeDetail.phone" style="color: #666; text-decoration: none;">{{ placeDetail.phone }}</a>
        </div>
      </div>

      <div class="info-item">
        <div class="info-label">
          <el-icon><Location /></el-icon>
          <span>地址</span>
        </div>
        <div class="info-value">{{ placeDetail.address }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElLoading } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const currentImageIndex = ref(0)
const planDialogVisible = ref(false)
const daysDialogVisible = ref(false)
const peopleDialogVisible = ref(false)
const budgetDialogVisible = ref(false)
const travelDays = ref('')
const travelPeople = ref('')
const travelBudget = ref('')
const placeDetail = reactive({
  name: '',
  shortDesc: '',
  rating: '',
  priceType: '',
  popularity: '',
  images: [],
  aiGuide: '',
  photoSpots: [],
  openingHours: '',
  phone: '',
  address: ''
})
const loading = ref(false)
let rotationTimer = null

const canGenerate = computed(() => {
  return travelDays.value && travelPeople.value && travelBudget.value
})

onMounted(() => {
  getPlaceDetail()
})

onBeforeUnmount(() => {
  if (rotationTimer) clearInterval(rotationTimer)
})

function getPlaceDetail() {
  const placeId = getUrlParam('id')
  if (!placeId) {
    ElMessage.error('缺少地点ID参数')
    return
  }

  loading.value = true
  const loadingInstance = ElLoading.service({
    lock: true,
    text: '加载中...',
    background: 'rgba(255, 255, 255, 0.7)'
  })

  request.get(`/place-recommendations/place/${placeId}`)
    .then(res => {
      if (res.success) {
        const data = res.data

        let images = []
        try {
          if (data.images) {
            images = JSON.parse(data.images)
          }
        } catch (e) {
          console.error('解析图片数据失败:', e)
        }
        if (images.length === 0 && data.coverUrl) {
          images = [data.coverUrl]
        }

        Object.assign(placeDetail, {
          name: data.title || '',
          shortDesc: data.shortDesc || '',
          rating: data.rating || '0.0',
          priceType: data.priceType || '未知',
          popularity: data.popularity || '',
          images: images,
          aiGuide: data.aiGuide || '暂无导览信息',
          photoSpots: (data.photoSpots || []).map(spot => ({ ...spot, showDetail: false })),
          openingHours: data.openingHours || '暂无信息',
          phone: data.phone || '暂无',
          address: data.address || '暂无地址信息'
        })

        startImageRotation()
      } else {
        ElMessage.error('获取详情失败')
      }
    })
    .catch(error => {
      console.error('获取地点详情失败:', error)
      ElMessage.error('网络错误，请稍后重试')
    })
    .finally(() => {
      loading.value = false
      loadingInstance.close()
    })
}

function startImageRotation() {
  if (placeDetail.images.length <= 1) return
  if (rotationTimer) clearInterval(rotationTimer)
  rotationTimer = setInterval(() => {
    currentImageIndex.value = (currentImageIndex.value + 1) % placeDetail.images.length
  }, 5000)
}

function showPlanDialog() {
  planDialogVisible.value = true
}
function showDaysSelector() {
  daysDialogVisible.value = true
}
function showPeopleSelector() {
  peopleDialogVisible.value = true
}
function showBudgetInput() {
  budgetDialogVisible.value = true
}
function selectDays(day) {
  travelDays.value = day + '天'
  daysDialogVisible.value = false
}
function selectPeople(num) {
  travelPeople.value = num + '人'
  peopleDialogVisible.value = false
}
function confirmBudget() {
  if (travelBudget.value) {
    budgetDialogVisible.value = false
  } else {
    ElMessage.warning('请输入预算金额')
  }
}

function generatePlan() {
  const planData = {
    place: placeDetail.name,
    days: travelDays.value,
    people: travelPeople.value,
    budget: travelBudget.value,
    date: new Date().toLocaleDateString(),
    loading: true,
    content: ''
  }
  localStorage.setItem('travelPlan', JSON.stringify(planData))
  router.push('/trip-itinerary')
}

function toggleSpotDetail(index) {
  const spot = placeDetail.photoSpots[index]
  spot.showDetail = !spot.showDetail
}
</script>

<style scoped>
/* 样式迁移自原 TOP-detail.html <style> 内联块；body 全局重置不迁移 */
.page-container {
  position: relative;
  min-height: 100vh;
  background-color: #f5f5f5;
}
.banner {
  position: relative;
  width: 100%;
  height: 50vh;
}
.banner-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.banner-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.8), transparent);
  padding: 20px;
  color: white;
}
.location-name {
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 5px;
}
.location-desc {
  font-size: 14px;
  opacity: 0.9;
}
.rating-bar {
  display: flex;
  background-color: #333;
  padding: 15px;
  color: white;
}
.rating-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.rating-value {
  font-size: 20px;
  font-weight: bold;
  color: #FFD700;
  margin-bottom: 5px;
}
.rating-label {
  font-size: 12px;
}
.content-section {
  background: white;
  margin: 10px 0;
  padding: 15px;
}
.section-title {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}
.section-title .el-icon {
  margin-right: 8px;
}
.ai-guide {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
}
.photo-spots {
  margin-top: 15px;
}
.spot-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
  cursor: pointer;
}
.spot-name {
  font-size: 14px;
}
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}
.info-label {
  display: flex;
  align-items: center;
}
.info-label .el-icon {
  margin-right: 8px;
  color: #666;
}
.info-value {
  color: #666;
  font-size: 14px;
}
.slide-indicator {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0,0,0,0.5);
  color: white;
  padding: 3px 8px;
  border-radius: 10px;
  font-size: 12px;
  z-index: 1;
}
.plan-form {
  padding: 10px 0;
}
.form-item {
  margin-bottom: 20px;
}
.form-title {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}
.blue-line {
  display: inline-block;
  width: 3px;
  height: 16px;
  background-color: #00BFFF;
  margin-right: 8px;
}
.form-input {
  background-color: #f5f5f5;
  padding: 15px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}
.confirm-btn {
  width: 100%;
  padding: 12px 0;
  font-size: 16px;
  border-radius: 30px;
}
.selector-list {
  max-height: 300px;
  overflow-y: auto;
}
.selector-item {
  padding: 15px;
  text-align: center;
  border-bottom: 1px solid #eee;
  cursor: pointer;
}
.selector-item:hover {
  background-color: #f5f5f5;
}
.spot-detail {
  padding: 12px 15px;
  background: #f9f9f9;
  border-radius: 8px;
  margin-bottom: 10px;
  font-size: 14px;
  color: #666;
  line-height: 1.6;
}
</style>
