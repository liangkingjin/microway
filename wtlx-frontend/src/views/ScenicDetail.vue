<template>
  <div class="page-container">
    <HeaderBar title="景点详情" :show-back="true" />

    <div class="banner">
      <img :src="scenic.imageUrl || '/imgs/icons/default-icon.jpg'" :alt="scenic.name" :title="scenic.name">
    </div>

    <div class="scenic-info">
      <div class="scenic-title">{{ scenic.name }}</div>
      <div class="scenic-rating">
        <span class="rating-score">{{ scenic.level }} {{ scenic.rating }}分</span>
        <span class="rating-count">{{ scenic.visitCount }}条评价</span>
      </div>
      <div class="scenic-status" :class="{ closed: scenic.status !== 1 }">
        {{ scenic.status === 1 ? '开放中' : '已关闭' }}
      </div>
      <div class="scenic-time">今日({{ getWeekDay() }}) {{ scenic.openingTime }}开放</div>
      <div class="scenic-address">
        <span class="address-text">{{ scenic.address }}</span>
        <span class="map-link" @click="showMap">地图</span>
      </div>
      <div class="notice" v-if="scenic.notice">
        <el-icon style="margin-right: 5px;"><Warning /></el-icon>
        <span>{{ scenic.notice }}</span>
      </div>
    </div>

    <div class="booking-section">
      <div class="booking-header">门票</div>
      <div class="booking-dates">
        <div class="date-item" :class="{ active: selectedDate === 'today' }" @click="selectedDate = 'today'">
          <div>今日</div>
          <div>{{ today }}</div>
        </div>
        <div class="date-item" :class="{ active: selectedDate === 'tomorrow' }" @click="selectedDate = 'tomorrow'">
          <div>明日</div>
          <div>{{ tomorrow }}</div>
        </div>
      </div>
      <div v-for="(ticket, key) in ticketList" :key="key" class="ticket-item">
        <div class="ticket-header">
          <span class="ticket-name">{{ ticket.name }}</span>
          <span class="ticket-price">¥{{ ticket.price }}</span>
        </div>
        <div class="ticket-desc">{{ ticket.description }}</div>
        <div class="ticket-tags">
          <span class="ticket-tag">{{ ticket.refund_type }}</span>
          <span class="ticket-tag">{{ ticket.booking_type }}</span>
        </div>
      </div>
    </div>

    <div style="height: 60px;"></div>

    <div class="bottom-bar">
      <div class="action-icon">
        <el-icon style="font-size: 20px; margin-bottom: 2px;"><Star /></el-icon>
        <span>收藏</span>
      </div>
      <div class="action-icon">
        <el-icon style="font-size: 20px; margin-bottom: 2px;"><Share /></el-icon>
        <span>分享</span>
      </div>
      <button class="book-button" @click="showBookingDialog">立即预订</button>
    </div>

    <!-- 预订弹窗 -->
    <el-dialog
      title="选择门票"
      v-model="bookingDialogVisible"
      width="90%"
      :close-on-click-modal="false"
    >
      <div class="booking-dialog-content">
        <div class="selected-date">
          <span>选择日期：</span>
          <span class="date-text">{{ selectedDate === 'today' ? today : tomorrow }}</span>
        </div>
        <div class="ticket-selection">
          <div v-for="(ticket, key) in ticketList" :key="key" class="ticket-option">
            <div class="ticket-info">
              <div class="ticket-name">{{ ticket.name }}</div>
              <div class="ticket-price">¥{{ ticket.price }}</div>
              <div class="ticket-desc">{{ ticket.description }}</div>
            </div>
            <div class="ticket-quantity">
              <el-input-number
                v-model="selectedTickets[key]"
                :min="0"
                :max="10"
                size="small"
                @change="(val) => handleQuantityChange(val, key)"
              ></el-input-number>
            </div>
          </div>
        </div>
        <div class="booking-summary">
          <div class="summary-item">
            <span>总价：</span>
            <span class="total-price">¥{{ totalPrice }}</span>
          </div>
          <div class="summary-item">
            <span>总数量：</span>
            <span>{{ totalQuantity }}张</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="bookingDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBooking" :disabled="totalQuantity === 0">确认预订</el-button>
      </template>
    </el-dialog>

    <!-- 地图弹窗 -->
    <div class="map-dialog" v-if="mapVisible">
      <div class="map-header">
        <div class="map-title">{{ scenic.name }}</div>
        <div class="map-close" @click="mapVisible = false">
          <el-icon><Close /></el-icon>
        </div>
      </div>
      <div id="mapContainer"></div>
      <div class="map-info">
        <div class="map-info-title">{{ scenic.name }}</div>
        <div class="map-info-address">{{ scenic.address }}</div>
        <div class="map-info-distance" v-if="distance">距离您约{{ distance }}米</div>
      </div>
      <div class="map-copyright">
        © 2025 Baidu - GS(2023)3206号 - 甲测资字1111342 - 京ICP证030173号 - Data © 百度智图
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const selectedDate = ref('today')
const bookingDialogVisible = ref(false)
const selectedTickets = reactive({})
const scenic = ref({
  id: null,
  name: '',
  imageUrl: '',
  level: '',
  rating: 0,
  visitCount: 0,
  address: '',
  price: 0,
  openingTime: '',
  status: 0,
  notice: '',
  ticketInfo: {}
})
const mapVisible = ref(false)
const distance = ref(null)
let map = null
let geocoder = null

