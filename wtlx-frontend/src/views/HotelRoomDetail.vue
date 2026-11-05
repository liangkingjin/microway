<template>
  <div class="wt-page room-detail">
    <HeaderBar :title="headerTitle">
      <template #right>
        <button class="header-close" type="button" aria-label="关闭" @click="goBack">
          <el-icon><Close /></el-icon>
        </button>
      </template>
    </HeaderBar>

    <div class="room-detail__body">
      <!-- ① 图库：主图 + 缩略图条，手动切换（原 3s 自动轮播会打断阅读，已移除） -->
      <section class="gallery" aria-label="房间图片">
        <div class="gallery__stage">
          <transition name="fade" mode="out-in">
            <img :key="currentImage" :src="currentImage" class="gallery__img" alt="房间图片">
          </transition>

          <button
            v-if="roomImages.length > 1"
            class="gallery__nav gallery__nav--prev"
            type="button"
            aria-label="上一张"
            @click="stepImage(-1)"
          >
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <button
            v-if="roomImages.length > 1"
            class="gallery__nav gallery__nav--next"
            type="button"
            aria-label="下一张"
            @click="stepImage(1)"
          >
            <el-icon><ArrowRight /></el-icon>
          </button>

          <div class="gallery__counter" v-if="roomImages.length > 1">
            {{ currentImageIndex + 1 }} / {{ roomImages.length }}
          </div>
        </div>

        <div class="gallery__thumbs" v-if="roomImages.length > 1">
          <button
            v-for="(img, i) in roomImages"
            :key="img + i"
            class="gallery__thumb"
            :class="{ 'is-active': i === currentImageIndex }"
            type="button"
            :aria-label="`第 ${i + 1} 张图片`"
            :aria-current="i === currentImageIndex"
            @click="currentImageIndex = i"
          >
            <img :src="img" alt="">
          </button>
        </div>
      </section>

      <!-- ② 主信息卡：酒店 → 房型 → 规格 → 价格，四级层级一次读完 -->
      <section class="card summary">
        <div class="summary__hotel">
          <h2 class="summary__hotel-name wt-ellipsis">{{ hotelName }}</h2>
          <span v-if="hotelScore" class="summary__score">
            <el-icon><StarFilled /></el-icon>{{ hotelScore }}
          </span>
        </div>
        <p class="summary__hotel-meta" v-if="hotelMeta">{{ hotelMeta }}</p>

        <h1 class="summary__room">{{ roomTitle }}</h1>
        <p class="summary__sub" v-if="roomDetail.bedInfo">
          {{ roomDetail.bedInfo }}<template v-if="roomDetail.size"> · {{ roomDetail.size }}</template>
        </p>

        <ul class="specs" v-if="specs.length">
          <li v-for="s in specs" :key="s.text" class="specs__item">
            <el-icon><component :is="s.icon" /></el-icon>{{ s.text }}
          </li>
        </ul>

        <div class="summary__price">
          <div class="price">
            <span class="price__currency">¥</span>
            <span class="price__value">{{ roomDetail.currentPrice ?? '—' }}</span>
            <span class="price__unit">/晚</span>
            <span v-if="roomDetail.originalPrice" class="price__original">¥{{ roomDetail.originalPrice }}</span>
          </div>
          <span v-if="roomDetail.discount" class="price__save">已减 ¥{{ roomDetail.discount }}</span>
        </div>
      </section>

      <!-- ③ 房间设施 -->
      <section class="card card--amenities">
        <h3 class="section-title">房间设施</h3>
        <ul class="amenities">
          <li v-for="a in amenities" :key="a.text" class="amenities__item">
            <el-icon class="amenities__icon"><component :is="a.icon" /></el-icon>
            <span>{{ a.text }}</span>
          </li>
        </ul>
      </section>

      <!-- ④ 优惠活动（原「会员专享已减¥29」与底部小字重复，合并为一条） -->
      <section class="card card--promo" v-if="promotions.length">
        <h3 class="section-title">优惠活动</h3>
        <ul class="promos">
          <li v-for="p in promotions" :key="p.tag + p.text" class="promos__item">
            <span class="promos__tag">{{ p.tag }}</span>
            <span class="promos__text">{{ p.text }}</span>
          </li>
        </ul>
      </section>

      <!-- ⑤ 预订须知：标签 / 内容两栏，比原来的大段留白更紧凑 -->
      <section class="card card--rules">
        <h3 class="section-title">预订须知</h3>
        <dl class="rules">
          <div class="rules__row">
            <dt>含餐</dt>
            <dd>{{ roomDetail.mealInfo || '1份早餐' }}</dd>
          </div>
          <div class="rules__row">
            <dt>免费取消</dt>
            <dd>北京时间 {{ cancelTime }} 前可免费取消，逾期取消已收费用不退</dd>
          </div>
          <div class="rules__row">
            <dt>立即确认</dt>
            <dd>付款成功后 15 分钟内确认预订结果（21:00–09:00 可能延迟）</dd>
          </div>
          <div class="rules__row">
            <dt>在线付</dt>
            <dd>房费需提前支付，支持微信、支付宝</dd>
          </div>
        </dl>
      </section>
    </div>

    <!-- ⑥ 操作栏：收藏 / 分享为次级图标按钮，价格与预订为视觉终点 -->
    <div class="actionbar">
      <div class="actionbar__inner">
        <button
          class="icon-btn"
          :class="{ 'is-active': isFavorite }"
          type="button"
          :aria-pressed="isFavorite"
          aria-label="收藏"
          @click="toggleFavorite"
        >
          <el-icon><component :is="isFavorite ? 'StarFilled' : 'Star'" /></el-icon>
          <span>{{ isFavorite ? '已收藏' : '收藏' }}</span>
        </button>

        <button class="icon-btn" type="button" aria-label="分享" @click="shareRoom">
          <el-icon><Share /></el-icon>
          <span>分享</span>
        </button>

        <div class="actionbar__price">
          <span class="actionbar__amount">
            <span class="price__currency">¥</span>{{ roomDetail.currentPrice ?? '—' }}
            <span class="price__unit">/晚</span>
          </span>
          <span v-if="roomDetail.originalPrice" class="actionbar__original">
            ¥{{ roomDetail.originalPrice }}
          </span>
        </div>

        <button class="cta" type="button" :disabled="soldOut" @click="bookNow">
          {{ soldOut ? '已售罄' : '马上预订' }}
        </button>
      </div>
    </div>
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

