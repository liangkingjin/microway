<template>
  <div class="page-container">
    <HeaderBar title="地图导航" :show-back="true" />

    <div class="search-container">
      <el-row>
        <el-col :span="12">
          <el-input
            id="startAddrInput"
            v-model="startAddr"
            placeholder="输入起点（如：武汉）"
            clearable
          ></el-input>
        </el-col>
        <el-col :span="12">
          <el-input
            id="endAddrInput"
            v-model="endAddr"
            placeholder="输入终点（如：黄石）"
            clearable
          ></el-input>
        </el-col>
      </el-row>
      <el-button
        type="primary"
        @click="searchRoute"
        style="width: 100%; margin-top: 10px;"
      >
        开始导航
      </el-button>
    </div>

    <!-- 固定的审图号显示 -->
    <div class="map-approval-number">© 2025 Baidu - GS(2023)3206号 - 甲测资字1111342 - 京ICP证030173号 - Data © 百度智图</div>

    <div id="allmap"></div>

    <div class="result-box" v-if="showResult">
      <p>行程时间：{{ duration }}</p>
      <p>总距离：{{ distance }}</p>
    </div>

    <FootBar :active-btn="0" />
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
let map = null
let transit = null

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
  const geolocation = new BMapGL.Geolocation()
  geolocation.getCurrentPosition((r) => {
    if (geolocation.getStatus() === BMAP_STATUS_SUCCESS) {
      map.centerAndZoom(r.point, 12)
      const marker = new BMapGL.Marker(r.point)
      map.addOverlay(marker)
    } else {
      map.centerAndZoom(new BMapGL.Point(116.404, 39.915), 12)
      ElMessage.error('定位失败，请检查网络或位置权限')
    }
  }, { enableHighAccuracy: true })
}

async function initAutocomplete() {
  const BMapGL = await ensureBMapGL()
  if (!BMapGL) return
  const startAddrInput = document.getElementById('startAddrInput')
  const endAddrInput = document.getElementById('endAddrInput')

  const startAutocomplete = new BMapGL.Autocomplete({
    input: 'startAddrInput'
  })
  const endAutocomplete = new BMapGL.Autocomplete({
    input: 'endAddrInput'
  })

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
      if (localSearch.getStatus() === BMAP_STATUS_SUCCESS) {
        const firstResult = results.getPoi(0)
        callback(firstResult.point)
      } else {
        ElMessage.error('地址解析失败，请检查输入')
      }
    }
  })
  localSearch.search(address)
}

function searchRoute() {
  if (!startAddr.value || !endAddr.value) {
    ElMessage.warning('请填写起点和终点')
    return
  }

  showResult.value = false
  if (transit) {
    map.clearOverlays()
  }

  const BMapGL = window.BMapGL
  getPoint(startAddr.value, (startPoint) => {
    getPoint(endAddr.value, (endPoint) => {
      transit = new BMapGL.DrivingRoute(map, {
        renderOptions: {
          map: map,
          autoViewport: true
        },
        onSearchComplete: (results) => {
          if (transit.getStatus() !== BMAP_STATUS_SUCCESS) {
            ElMessage.error('路线规划失败')
            return
          }
          const plan = results.getPlan(0)
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
  background: #f8f8f8;
  min-height: 100vh;
}
#allmap {
  width: 100%;
  height: calc(100vh - 185px);
  margin-top: 85px;
  overflow: hidden;
  position: relative;
}
.map-approval-number {
  position: fixed;
  left: 0;
  right: 0;
  font-size: 11px;
  color: #333;
  background-color: rgba(255, 255, 255, 0.9);
  padding: 5px 15px;
  text-align: center;
  z-index: 1001;
  border-bottom: 1px solid #eee;
}
.search-container {
  position: fixed;
  top: 75px;
  left: 0;
  right: 0;
  padding: 15px;
  background: white;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
  z-index: 1000;
}
.search-container :deep(.el-input) {
  flex: 1;
  margin-right: 10px;
}
.search-container :deep(.el-button) {
  flex: 0 0 auto;
}
.result-box {
  position: fixed;
  bottom: 60px;
  left: 15px;
  right: 15px;
  background: white;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
  z-index: 1000;
}
</style>