const today = computed(() => formatDate(new Date()))
const tomorrow = computed(() => {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return formatDate(date)
})

const ticketList = computed(() => {
  try {
    return typeof scenic.value.ticketInfo === 'string'
      ? JSON.parse(scenic.value.ticketInfo)
      : (scenic.value.ticketInfo || {})
  } catch (e) {
    return {}
  }
})

const totalPrice = computed(() => {
  if (!ticketList.value || !selectedTickets) return 0
  return Object.entries(selectedTickets).reduce((total, [key, quantity]) => {
    const ticket = ticketList.value[key]
    quantity = parseInt(quantity) || 0
    if (ticket && quantity > 0) {
      return total + (ticket.price * quantity)
    }
    return total
  }, 0).toFixed(2)
})

const totalQuantity = computed(() => {
  let total = 0
  Object.values(selectedTickets).forEach(quantity => {
    total += parseInt(quantity) || 0
  })
  return total
})

function formatDate(date) {
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function getWeekDay() {
  const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  const date = new Date()
  return days[date.getDay()]
}

function handleQuantityChange(value, key) {
  selectedTickets[key] = parseInt(value) || 0
}

function showBookingDialog() {
  bookingDialogVisible.value = true
  Object.keys(selectedTickets).forEach(k => delete selectedTickets[k])
  Object.keys(ticketList.value).forEach(key => {
    selectedTickets[key] = 0
  })
}

function confirmBooking() {
  if (totalQuantity.value === 0) {
    ElMessage.warning('请选择至少一张门票')
    return
  }

  const visitDate = selectedDate.value === 'today'
    ? formatDate(new Date())
    : formatDate(new Date(new Date().setDate(new Date().getDate() + 1)))

  const orderData = {
    scenicId: scenic.value.id,
    scenicName: scenic.value.name,
    visitDate: visitDate,
    tickets: JSON.stringify(Object.entries(selectedTickets)
      .filter(([_, quantity]) => quantity > 0)
      .map(([type, quantity]) => ({
        type,
        name: ticketList.value[type].name,
        price: ticketList.value[type].price,
        quantity: parseInt(quantity)
      }))),
    totalPrice: parseFloat(totalPrice.value),
    totalQuantity: totalQuantity.value,
    orderTime: new Date().toISOString()
  }

  request.post('/orders', orderData)
    .then(res => {
      if (res.success) {
        ElMessage.success('预订成功！')
        bookingDialogVisible.value = false
      } else {
        ElMessage.error(res.message || res.errorMsg || '预订失败')
      }
    })
    .catch(err => {
      console.error(err)
      const errorMsg = err.response?.data?.message || err.response?.data?.errorMsg || err.response?.data?.error || err.message || '预订失败，请稍后重试'
      ElMessage.error(errorMsg)
    })
}

function loadScenicDetail(id) {
  request.get(`/scenic-spots/${id}`)
    .then(res => {
      scenic.value = res.data
    })
    .catch(err => {
      console.error(err)
      ElMessage.error('加载失败')
      setTimeout(() => router.back(), 1500)
    })
}

// 动态加载百度地图 GL 脚本（与 map.html 不同的 ak，按原页保留）
let bmapPromise = null
function loadBMapGL() {
  if (window.BMapGL) return Promise.resolve(window.BMapGL)
  if (bmapPromise) return bmapPromise
  bmapPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = '//api.map.baidu.com/api?type=webgl&v=1.0&ak=MZZeQYAyRjGoHdKCNdD9R2IzHFDRbsZ3'
    script.onload = () => resolve(window.BMapGL)
    script.onerror = reject
    document.head.appendChild(script)
  })
  return bmapPromise
}

async function showMap() {
  mapVisible.value = true
  await loadBMapGL()
  await nextTick()
  initMap()
}

function initMap() {
  const BMapGL = window.BMapGL
  map = new BMapGL.Map('mapContainer')
  geocoder = new BMapGL.Geocoder()

  geocoder.getPoint(scenic.value.address, (point) => {
    if (point) {
      map.centerAndZoom(point, 15)
      const marker = new BMapGL.Marker(point)
      map.addOverlay(marker)

      const infoWindow = new BMapGL.InfoWindow(`
        <div style="padding: 10px;">
          <div style="font-weight: bold; margin-bottom: 5px;">${scenic.value.name}</div>
          <div style="color: #666;">${scenic.value.address}</div>
        </div>
      `)
      marker.addEventListener('click', () => {
        map.openInfoWindow(infoWindow, point)
      })

      getCurrentLocation(point)
    }
  })
}