const FAVORITES_KEY = 'hotelRoomFavorites'

const shopId = ref(null)
const roomId = ref(null)
const hotelInfo = ref({})
const roomDetail = ref({})
const roomImages = ref([])
const currentImageIndex = ref(0)
const cancelTime = ref('入住当日 18:00')
const isFavorite = ref(false)

const currentImage = computed(() =>
  roomImages.value.length ? roomImages.value[currentImageIndex.value] : '/imgs/icons/default-icon.jpg'
)

const hotelName = computed(() => hotelInfo.value.name || roomDetail.value.shopName || '酒店房型')

// 顶部只放酒店主名：括号里的分店/地标说明属于详情信息，交给下方主信息卡展示。
// 完整酒店名塞进 HeaderBar 会撑破绝对居中的标题、盖住返回与关闭按钮。
const headerTitle = computed(() => {
  const mainName = (hotelName.value || '').split(/[（(]/)[0].trim()
  return mainName || '房型详情'
})

// 评分体系：库里的 shop.score 是 0–50，展示时压到 5 分制
const hotelScore = computed(() => {
  const raw = Number(hotelInfo.value.score)
  if (!raw || Number.isNaN(raw)) return ''
  return (raw / 10).toFixed(1)
})

const hotelMeta = computed(() => {
  const parts = []
  if (hotelInfo.value.address) parts.push(hotelInfo.value.address)
  const comments = Number(hotelInfo.value.comments)
  if (comments > 0) parts.push(`${comments} 条点评`)
  return parts.join(' · ')
})

const roomTitle = computed(() => {
  const { roomType, title } = roomDetail.value
  if (roomType && title && !String(title).includes(roomType)) return `${roomType} · ${title}`
  return title || roomType || '房型详情'
})

const soldOut = computed(() => Number(roomDetail.value.stock ?? 1) < 1)

const specs = computed(() => {
  const d = roomDetail.value
  const list = []
  if (d.bedInfo) list.push({ icon: 'Bed', text: d.bedInfo })
  if (d.size) list.push({ icon: 'FullScreen', text: d.size })
  if (d.capacity) list.push({ icon: 'User', text: d.capacity })
  if (d.floor) list.push({ icon: 'OfficeBuilding', text: d.floor })
  return list
})

// 设施：优先后端 facilities（JSON 数组或逗号串），解析不出时回退到固定项
const AMENITY_ICONS = [
  [/wifi|无线|宽带|网络/i, 'Wifi'],
  [/电视|tv/i, 'Monitor'],
  [/空调|air|暖/i, 'Sunny'],
  [/卫浴|淋浴|浴缸|bath/i, 'Bath'],
  [/水|饮|咖啡|茶/i, 'Coffee'],
  [/早餐|餐/i, 'KnifeFork'],
  [/窗|阳台/i, 'House'],
  [/停车|车库/i, 'Van']
]
const DEFAULT_AMENITIES = ['宽带上网', '电视', '独立卫浴间', '免费瓶装水', '空调', '吹风机']

function iconFor(text) {
  const hit = AMENITY_ICONS.find(([re]) => re.test(text))
  return hit ? hit[1] : 'CircleCheck'
}

const amenities = computed(() => {
  const raw = roomDetail.value.facilities
  let list = []
  if (Array.isArray(raw)) {
    list = raw
  } else if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw)
      list = Array.isArray(parsed) ? parsed : []
    } catch {
      list = raw.split(/[,，、]/)
    }
  }
  const texts = list
    .map(item => (typeof item === 'string' ? item : item?.name || item?.text || ''))
    .map(t => String(t).trim())
    .filter(Boolean)

  const finalTexts = texts.length ? texts : DEFAULT_AMENITIES
  return finalTexts.map(text => ({ text, icon: iconFor(text) }))
})

