/**
 * 行程地点标注 → 百度地图真实路线的共享逻辑。
 *
 * 「旅行计划详情」里每天的内嵌地图，与底栏「行程」的地图页共用这一份：
 * 地点解析、编号标记、驾车/步行串联只实现一次，避免两处行为逐渐漂移。
 * 地图实例由调用方各自持有（多张地图可并存），本模块只负责往实例上画。
 */
import { loadBMapGL } from './bmap'

const BMAP_AK = 'd3SExcYB3XNkA5KXvqdpAvsAilJxiNKx'
export const POI_PATTERN = /【([^】]{1,40})】/

export function loadMapSdk() {
  return loadBMapGL(BMAP_AK)
}

export function bmapStatusOk(status) {
  return status === (window.BMAP_STATUS_SUCCESS ?? 0)
}

/** 从行程条目里取出 AI 标注的【真实地点名】，保持条目先后顺序 */
export function extractStops(items) {
  return (items || [])
    .map(item => {
      const match = POI_PATTERN.exec(item.content || '')
      return match
        ? { name: match[1].trim(), time: item.time || '', content: item.content || '' }
        : null
    })
    .filter(Boolean)
}

export function escapeHtml(value) {
  return String(value || '').replace(/[&<>"']/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[ch]))
}

/** 单个地点名 → 百度坐标；失败或超时返回 null，绝不把 Promise 悬在空中 */
function geocodePoi(map, BMapGL, name, timeout = 6000) {
  return new Promise((resolve) => {
    let settled = false
    const done = (hit) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(hit)
    }
    const timer = setTimeout(() => done(null), timeout)
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
      // forceLocal:false 允许跳出地图当前城市，否则异地行程（人在武汉、计划在北京）搜不到 POI
      search.search(name, { forceLocal: false })
    } catch (e) {
      done(null)
    }
  })
}

function addNumberedMarkers(map, BMapGL, points) {
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
    // 行程文案来自模型输出，进 InfoWindow 前必须转义
    const html = '<div style="font-size:13px;line-height:1.6"><b>'
      + escapeHtml(p.seq + '. ' + p.stop.name) + '</b><br/>'
      + escapeHtml(p.stop.time) + '<br/>'
      + escapeHtml(p.stop.content) + '</div>'
    const info = new BMapGL.InfoWindow(html, { width: 230 })
    marker.addEventListener('click', () => map.openInfoWindow(info, p.point))
  })
}

/**
 * 把一天（或一段）的地点串成真实道路路线并画到地图上。
 *
 * @returns {Promise<{ok:boolean, reason?:string, points:Array, failed:string[],
 *                    duration?:string, distance?:string, truncated?:boolean, segments?:number}>}
 */
export async function renderItineraryRoute({ map, BMapGL, stops, mode = 'driving' }) {
  const points = []
  const failed = []
  for (let i = 0; i < stops.length; i++) {
    const hit = await geocodePoi(map, BMapGL, stops[i].name)
    if (hit) points.push({ ...hit, stop: stops[i], seq: i + 1 })
    else failed.push(stops[i].name)
  }
  if (points.length < 2) {
    return { ok: false, reason: '可定位地点不足 2 个', points, failed }
  }

  addNumberedMarkers(map, BMapGL, points)

  if (mode === 'walking') {
    // 步行不支持途经点，逐段规划真实步行路线
    for (let i = 0; i < points.length - 1; i++) {
      const walk = new BMapGL.WalkingRoute(map, {
        renderOptions: { map, autoViewport: i === 0 }
      })
      walk.search(points[i].point, points[i + 1].point)
    }
    return { ok: true, points, failed, segments: points.length - 1 }
  }

  // 驾车支持途经点，一次调用把整天的景点串成真实道路
  const first = points[0].point
  const last = points[points.length - 1].point
  let mids = points.slice(1, -1).map(p => p.point)
  let truncated = false
  if (mids.length > 10) {
    mids = mids.slice(0, 10)
    truncated = true
  }

  const result = await new Promise((resolve) => {
    let settled = false
    const finish = (value) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(value)
    }
    const timer = setTimeout(() => finish({ ok: false, reason: '路线规划超时' }), 12000)
    const driving = new BMapGL.DrivingRoute(map, {
      renderOptions: { map, autoViewport: true },
      onSearchComplete: (results) => {
        if (!bmapStatusOk(driving.getStatus())) {
          finish({ ok: false, reason: '路线规划失败' })
          return
        }
        const plan = results.getPlan(0)
        if (!plan) {
          finish({ ok: false, reason: '未找到可用路线' })
          return
        }
        finish({ ok: true, duration: plan.getDuration(true), distance: plan.getDistance(true) })
      }
    })
    driving.search(first, last, { waypoints: mids })
  })

  return { ...result, points, failed, truncated }
}