function getCurrentLocation(destinationPoint) {
  const BMapGL = window.BMapGL
  const geolocation = new BMapGL.Geolocation()
  geolocation.getCurrentPosition((r) => {
    if (r.point) {
      const currentPoint = r.point
      distance.value = Math.round(map.getDistance(currentPoint, destinationPoint))

      const currentMarker = new BMapGL.Marker(currentPoint)
      map.addOverlay(currentMarker)

      const driving = new BMapGL.DrivingRoute(map, {
        renderOptions: {
          map: map,
          autoViewport: true
        }
      })
      driving.search(currentPoint, destinationPoint)
    }
  })
}

onMounted(() => {
  const id = getUrlParam('id')
  if (id) {
    loadScenicDetail(id)
  } else {
    ElMessage.error('参数错误')
    setTimeout(() => router.back(), 1500)
  }
})
</script>

<style scoped>
.page-container {
  background: #f5f5f5;
  min-height: 100vh;
}
.banner {
  position: relative;
  width: 100%;
  height: 200px;
}
.banner img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.scenic-info {
  background: #fff;
  padding: 15px;
  position: relative;
}
.scenic-title {
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 8px;
}
.scenic-rating {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.rating-score {
  color: #ff9900;
  font-size: 14px;
}
.rating-count {
  color: #999;
  font-size: 12px;
}
.scenic-status {
  display: inline-block;
  padding: 2px 6px;
  background: #67c23a;
  color: #fff;
  font-size: 12px;
  border-radius: 2px;
  margin-bottom: 8px;
}
.scenic-status.closed {
  background: #f56c6c;
}
.scenic-time {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
}
.scenic-address {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #666;
  font-size: 13px;
  padding: 8px 0;
  border-bottom: 1px solid #eee;
}
.address-text {
  flex: 1;
}
.map-link {
  color: #409EFF;
  padding-left: 15px;
  cursor: pointer;
}
.notice {
  padding: 8px 0;
  display: flex;
  align-items: center;
  color: #f56c6c;
  font-size: 13px;
}
.booking-section {
  margin-top: 10px;
  background: #fff;
}
.booking-header {
  padding: 12px 15px;
  border-bottom: 1px solid #eee;
  font-size: 15px;
  font-weight: 500;
}
.booking-dates {
  display: flex;
  padding: 15px;
  gap: 10px;
}
.date-item {
  flex: 1;
  text-align: center;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.date-item.active {
  border-color: #409EFF;
  color: #409EFF;
}
.ticket-item {
  padding: 15px;
  border-bottom: 1px solid #eee;
}
.ticket-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.ticket-name {
  font-size: 15px;
  font-weight: 500;
}
.ticket-price {
  color: #f56c6c;
  font-size: 16px;
  font-weight: bold;
}
.ticket-desc {
  font-size: 12px;
  color: #999;
}
.ticket-tags {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.ticket-tag {
  font-size: 11px;
  color: #409EFF;
  border: 1px solid #409EFF;
  padding: 2px 6px;
  border-radius: 2px;
}
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  padding: 8px 15px;
  background: #fff;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.05);
  z-index: 100;
}
.action-icon {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 12px;
  color: #666;
  margin-right: 15px;
}
.book-button {
  flex: 1;
  background: #409EFF;
  color: #fff;
  border: none;
  border-radius: 4px;
  padding: 10px;
  font-size: 15px;
}
.booking-dialog-content {
  padding: 10px 0;
}
.selected-date {
  margin-bottom: 15px;
  font-size: 14px;
  color: #666;
}
.date-text {
  color: #409EFF;
  font-weight: 500;
}
.ticket-selection {
  max-height: 300px;
  overflow-y: auto;
}
.ticket-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #eee;
}
.ticket-info {
  flex: 1;
}
.ticket-quantity {
  margin-left: 15px;
}
.booking-summary {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #eee;
}
.summary-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}
.total-price {
  color: #f56c6c;
  font-size: 18px;
  font-weight: bold;
}
.map-dialog {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  z-index: 2000;
  display: flex;
  flex-direction: column;
}
.map-header {
  height: 44px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #eee;
  position: relative;
}
.map-title {
  flex: 1;
  text-align: center;
  font-size: 16px;
  color: #333;
}
.map-close {
  position: absolute;
  right: 16px;
  font-size: 20px;
  color: #666;
  cursor: pointer;
}
.map-dialog :deep(#mapContainer) {
  flex: 1;
  width: 100%;
  height: calc(100% - 60px);
  margin-bottom: 60px;
  position: relative;
}
.map-info {
  position: absolute;
  top: 60px;
  left: 10px;
  right: auto;
  background: #fff;
  padding: 12px 16px;
  z-index: 100;
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
  max-width: 300px;
}
.map-info-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 8px;
  color: #333;
}
.map-info-address {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
  line-height: 1.4;
}
.map-info-distance {
  font-size: 13px;
  color: #999;
  border-top: 1px solid #eee;
  padding-top: 8px;
}
.map-copyright {
  position: absolute;
  left: 80px;
  bottom: 20px;
  width: calc(100% - 100px);
  text-align: left;
  padding: 0 5px;
  font-size: 12px;
  color: #666;
  line-height: 1.5;
  background: transparent;
  z-index: 1000;
}
</style>
