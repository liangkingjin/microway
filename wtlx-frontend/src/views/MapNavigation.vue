<template>
  <div class="page-container">
    <HeaderBar title="行程导航" :show-back="true" />

    <div class="mode-tabs">
      <div class="mode-tab" :class="{ active: mode === 'plan' }" @click="mode = 'plan'">我的行程</div>
      <div class="mode-tab" :class="{ active: mode === 'free' }" @click="mode = 'free'">自由导航</div>
    </div>

    <!-- 我的行程：直接读取「我的计划」里已落库的行程条目，按天串联真实道路路线 -->
    <div v-if="mode === 'plan'" class="plan-panel">
      <div class="plan-panel__row">
        <el-select v-model="selectedPlanId" placeholder="选择旅行计划" class="plan-panel__select" @change="onPlanChange">
          <el-option v-for="p in plans" :key="p.id" :label="p.title" :value="p.id" />
        </el-select>
        <el-select v-model="travelMode" class="plan-panel__mode">
          <el-option label="驾车" value="driving" />
          <el-option label="步行" value="walking" />
        </el-select>
      </div>

      <div v-if="days.length" class="day-tabs">
        <div
          v-for="d in days"
          :key="d"
          class="day-tab"
          :class="{ active: activeDay === d }"
          @click="selectDay(d)"
        >{{ d.slice(5) }}</div>
      </div>

      <div v-if="dayStops.length" class="stop-list">
        <div v-for="(s, i) in dayStops" :key="i" class="stop-item">
          <span class="stop-item__seq">{{ i + 1 }}</span>
          <span class="stop-item__name">{{ s.name }}</span>
          <span class="stop-item__time">{{ s.time }}</span>
        </div>
      </div>

      <el-button
        type="primary"
        class="plan-panel__btn"
        :loading="routeLoading"
        :disabled="dayStops.length < 2"
        @click="drawPlanRoute"
      >在地图上串联路线</el-button>
      <div v-if="planHint" class="plan-panel__hint">{{ planHint }}</div>
      <div v-if="!plans.length" class="plan-panel__empty">
        还没有旅行计划，先让 AI 助手生成一份吧
        <button type="button" class="plan-panel__link" @click="goAiPlanner">去生成行程</button>
      </div>
    </div>

    <!-- 自由导航：保留原来的起终点手动规划 -->
    <div v-else class="route-panel">
      <div class="route-panel__field">
        <span class="route-panel__dot route-panel__dot--start"></span>
        <el-input id="startAddrInput" v-model="startAddr" placeholder="输入起点（如：武汉）" clearable></el-input>
      </div>
      <div class="route-panel__field">
        <span class="route-panel__dot route-panel__dot--end"></span>
        <el-input id="endAddrInput" v-model="endAddr" placeholder="输入终点（如：黄石）" clearable></el-input>
      </div>
      <el-button type="primary" class="route-panel__btn" :loading="loading" @click="searchRoute">开始导航</el-button>
    </div>

    <div class="map-shell">
      <div id="allmap"></div>
      <div v-if="!mapReady" class="map-placeholder">地图加载中…</div>

      <div v-if="showResult" class="result-card">
        <div class="result-card__item">
          <span class="result-card__label">预计用时</span>
          <span class="result-card__value">{{ duration }}</span>
        </div>
        <div class="result-card__divider"></div>
        <div class="result-card__item">
          <span class="result-card__label">全程距离</span>
          <span class="result-card__value">{{ distance }}</span>
        </div>
        <button class="result-card__close" type="button" aria-label="关闭" @click="showResult = false">×</button>
      </div>
    </div>

    <FootBar :active-btn="2" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import FootBar from '../components/FootBar.vue'
import request from '../api/request'
import { loadBMapGL } from '../utils/bmap'

const BMAP_AK = 'd3SExcYB3XNkA5KXvqdpAvsAilJxiNKx'
// AI 在行程条目的 content 开头标注真实 POI，例如「【黄鹤楼】登楼游览约 1.5 小时」
const POI_RE = /【([^】]{1,40})】/

