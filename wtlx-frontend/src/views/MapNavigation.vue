<template>
  <div class="page-container">
    <HeaderBar title="行程导航" :show-back="true" />

    <div class="route-panel">
      <div class="route-panel__field">
        <span class="route-panel__dot route-panel__dot--start"></span>
        <el-input
          id="startAddrInput"
          v-model="startAddr"
          placeholder="输入起点（如：武汉）"
          clearable
        ></el-input>
      </div>
      <div class="route-panel__field">
        <span class="route-panel__dot route-panel__dot--end"></span>
        <el-input
          id="endAddrInput"
          v-model="endAddr"
          placeholder="输入终点（如：黄石）"
          clearable
        ></el-input>
      </div>
      <el-button
        type="primary"
        class="route-panel__btn"
        :loading="loading"
        @click="searchRoute"
      >开始导航</el-button>
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

      <!-- 版权/审图号由百度地图 GL SDK 自带，不再叠加一条固定白条 -->
    </div>

    <FootBar :active-btn="2" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import FootBar from '../components/FootBar.vue'
import { loadBMapGL } from '../utils/bmap'

const BMAP_AK = 'd3SExcYB3XNkA5KXvqdpAvsAilJxiNKx'

const startAddr = ref('')
const endAddr = ref('')
const showResult = ref(false)
const duration = ref('')
const distance = ref('')
const loading = ref(false)
const mapReady = ref(false)
let map = null
let transit = null

// 百度 GL 的成功状态码挂在 window 上，裸全局标识符在严格模式下不稳，统一兜底为 0
function statusOk(status) {
  return status === (window.BMAP_STATUS_SUCCESS ?? 0)
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
      ElMessage.warning('定位失败，已显示默认位置')
    }
  }, { enableHighAccuracy: true })
}

async function initAutocomplete() {
  const BMapGL = await ensureBMapGL()
  if (!BMapGL) return
  const startAddrInput = document.getElementById('startAddrInput')
  const endAddrInput = document.getElementById('endAddrInput')

  const startAutocomplete = new BMapGL.Autocomplete({ input: 'startAddrInput' })
  const endAutocomplete = new BMapGL.Autocomplete({ input: 'endAddrInput' })

  startAutocomplete.addEventListener('onconfirm', (e) => {
    startAddr.value = e.item.value
    startAddrInput.focus()
  })
  endAutocomplete.addEventListener('onconfirm', (e) => {
    endAddr.value = e.item.value
    endAddrInput.focus()
  })
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
  localSearch.search(address)
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

onMounted(() => {
  initMap()
  initAutocomplete()
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
/* 百度地图会把部分控件挂到 body 下且层级较高，底部导航保持在其上 */
.page-container :deep(.foot) {
  z-index: 1200;
}
.route-panel {
  flex: 0 0 auto;
  padding: 16px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  z-index: 2;
}
.route-panel__field {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.route-panel__dot {
  flex: 0 0 10px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.route-panel__dot--start { background: var(--wt-color-brand); }
.route-panel__dot--end { background: var(--wt-color-danger); }
.route-panel__btn { width: 100%; }
.map-shell {
  flex: 1 1 auto;
  position: relative;
  min-height: 0;
  overflow: hidden;
  /* 建独立层叠上下文，把百度地图内部的高 z-index 控件限制在本层内 */
  z-index: 1;
}
#allmap {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
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
.result-card__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.result-card__label { font-size: 12px; color: #909399; }
.result-card__value {
  font-size: 16px;
  font-weight: 600;
  color: var(--wt-color-brand);
}
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
</style>
