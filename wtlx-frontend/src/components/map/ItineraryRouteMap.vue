<template>
  <div class="itinerary-route">
    <div class="itinerary-route__head">
      <span class="itinerary-route__title">当日路线</span>
      <span class="itinerary-route__meta">{{ summary }}</span>
    </div>
    <div class="itinerary-route__canvas-wrap">
      <div :id="mapId" class="itinerary-route__canvas"></div>
      <div v-if="loading" class="itinerary-route__mask">正在串联景点路线…</div>
    </div>
    <div class="itinerary-route__foot">
      <span v-for="(s, i) in stops" :key="i" class="itinerary-route__stop">
        <b>{{ i + 1 }}</b>{{ s.name }}
      </span>
    </div>
  </div>
</template>

<script setup>
/**
 * 行程内嵌地图：挂在「旅行计划详情」某一天的行程里，挂载即自动把当天带
 * 【地点名】标注的景点解析坐标并串成真实道路路线，无需任何手动操作。
 * 每张地图持有独立实例，故 id 用模块级自增序号保证唯一。
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { loadMapSdk, renderItineraryRoute } from '../../utils/itineraryRoute'

let seq = 0

const props = defineProps({
  stops: { type: Array, default: () => [] },
  mode: { type: String, default: 'driving' }
})

const mapId = 'itinerary-route-map-' + (++seq)
const loading = ref(true)
const hint = ref('')
const duration = ref('')
const distance = ref('')
let map = null
let disposed = false

const summary = computed(() => {
  if (loading.value) return '生成中'
  if (duration.value) return `预计 ${duration.value} · ${distance.value}`
  return hint.value || `已串联 ${props.stops.length} 个地点`
})

async function draw() {
  if (disposed || props.stops.length < 2) return
  loading.value = true
  hint.value = ''
  duration.value = ''
  distance.value = ''
  try {
    const BMapGL = await loadMapSdk()
    if (disposed) return
    if (!map) {
      map = new BMapGL.Map(mapId)
      // 先给一个默认视野，路线规划完成后 autoViewport 会自动缩放到全部景点
      map.centerAndZoom(new BMapGL.Point(116.404, 39.915), 12)
    } else {
      map.clearOverlays()
    }
    const res = await renderItineraryRoute({
      map,
      BMapGL,
      stops: props.stops,
      mode: props.mode
    })
    if (disposed) return
    if (!res.ok) {
      hint.value = res.reason || '路线生成失败'
    } else {
      if (res.duration) {
        duration.value = res.duration
        distance.value = res.distance
      }
      if (res.failed.length) hint.value = '未定位：' + res.failed.join('、')
      else if (res.truncated) hint.value = '途经点超过 10 个，仅取前 10 个'
      else if (!res.duration) hint.value = `步行 ${res.segments} 段已串联`
    }
  } catch (e) {
    if (!disposed) hint.value = '地图加载失败，请检查网络'
  } finally {
    if (!disposed) loading.value = false
  }
}

// 用内容指纹而不是数组引用做依赖：父组件每次渲染会生成新的 stops 数组，
// 直接 watch 引用会导致「重画 → 重渲染 → 再重画」的死循环
const stopKey = computed(() => props.stops.map(s => s.name).join('|') + '::' + props.mode)
watch(stopKey, () => nextTick(draw))

onMounted(() => nextTick(draw))
onBeforeUnmount(() => {
  disposed = true
  if (map) {
    try { map.clearOverlays() } catch (e) { /* 实例已随 DOM 销毁 */ }
    map = null
  }
})
</script>

<style scoped>
.itinerary-route {
  margin: 0 0 12px;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}
.itinerary-route__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 12px;
  background: #f5f9ff;
  border-bottom: 1px solid #e8edf2;
}
.itinerary-route__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--wt-color-brand, #409eff);
}
.itinerary-route__meta {
  font-size: 12px;
  color: #7a8290;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.itinerary-route__canvas-wrap {
  position: relative;
  height: 210px;
  background: #eef2f7;
}
.itinerary-route__canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.itinerary-route__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #7a8290;
  background: rgba(245, 249, 255, 0.85);
  pointer-events: none;
}
.itinerary-route__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  padding: 8px 12px;
  border-top: 1px solid #f0f3f7;
}
.itinerary-route__stop {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #5f5e5a;
}
.itinerary-route__stop b {
  display: inline-block;
  width: 15px;
  height: 15px;
  line-height: 15px;
  text-align: center;
  border-radius: 50%;
  background: var(--wt-color-brand, #409eff);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
}
</style>
