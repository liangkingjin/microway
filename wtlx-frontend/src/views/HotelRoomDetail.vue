<template>
  <div class="page-container">
    <HeaderBar title="预订须知">
      <template #right>
        <div class="close-btn" @click="goBack"><el-icon><Close /></el-icon></div>
      </template>
    </HeaderBar>

    <div class="room-gallery">
      <img :src="currentImage" class="room-image" alt="房间图片">
      <div class="image-indicator">{{ roomImages.length ? currentImageIndex + 1 : 0 }}/{{ roomImages.length }}</div>
    </div>

    <div class="room-info">
      <div class="room-title">{{ roomDetail.roomType }} · {{ roomDetail.title }}</div>
      <div class="room-subtitle">{{ roomDetail.bedInfo }} ({{ roomDetail.size }})</div>

      <div class="room-features">
        <div class="feature-item">
          <div class="feature-icon"><el-icon><User /></el-icon></div>
          <div class="feature-text">{{ roomDetail.capacity }}</div>
        </div>
        <div class="feature-item">
          <div class="feature-icon"><el-icon><OfficeBuilding /></el-icon></div>
          <div class="feature-text">楼层:2-5</div>
        </div>
        <div class="feature-item">
          <div class="feature-icon"><el-icon><Coffee /></el-icon></div>
          <div class="feature-text">免费瓶装水</div>
        </div>
        <div class="feature-item">
          <div class="feature-icon"><el-icon><Monitor /></el-icon></div>
          <div class="feature-text">电视</div>
        </div>
        <div class="feature-item">
          <div class="feature-icon"><el-icon><Wifi /></el-icon></div>
          <div class="feature-text">宽带上网</div>
        </div>
        <div class="feature-item">
          <div class="feature-icon"><el-icon><Bath /></el-icon></div>
          <div class="feature-text">独立卫浴间</div>
        </div>
      </div>
    </div>

    <div class="promotion-section">
      <div class="section-title">优惠活动</div>
      <div class="promotion-item" v-if="roomDetail.discount">
        <div class="promotion-tag">直减</div>
        <div>已优惠¥{{ roomDetail.discount }}</div>
      </div>
      <div class="promotion-item">
        <div class="promotion-tag">会员专享</div>
        <div>已减¥29</div>
      </div>
      <div style="font-size: 12px; color: #999; margin-top: 10px;">
        划线价为酒店官方价格，活动期间每晚立减¥29
      </div>
    </div>

    <div class="notice-section">
      <div class="section-title">必读须知</div>

      <div class="notice-item">
        <div class="notice-title">含餐</div>
        <div class="notice-content">1份早餐</div>
      </div>

      <div class="notice-item">
        <div class="notice-title">免费取消({{ cancelTime }})</div>
        <div class="notice-content">
          我们理解旅行计划会变更，因此在北京时间{{ cancelTime }}前您可以免费取消此预订。在此日期后取消，已收取费用将无法退回
        </div>
      </div>

      <div class="notice-item">
        <div class="notice-title">立即确认</div>
        <div class="notice-content">
          付款成功后15分钟内确认预订结果 (21:00-09:00可能会有延迟)
        </div>
      </div>

      <div class="notice-item">
        <div class="notice-title">在线付</div>
        <div class="notice-content">
          房费需要提前支付，可以使用微信或支付宝等支付款
        </div>
      </div>
    </div>

    <div style="height: 70px;"></div>
  </div>

  <div class="bottom-bar">
    <div class="price-info">
      <div class="original-price" v-if="roomDetail.originalPrice">¥{{ roomDetail.originalPrice }}</div>
      <div class="current-price">
        ¥{{ roomDetail.currentPrice }}
        <span class="discount-tag" v-if="roomDetail.discount">/晚</span>
      </div>
    </div>
    <button class="book-btn" @click="bookNow">马上预订</button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const shopId = ref(null)
const roomId = ref(null)
const hotelInfo = ref({})
const roomDetail = ref({})
const roomImages = ref([])
const currentImageIndex = ref(0)
const cancelTime = ref('2025年04月15日 18:00前')

let rotationTimer = null

const currentImage = computed(() => {
  return roomImages.value.length > 0 ? roomImages.value[currentImageIndex.value] : '/imgs/icons/default-icon.jpg'
})

onMounted(() => {
  shopId.value = getUrlParam('shopId')
  roomId.value = getUrlParam('roomId')

  const hotelInfoStr = localStorage.getItem('hotelInfo')
  const roomDetailStr = localStorage.getItem('currentRoom')

  if (hotelInfoStr) {
    try { hotelInfo.value = JSON.parse(hotelInfoStr) } catch (e) { console.error(e) }
  }

  if (roomDetailStr) {
    try {
      roomDetail.value = JSON.parse(roomDetailStr)
      if (roomDetail.value.processedImages && roomDetail.value.processedImages.length > 0) {
        roomImages.value = roomDetail.value.processedImages
      } else if (roomDetail.value.imagesList && roomDetail.value.imagesList.length > 0) {
        roomImages.value = roomDetail.value.imagesList
      } else if (roomDetail.value.images) {
        if (typeof roomDetail.value.images === 'string') {
          roomImages.value = roomDetail.value.images.split(',').map(url => normalizeImageUrl(url))
        } else if (Array.isArray(roomDetail.value.images)) {
          roomImages.value = roomDetail.value.images
        }
      }
    } catch (e) {
      console.error('解析房间信息失败:', e)
    }
  }

  if ((!roomDetail.value.id || !roomDetail.value.title) && shopId.value && roomId.value) {
    fetchRoomDetail()
  } else if (roomImages.value.length === 0) {
    roomImages.value = ['/imgs/icons/default-icon.jpg']
  }

  startImageRotation()
})