const route = useRoute()
const router = useRouter()

const mode = ref('plan')
const plans = ref([])
const selectedPlanId = ref(null)
const itinerary = ref([])
const days = ref([])
const activeDay = ref('')
const travelMode = ref('driving')
const routeLoading = ref(false)
// 过程性提示（坐标解析中 / 规划结果）；常态的「地点不足」提示由 computed 推导
const statusHint = ref('')

const startAddr = ref('')
const endAddr = ref('')
const showResult = ref(false)
const duration = ref('')
const distance = ref('')
const loading = ref(false)
const mapReady = ref(false)
let map = null
let transit = null

// 行程内容里由 AI 标注的【真实地点名】，按天取出后交给百度地图解析坐标
const dayStops = computed(() => itinerary.value
  .filter(item => String(item.date || '') === activeDay.value)
  .map(item => {
    const match = POI_RE.exec(item.content || '')
    return match ? { name: match[1].trim(), time: item.time || '', content: item.content || '' } : null
  })
  .filter(Boolean))

const planHint = computed(() => {
  if (!plans.value.length) return ''
  if (statusHint.value) return statusHint.value
  if (dayStops.value.length < 2) return '当天不足 2 个可定位地点；旧计划没有地点标注，让 AI 重新生成一份即可自动串联路线'
  return ''
})

function goAiPlanner() {
  router.push('/ai-assistant')
}

function statusOk(status) {
  return status === (window.BMAP_STATUS_SUCCESS ?? 0)
}