// 优惠：房型自带 discount + promotions，去掉原先重复的"每晚立减 29"说明行
const promotions = computed(() => {
  const list = []
  if (roomDetail.value.discount) {
    list.push({ tag: '直减', text: `已优惠 ¥${roomDetail.value.discount}` })
  }
  const raw = roomDetail.value.promotions
  let parsed = []
  if (Array.isArray(raw)) {
    parsed = raw
  } else if (typeof raw === 'string' && raw.trim()) {
    try { parsed = JSON.parse(raw) } catch { parsed = [] }
  }
  parsed.forEach(item => {
    const text = typeof item === 'string' ? item : item?.name || item?.text || item?.title || ''
    if (text) list.push({ tag: (typeof item === 'object' && (item.tag || item.type)) || '优惠', text })
  })
  if (!list.length && roomDetail.value.currentPrice) {
    list.push({ tag: '在线付', text: '提前付款可享房型优惠价' })
  }
  return list
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
      if (roomDetail.value.processedImages?.length) {
        roomImages.value = roomDetail.value.processedImages
      } else if (roomDetail.value.imagesList?.length) {
        roomImages.value = roomDetail.value.imagesList
      } else if (roomDetail.value.images) {
        roomImages.value = typeof roomDetail.value.images === 'string'
          ? roomDetail.value.images.split(',').map(normalizeImageUrl)
          : roomDetail.value.images
      }
    } catch (e) {
      console.error('解析房间信息失败:', e)
    }
  }

  if ((!roomDetail.value.id || !roomDetail.value.title) && shopId.value && roomId.value) {
    fetchRoomDetail()
  } else if (!roomImages.value.length) {
    roomImages.value = ['/imgs/icons/default-icon.jpg']
  }

  loadFavorite()
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})

function normalizeImageUrl(url) {
  return url || '/imgs/icons/default-icon.jpg'
}

function goBack() {
  router.back()
}

function stepImage(delta) {
  const n = roomImages.value.length
  if (n < 2) return
  currentImageIndex.value = (currentImageIndex.value + delta + n) % n
}

function onKeydown(e) {
  if (e.key === 'ArrowLeft') stepImage(-1)
  else if (e.key === 'ArrowRight') stepImage(1)
}

function favoriteKey() {
  return `${shopId.value}:${roomId.value}`
}

function loadFavorite() {
  if (!shopId.value || !roomId.value) return
  try {
    const saved = JSON.parse(localStorage.getItem(FAVORITES_KEY) || '[]')
    isFavorite.value = Array.isArray(saved) && saved.includes(favoriteKey())
  } catch {
    isFavorite.value = false
  }
}

function toggleFavorite() {
  isFavorite.value = !isFavorite.value
  try {
    const saved = JSON.parse(localStorage.getItem(FAVORITES_KEY) || '[]')
    const set = new Set(Array.isArray(saved) ? saved : [])
    if (isFavorite.value) set.add(favoriteKey())
    else set.delete(favoriteKey())
    localStorage.setItem(FAVORITES_KEY, JSON.stringify([...set]))
  } catch (e) {
    console.error('保存收藏失败:', e)
  }
  ElMessage({
    message: isFavorite.value ? '已加入收藏' : '已取消收藏',
    type: isFavorite.value ? 'success' : 'info',
    duration: 1500
  })
}