onBeforeUnmount(() => {
  if (rotationTimer) clearInterval(rotationTimer)
})

function normalizeImageUrl(url) {
  if (!url) return '/imgs/icons/default-icon.jpg'
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  if (!url.startsWith('/')) return url
  return url
}

function goBack() {
  router.back()
}

function fetchRoomDetail() {
  const loading = ElLoading.service({
    lock: true,
    text: '加载中...',
    background: 'rgba(0, 0, 0, 0.7)'
  })

  request.get(`/tb-hotel-room/${shopId.value}/${roomId.value}`)
    .then(res => {
      const data = res.data
      roomDetail.value = data
      if (data && data.images) {
        if (typeof data.images === 'string') {
          roomImages.value = data.images.split(',').map(url => normalizeImageUrl(url))
        } else if (Array.isArray(data.images)) {
          roomImages.value = data.images
        }
      } else {
        roomImages.value = ['/imgs/icons/default-icon.jpg']
      }

      if (data.facilities && typeof data.facilities === 'string') {
        try { roomDetail.value.facilities = JSON.parse(data.facilities) } catch (e) { console.error('解析设施信息失败:', e) }
      }
      if (data.promotions && typeof data.promotions === 'string') {
        try { roomDetail.value.promotions = JSON.parse(data.promotions) } catch (e) { console.error('解析促销信息失败:', e) }
      }
    })
    .catch(err => {
      ElMessage.error('获取房间详情失败')
      console.error('获取房间详情失败:', err)
      roomImages.value = ['/imgs/icons/default-icon.jpg']
    })
    .finally(() => {
      loading.close()
    })
}

function startImageRotation() {
  rotationTimer = setInterval(() => {
    if (roomImages.value.length > 0) {
      currentImageIndex.value = (currentImageIndex.value + 1) % roomImages.value.length
    }
  }, 3000)
}

function bookNow() {
  if ((roomDetail.value.stock ?? 1) < 1) {
    ElMessage.error('房间已售罄，请选择其他房型')
    return
  }

  ElMessageBox.confirm('确认预订该房间?', '预订确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
    center: true
  }).then(() => {
    submitBooking()
  }).catch(() => {
    console.log('用户取消预订')
  })
}

function submitBooking() {
  const loading = ElLoading.service({
    lock: true,
    text: '预订中...',
    background: 'rgba(0, 0, 0, 0.7)'
  })

  const bookingData = {
    shopId: shopId.value,
    roomId: roomId.value,
    price: roomDetail.value.currentPrice,
    checkInDate: new Date().toISOString().split('T')[0],
    checkOutDate: new Date(Date.now() + 86400000).toISOString().split('T')[0]
  }

  request.post('/tb-hotel-order', bookingData)
    .then(() => {
      handleBookingSuccess()
    })
    .catch(error => {
      ElMessage.error(typeof error === 'string' ? error : '预订失败')
      console.error('预订失败:', error)
    })
    .finally(() => {
      loading.close()
    })
}

function handleBookingSuccess() {
  ElMessage({
    message: '预订成功！',
    type: 'success',
    duration: 3000
  })
  setTimeout(() => {
    goBack()
  }, 2000)
}
</script>

<style scoped>
.page-container {
  max-width: 600px;
  margin: 0 auto;
  background-color: #f5f5f5;
  min-height: 100vh;
}
.close-btn {
  position: absolute;
  right: 16px;
  font-size: 20px;
  color: #333;
  display: flex;
  align-items: center;
}
.room-gallery {
  position: relative;
}
.room-image {
  width: 100%;
  height: 250px;
  object-fit: cover;
  display: block;
}
.image-indicator {
  position: absolute;
  right: 10px;
  bottom: 10px;
  background-color: rgba(0, 0, 0, 0.6);
  color: white;
  padding: 3px 8px;
  border-radius: 12px;
  font-size: 12px;
}
.room-info {
  background-color: #fff;
  padding: 15px;
}
.room-title {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 10px;
}
.room-subtitle {
  font-size: 14px;
  color: #666;
  margin-bottom: 5px;
}
.room-features {
  display: flex;
  flex-wrap: wrap;
  margin: 15px 0;
}
.feature-item {
  width: 33.33%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 15px;
}
.feature-icon {
  font-size: 20px;
  color: #666;
  margin-bottom: 5px;
}
.feature-text {
  font-size: 12px;
  color: #666;
  text-align: center;
}
.section-title {
  font-size: 16px;
  font-weight: bold;
  margin: 15px 0 10px;
  padding-left: 10px;
  border-left: 3px solid #409EFF;
}
.promotion-section {
  background-color: #fff;
  padding: 15px;
  margin-top: 10px;
}
.promotion-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}
.promotion-tag {
  background-color: #f1f8ff;
  color: #409EFF;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 12px;
  margin-right: 10px;
}
.notice-section {
  background-color: #fff;
  padding: 15px;
  margin-top: 10px;
}
.notice-item {
  margin-bottom: 15px;
}
.notice-title {
  font-weight: bold;
  margin-bottom: 5px;
}
.notice-content {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
}
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 15px;
  box-shadow: 0 -1px 5px rgba(0, 0, 0, 0.1);
  max-width: 600px;
  margin: 0 auto;
}
.price-info {
  display: flex;
  flex-direction: column;
}
.original-price {
  font-size: 12px;
  color: #999;
  text-decoration: line-through;
}
.current-price {
  color: #ff6633;
  font-size: 22px;
  font-weight: bold;
}
.discount-tag {
  font-size: 12px;
  color: #ff6633;
  margin-left: 5px;
}
.book-btn {
  background-color: #ff9500;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 10px 25px;
  font-size: 16px;
  font-weight: bold;
}
</style>