function escapeHtml(value) {
  return String(value || '').replace(/[&<>"']/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[ch]))
}

async function ensureBMapGL() {
  try {
    return await loadBMapGL(BMAP_AK)
  } catch (e) {
    console.error('[Map]', e)
    ElMessage.error('地图加载失败，请检查网络或 AK 配置')
    return null
  }
}

async function initMap() {
  const BMapGL = await ensureBMapGL()
  if (!BMapGL) return
  map = new BMapGL.Map('allmap')
  mapReady.value = true
  const geolocation = new BMapGL.Geolocation()
  geolocation.getCurrentPosition((r) => {
    if (statusOk(geolocation.getStatus())) {
      map.centerAndZoom(r.point, 12)
      map.addOverlay(new BMapGL.Marker(r.point))
    } else {
      map.centerAndZoom(new BMapGL.Point(116.404, 39.915), 12)
    }
  }, { enableHighAccuracy: true })
}

// 起终点输入框只在「自由导航」模式下存在，默认「我的行程」模式下初始化会拿不到 DOM
let autocompleteReady = false
async function initAutocomplete() {
  if (autocompleteReady) return
  const BMapGL = await ensureBMapGL()
  if (!BMapGL) return
  const startInput = document.getElementById('startAddrInput')
  const endInput = document.getElementById('endAddrInput')
  if (!startInput || !endInput) return
  const startAutocomplete = new BMapGL.Autocomplete({ input: 'startAddrInput' })
  const endAutocomplete = new BMapGL.Autocomplete({ input: 'endAddrInput' })
  startAutocomplete.addEventListener('onconfirm', (e) => {
    startAddr.value = e.item.value
    startInput.focus()
  })
  endAutocomplete.addEventListener('onconfirm', (e) => {
    endAddr.value = e.item.value
    endInput.focus()
  })
  autocompleteReady = true
}

watch(mode, (value) => {
  if (value === 'free') nextTick(initAutocomplete)
})

// 换天后旧的「已串联 N 个地点」不再成立，让 computed 回落到常态提示。
// 这里显式清理而不用 watch：自动画图会在同一轮微任务里写入过程提示，
// 异步刷新的 watch 会把它误清掉。
function selectDay(day) {
  activeDay.value = day
  statusHint.value = ''
}

async function loadPlans() {
  try {
    const res = await request.get('/travel-plans')
    const list = Array.isArray(res.data) ? res.data : []
    plans.value = list
    if (!list.length) return
    // AI 助手带 planId 跳转时精确选中那一份；直接点底栏「行程」则退回取 id 最大的最新计划
    const askedId = Number(route.query.planId)
    const asked = list.find(p => Number(p.id) === askedId)
    const latest = list.reduce((a, b) => (Number(b.id) > Number(a.id) ? b : a))
    const target = asked || latest
    selectedPlanId.value = target.id
    await loadItinerary(target.id)
  } catch (e) {
    statusHint.value = '读取我的计划失败，请稍后重试'
  }
}

async function loadItinerary(planId) {
  statusHint.value = ''
  try {
    const res = await request.get('/itinerary-items/' + planId)
    // 后端 list() 无排序；同一天不按时间排，路线会在景点之间来回折返
    const list = (Array.isArray(res.data) ? res.data : []).slice().sort((a, b) => {
      const byDate = String(a.date || '').localeCompare(String(b.date || ''))
      return byDate || String(a.time || '').localeCompare(String(b.time || ''))
    })
    itinerary.value = list
    days.value = [...new Set(list.map(i => String(i.date || '')).filter(Boolean))]
    // 自动画图时优先落在真正有 >=2 个地点标注的那天，而不是无脑选第一天
    const usable = days.value.find(d => list.filter(i =>
      String(i.date || '') === d && POI_RE.test(i.content || '')).length >= 2)
    activeDay.value = usable || days.value[0] || ''
  } catch (e) {
    statusHint.value = '读取行程失败'
  }
}

function onPlanChange(id) {
  loadItinerary(id)
}

// 把地点名解析为百度坐标（bd09ll），返回 null 表示该地点未定位
function geocodePoi(name) {
  return new Promise((resolve) => {
    const BMapGL = window.BMapGL
    let settled = false
    const done = (hit) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(hit)
    }
    // LocalSearch 失败时不一定回调；不设超时会让「正在解析坐标…」永久卡住
    const timer = setTimeout(() => done(null), 6000)
    try {
      const search = new BMapGL.LocalSearch(map, {
        onSearchComplete: (results) => {
          try {
            const poi = results && results.getPoi(0)
            done(poi && poi.point ? { point: poi.point, title: poi.title || name } : null)
          } catch (e) {
            done(null)
          }
        }
      })
      // forceLocal:false 允许跳出地图当前城市，否则异地行程（人在武汉、计划在北京）会搜不到 POI
      search.search(name, { forceLocal: false })
    } catch (e) {
      done(null)
    }
  })
}

async function drawPlanRoute() {
  if (dayStops.value.length < 2) {
    ElMessage.warning('至少需要 2 个带地点标注的行程点')
    return
  }
  if (!map) {
    ElMessage.warning('地图尚未加载完成，请稍候')
    return
  }

  routeLoading.value = true
  statusHint.value = '正在解析地点坐标…'
  showResult.value = false
  map.clearOverlays()
  const BMapGL = window.BMapGL

  const stops = dayStops.value
  const points = []
  const failed = []
  for (let i = 0; i < stops.length; i++) {
    const hit = await geocodePoi(stops[i].name)
    if (hit) points.push({ ...hit, stop: stops[i], seq: i + 1 })
    else failed.push(stops[i].name)
  }
  if (points.length < 2) {
    routeLoading.value = false
    statusHint.value = '地点解析失败，请检查行程里的地点名称'
    return
  }

  // 编号标记 + 点击气泡（行程文案来自模型，必须转义后再进 InfoWindow）
  points.forEach((p) => {
    const marker = new BMapGL.Marker(p.point)
    map.addOverlay(marker)
    const label = new BMapGL.Label(String(p.seq), {
      position: p.point,
      offset: new BMapGL.Size(6, -6)
    })
    label.setStyle({
      border: 'none',
      background: '#409eff',
      color: '#fff',
      borderRadius: '10px',
      padding: '1px 6px',
      fontSize: '12px'
    })
    map.addOverlay(label)
    const html = '<div style="font-size:13px;line-height:1.6"><b>'
      + escapeHtml(p.seq + '. ' + p.stop.name) + '</b><br/>'
      + escapeHtml(p.stop.time) + '<br/>'
      + escapeHtml(p.stop.content) + '</div>'
    const info = new BMapGL.InfoWindow(html, { width: 230 })
    marker.addEventListener('click', () => map.openInfoWindow(info, p.point))
  })

  if (travelMode.value === 'walking') {
    // 步行不支持途经点，逐段规划真实步行路线
    for (let i = 0; i < points.length - 1; i++) {
      const walk = new BMapGL.WalkingRoute(map, {
        renderOptions: { map: map, autoViewport: i === 0 }
      })
      walk.search(points[i].point, points[i + 1].point)
    }
    showResult.value = false
    statusHint.value = '已按步行逐段规划 ' + (points.length - 1) + ' 段路线'
  } else {
    // 驾车支持最多 10 个途经点，一次调用把整天的景点串成真实道路
    const first = points[0].point
    const last = points[points.length - 1].point
    let mids = points.slice(1, -1).map(p => p.point)
    let truncated = false
    if (mids.length > 10) {
      mids = mids.slice(0, 10)
      truncated = true
    }
    const driving = new BMapGL.DrivingRoute(map, {
      renderOptions: { map: map, autoViewport: true },
      onSearchComplete: (results) => {
        if (!statusOk(driving.getStatus())) {
          statusHint.value = '路线规划失败，请稍后重试或切换为步行'
          return
        }
        const plan = results.getPlan(0)
        if (!plan) {
          statusHint.value = '未找到可用路线，请检查地点顺序'
          return
        }
        duration.value = plan.getDuration(true)
        distance.value = plan.getDistance(true)
        showResult.value = true
      }
    })
    driving.search(first, last, { waypoints: mids })
    statusHint.value = '已串联 ' + points.length + ' 个地点'
      + (truncated ? '（途经点超过 10 个，仅取前 10 个）' : '')
      + (failed.length ? '，未定位：' + failed.join('、') : '')
  }

  routeLoading.value = false
}

function getPoint(address, callback) {
  const BMapGL = window.BMapGL
  const localSearch = new BMapGL.LocalSearch(map, {
    onSearchComplete: (results) => {
      if (!statusOk(localSearch.getStatus())) {
        loading.value = false
        ElMessage.error('地址解析失败，请检查输入')
        return
      }
      const firstResult = results.getPoi(0)
      if (!firstResult || !firstResult.point) {
        loading.value = false
        ElMessage.error('未找到该地点，请换个关键词')
        return
      }
      callback(firstResult.point)
    }
  })
  localSearch.search(address, { forceLocal: false })
}

function searchRoute() {
  if (!startAddr.value || !endAddr.value) {
    ElMessage.warning('请填写起点和终点')
    return
  }
  if (!map) {
    ElMessage.warning('地图尚未加载完成，请稍候')
    return
  }
  loading.value = true
  showResult.value = false
  map.clearOverlays()
  const BMapGL = window.BMapGL
  getPoint(startAddr.value, (startPoint) => {
    getPoint(endAddr.value, (endPoint) => {
      transit = new BMapGL.DrivingRoute(map, {
        renderOptions: { map: map, autoViewport: true },
        onSearchComplete: (results) => {
          loading.value = false
          if (!statusOk(transit.getStatus())) {
            ElMessage.error('路线规划失败')
            return
          }
          const plan = results.getPlan(0)
          if (!plan) {
            ElMessage.error('未找到可用路线')
            return
          }
          duration.value = plan.getDuration(true)
          distance.value = plan.getDistance(true)
          showResult.value = true
        }
      })
      transit.search(startPoint, endPoint)
    })
  })
}

onMounted(async () => {
  await initMap()
  // 从 AI 助手「在地图上查看路线」进来时带 auto=1，加载完计划直接画路线；
  // 用 URL 参数而不是 localStorage 标记，避免中途离开后下次进「行程」页误触发
  const autoDraw = route.query.auto === '1' && mode.value === 'plan'
  if (mode.value === 'free') nextTick(initAutocomplete)
  await loadPlans()
  if (autoDraw && map && dayStops.value.length >= 2) {
    drawPlanRoute()
  }
})
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  box-sizing: border-box;
  padding-bottom: 50px;
  background: #f5f7fa;
}
.page-container :deep(.header) {
  position: static;
  flex: 0 0 auto;
}
.mode-tabs {
  flex: 0 0 auto;
  display: flex;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.mode-tab {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  font-size: 14px;
  color: #7a8290;
  cursor: pointer;
  position: relative;
}
.mode-tab.active { color: var(--wt-color-brand); font-weight: 600; }
.mode-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 32px;
  height: 2px;
  border-radius: 1px;
  background: var(--wt-color-brand);
  transform: translateX(-50%);
}
.plan-panel {
  flex: 0 0 auto;
  max-height: 40vh;
  overflow-y: auto;
  padding: 12px 16px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  z-index: 2;
}
.plan-panel__row { display: flex; gap: 8px; }
.plan-panel__select { flex: 1; min-width: 0; }
.plan-panel__mode { width: 92px; flex: 0 0 92px; }
.day-tabs {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  margin-top: 10px;
}
.day-tab {
  flex: 0 0 auto;
  padding: 4px 12px;
  border-radius: 14px;
  font-size: 12px;
  color: #5f5e5a;
  background: #f1efe8;
  cursor: pointer;
}
.day-tab.active { color: #fff; background: var(--wt-color-brand); }
.stop-list { margin-top: 10px; }
.stop-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f2f4f7;
  font-size: 12px;
}
.stop-item__seq {
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--wt-color-brand);
  color: #fff;
  text-align: center;
  line-height: 18px;
}
.stop-item__name { flex: 1; color: #2c3e50; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stop-item__time { color: #909399; }
.plan-panel__btn { width: 100%; margin-top: 10px; }
.plan-panel__hint { margin-top: 8px; font-size: 12px; color: #909399; line-height: 1.5; }
.plan-panel__empty { padding: 16px 0; text-align: center; font-size: 13px; color: #909399; }
.plan-panel__link {
  display: block;
  margin: 10px auto 0;
  padding: 6px 16px;
  border: 1px solid var(--wt-color-brand);
  border-radius: 16px;
  background: #fff;
  color: var(--wt-color-brand);
  font-size: 13px;
  cursor: pointer;
}
.plan-panel__link:hover { background: #ecf5ff; }
.route-panel {
  flex: 0 0 auto;
  padding: 16px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  z-index: 2;
}
.route-panel__field { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.route-panel__dot { flex: 0 0 10px; width: 10px; height: 10px; border-radius: 50%; }
.route-panel__dot--start { background: var(--wt-color-brand); }
.route-panel__dot--end { background: var(--wt-color-danger); }
.route-panel__btn { width: 100%; }
.map-shell {
  flex: 1 1 auto;
  position: relative;
  min-height: 0;
  overflow: hidden;
  z-index: 1;
}
#allmap { position: absolute; inset: 0; width: 100%; height: 100%; }
.map-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 13px;
  background: #eef2f7;
  pointer-events: none;
}
.result-card {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  z-index: 3;
}
.result-card__item { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.result-card__label { font-size: 12px; color: #909399; }
.result-card__value { font-size: 16px; font-weight: 600; color: var(--wt-color-brand); }
.result-card__divider { width: 1px; height: 32px; background: #ebeef5; }
.result-card__close {
  position: absolute;
  top: 6px;
  right: 10px;
  border: none;
  background: transparent;
  color: #c0c4cc;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}
.page-container :deep(.foot) { z-index: 1200; }
</style>