async function shareRoom() {
  const url = window.location.href
  const title = `${hotelName.value} · ${roomTitle.value}`
  if (navigator.share) {
    try {
      await navigator.share({ title, url })
      return
    } catch (e) {
      if (e?.name === 'AbortError') return
    }
  }
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制，可分享给朋友')
  } catch {
    ElMessage.info(url)
  }
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
      if (data?.images) {
        roomImages.value = typeof data.images === 'string'
          ? data.images.split(',').map(normalizeImageUrl)
          : data.images
      } else {
        roomImages.value = ['/imgs/icons/default-icon.jpg']
      }
    })
    .catch(err => {
      ElMessage.error('获取房间详情失败')
      console.error('获取房间详情失败:', err)
      roomImages.value = ['/imgs/default-icon.jpg']
    })
    .finally(() => {
      loading.close()
    })
}

function bookNow() {
  if (soldOut.value) {
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
/* ==========================================================================
   房型详情
   ------------------------------------------------------------------------
   层级：图库 → 酒店/房型/规格/价格 → 设施 → 优惠 → 须知 → 操作栏。
   移动端单列固定底栏；≥768px 底栏浮起居中；≥1024px 左图右文双列、图库吸顶。
   间距 / 颜色 / 圆角一律走 --wt-* 令牌。
   ========================================================================== */
.room-detail {
  padding-bottom: calc(88px + env(safe-area-inset-bottom, 0px));
}

.header-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-right: -8px;
  border: none;
  border-radius: 50%;
  background: rgba(142, 202, 230, 0.1);
  color: var(--wt-color-brand-tint);
  font-size: 18px;
  cursor: pointer;
  transition: background-color 0.2s ease, transform 0.2s ease;
}
.header-close:hover { background: rgba(142, 202, 230, 0.2); }
.header-close:active { transform: scale(0.92); }

.room-detail__body {
  display: flex;
  flex-direction: column;
  gap: var(--wt-space-3);
  padding-bottom: var(--wt-space-4);
}

/* ---------------- 图库 ---------------- */
.gallery { background: #0e1116; }
.gallery__stage {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
}
.gallery__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.fade-enter-active,
.fade-leave-active { transition: opacity 0.28s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

.gallery__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  background: rgba(20, 26, 34, 0.42);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}
.gallery:hover .gallery__nav,
.gallery__nav:focus-visible { opacity: 1; }
.gallery__nav:hover { background: rgba(20, 26, 34, 0.62); }
.gallery__nav:active { transform: translateY(-50%) scale(0.92); }
.gallery__nav--prev { left: var(--wt-space-3); }
.gallery__nav--next { right: var(--wt-space-3); }
/* 触屏没有 hover，翻页按钮常显 */
@media (hover: none) {
  .gallery__nav { opacity: 1; }
}

.gallery__counter {
  position: absolute;
  right: var(--wt-space-3);
  bottom: var(--wt-space-3);
  padding: 3px var(--wt-space-2);
  border-radius: var(--wt-radius-pill);
  background: rgba(14, 17, 22, 0.6);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
  color: #fff;
  font-size: var(--wt-fs-xs);
  font-variant-numeric: tabular-nums;
}
.gallery__thumbs {
  display: flex;
  gap: var(--wt-space-2);
  padding: var(--wt-space-2) var(--wt-space-3);
  overflow-x: auto;
  scrollbar-width: none;
}
.gallery__thumbs::-webkit-scrollbar { display: none; }
.gallery__thumb {
  flex: 0 0 auto;
  width: 56px;
  height: 42px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: var(--wt-radius-sm);
  background: none;
  overflow: hidden;
  cursor: pointer;
  opacity: 0.62;
  transition: opacity 0.2s ease, border-color 0.2s ease;
}
.gallery__thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.gallery__thumb:hover { opacity: 0.9; }
.gallery__thumb.is-active {
  opacity: 1;
  border-color: var(--wt-color-brand);
}

/* ---------------- 卡片 ---------------- */
.card {
  margin-inline: var(--wt-gutter);
  padding: var(--wt-space-5);
  background: var(--wt-bg-surface);
  border-radius: var(--wt-radius-md);
  box-shadow: var(--wt-shadow-card);
}
.section-title {
  margin: 0 0 var(--wt-space-3);
  font-size: var(--wt-fs-lg);
  font-weight: 600;
  color: var(--wt-text-1);
  letter-spacing: 0.01em;
}

/* ---------------- 主信息 ---------------- */
.summary__hotel {
  display: flex;
  align-items: center;
  gap: var(--wt-space-2);
}
.summary__hotel-name {
  margin: 0;
  font-size: var(--wt-fs-md);
  font-weight: 600;
  color: var(--wt-text-2);
}
.summary__score {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
  padding: 2px var(--wt-space-2) 2px 6px;
  border-radius: var(--wt-radius-sm);
  background: linear-gradient(135deg, #ffb65c, #ff8f3d);
  color: #fff;
  font-size: var(--wt-fs-xs);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.summary__hotel-meta {
  margin: var(--wt-space-1) 0 0;
  font-size: var(--wt-fs-xs);
  color: var(--wt-text-3);
}
.summary__room {
  margin: var(--wt-space-3) 0 0;
  font-size: 22px;
  line-height: 1.3;
  font-weight: 700;
  color: var(--wt-text-1);
  letter-spacing: -0.01em;
}
.summary__sub {
  margin: var(--wt-space-1) 0 0;
  font-size: var(--wt-fs-sm);
  color: var(--wt-text-3);
}
.specs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wt-space-2);
  margin: var(--wt-space-4) 0 0;
  padding: 0;
  list-style: none;
}
.specs__item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px var(--wt-space-2) 5px 7px;
  border-radius: var(--wt-radius-sm);
  background: var(--wt-bg-muted);
  color: var(--wt-text-2);
  font-size: var(--wt-fs-xs);
}
.specs__item .el-icon {
  color: var(--wt-color-brand);
  font-size: 14px;
}
.summary__price {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--wt-space-3);
  margin-top: var(--wt-space-4);
  padding-top: var(--wt-space-4);
  border-top: 1px dashed var(--wt-border);
}
.price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  color: var(--wt-color-danger);
  font-variant-numeric: tabular-nums;
}
.price__currency { font-size: var(--wt-fs-md); font-weight: 600; }
.price__value { font-size: 28px; font-weight: 700; letter-spacing: -0.02em; }
.price__unit { margin-left: 2px; font-size: var(--wt-fs-xs); color: var(--wt-text-3); }
.price__original {
  margin-left: var(--wt-space-2);
  font-size: var(--wt-fs-xs);
  color: var(--wt-text-4);
  text-decoration: line-through;
}
.price__save {
  flex: 0 0 auto;
  padding: 2px var(--wt-space-2);
  border-radius: 4px;
  background: rgba(245, 108, 108, 0.1);
  color: var(--wt-color-danger);
  font-size: var(--wt-fs-xs);
}

/* ---------------- 设施 ---------------- */
.amenities {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: var(--wt-space-3) var(--wt-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.amenities__item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: var(--wt-fs-sm);
  color: var(--wt-text-2);
}
.amenities__item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.amenities__icon {
  flex: 0 0 auto;
  color: var(--wt-text-3);
  font-size: 16px;
}

/* ---------------- 优惠 ---------------- */
.promos {
  display: flex;
  flex-direction: column;
  gap: var(--wt-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.promos__item {
  display: flex;
  align-items: center;
  gap: var(--wt-space-2);
  font-size: var(--wt-fs-sm);
  color: var(--wt-text-2);
}
.promos__tag {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(245, 108, 108, 0.1);
  color: var(--wt-color-danger);
  font-size: var(--wt-fs-xs);
}

/* ---------------- 须知 ---------------- */
.rules { margin: 0; }
.rules__row {
  display: grid;
  grid-template-columns: 76px 1fr;
  gap: var(--wt-space-3);
  padding: var(--wt-space-2) 0;
}
.rules__row + .rules__row {
  border-top: 1px solid var(--wt-border);
}
.rules__row dt {
  color: var(--wt-text-1);
  font-size: var(--wt-fs-sm);
  font-weight: 600;
}
.rules__row dd {
  margin: 0;
  color: var(--wt-text-2);
  font-size: var(--wt-fs-sm);
  line-height: 1.6;
}

/* ---------------- 操作栏 ---------------- */
.actionbar {
  position: fixed;
  inset: auto 0 0 0;
  z-index: 900;
  background: var(--wt-bg-surface);
  box-shadow: 0 -2px 16px rgba(31, 45, 61, 0.1);
  padding-bottom: env(safe-area-inset-bottom, 0px);
}
.actionbar__inner {
  display: flex;
  align-items: center;
  gap: var(--wt-space-2);
  max-width: var(--wt-page-max);
  margin-inline: auto;
  padding: var(--wt-space-2) var(--wt-gutter);
}
.icon-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
  width: 46px;
  padding: 0;
  border: none;
  background: none;
  color: var(--wt-text-3);
  font-size: 10px;
  line-height: 1.2;
  cursor: pointer;
  transition: color 0.2s ease, transform 0.15s ease;
}
.icon-btn .el-icon { font-size: 20px; }
.icon-btn:hover { color: var(--wt-color-brand); }
.icon-btn:active { transform: scale(0.92); }
.icon-btn.is-active { color: var(--wt-color-danger); }
.actionbar__price {
  display: flex;
  flex: 1;
  align-items: baseline;
  justify-content: flex-end;
  gap: var(--wt-space-2);
  min-width: 0;
}
.actionbar__amount {
  color: var(--wt-color-danger);
  font-size: 20px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.actionbar__original {
  font-size: var(--wt-fs-xs);
  color: var(--wt-text-4);
  text-decoration: line-through;
}
.cta {
  flex: 0 0 auto;
  min-width: 116px;
  height: 44px;
  padding: 0 var(--wt-space-5);
  border: none;
  border-radius: var(--wt-radius-pill);
  background: linear-gradient(135deg, #ffab40, #ff8f1f);
  box-shadow: 0 4px 12px rgba(255, 143, 31, 0.32);
  color: #fff;
  font-size: var(--wt-fs-md);
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.2s ease, filter 0.2s ease;
}
.cta:hover {
  filter: brightness(1.04);
  box-shadow: 0 6px 16px rgba(255, 143, 31, 0.42);
  transform: translateY(-1px);
}
.cta:active {
  transform: translateY(0) scale(0.98);
  box-shadow: 0 2px 8px rgba(255, 143, 31, 0.3);
}
.cta:focus-visible {
  outline: 2px solid var(--wt-color-brand);
  outline-offset: 2px;
}
.cta:disabled {
  background: var(--wt-border-strong);
  box-shadow: none;
  color: var(--wt-text-3);
  cursor: not-allowed;
  transform: none;
  filter: none;
}

/* 键盘可达性 */
.icon-btn:focus-visible,
.gallery__nav:focus-visible,
.gallery__thumb:focus-visible {
  outline: 2px solid var(--wt-color-brand);
  outline-offset: 2px;
}

/* ==========================================================================
   响应式
   ========================================================================== */
@media (min-width: 768px) {
  .card { padding: var(--wt-space-6); }
  .gallery__stage { aspect-ratio: 16 / 9; max-height: 420px; }
  .room-detail { padding-bottom: calc(var(--wt-space-6) + 88px); }
  .actionbar {
    inset: auto 0 var(--wt-space-4) 0;
    max-width: 640px;
    margin-inline: auto;
    border-radius: var(--wt-radius-lg);
    box-shadow: var(--wt-shadow-float);
  }
  .actionbar__inner { padding: var(--wt-space-2) var(--wt-space-3); }
}

/* 桌面双列：左图吸顶，右侧信息流按 summary → 设施 → 优惠 → 须知 排列 */
@media (min-width: 1024px) {
  .room-detail__body {
    display: grid;
    grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
    align-items: start;
    gap: var(--wt-space-4);
    max-width: 1060px;
    margin-inline: auto;
    padding: var(--wt-space-4);
    grid-template-areas:
      'gallery summary'
      'gallery amenities'
      'gallery promo'
      'gallery rules';
  }
  .gallery {
    grid-area: gallery;
    position: sticky;
    top: 72px;
    /* 撑满视口高度：右侧信息流滚动时左图始终占满，避免下方出现大片空白 */
    height: calc(100vh - 96px);
    max-height: 680px;
    display: flex;
    flex-direction: column;
    border-radius: var(--wt-radius-md);
    overflow: hidden;
  }
  .gallery__stage {
    flex: 1;
    aspect-ratio: auto;
    max-height: none;
    min-height: 0;
  }
  .gallery__thumbs { flex: 0 0 auto; }
  .summary { grid-area: summary; }
  .card--amenities { grid-area: amenities; }
  .card--promo { grid-area: promo; }
  .card--rules { grid-area: rules; }
  .card { margin-inline: 0; }
  /* 双列下浮条放宽到与内容区同宽，视觉上与左右两列对齐 */
  .actionbar { max-width: 1000px; }
}
</style>
