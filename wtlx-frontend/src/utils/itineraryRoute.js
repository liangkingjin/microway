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

/** 地址解析（不依赖地图当前城市），用于锚定行程所在城市 / LocalSearch 失败的兜底 */
function geocodeByAddress(BMapGL, name, timeout = 6000) {
  return new Promise((resolve) => {
    let settled = false
    const done = (point) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(point || null)
    }
    const timer = setTimeout(() => done(null), timeout)
    try {
      const geocoder = new BMapGL.Geocoder()
      geocoder.getPoint(name, (point) => done(point))
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
    // 行程文案来自模型输出，进 InfoWindow 前必须转义；链接走 hash 路由，直接给 a[href] 即可
    const html = '<div style="font-size:13px;line-height:1.6"><b>'
      + escapeHtml(p.seq + '. ' + p.stop.name) + '</b><br/>'
      + escapeHtml(p.stop.time) + '<br/>'
      + escapeHtml(p.stop.content) + '<br/>'
      + '<a href="#/spot-detail?name=' + encodeURIComponent(p.stop.name)
      + '" style="display:inline-block;margin-top:6px;color:#409eff;text-decoration:none">'
      + '查看景点详情 ›</a></div>'
    const info = new BMapGL.InfoWindow(html, { width: 240 })
    marker.addEventListener('click', () => map.openInfoWindow(info, p.point))
  })
}

/** 百度单次驾车最多 10 个途经点；超出时按顺序切成多段连续路线，段与段共享端点 */
const MAX_WAYPOINTS = 10

function splitLegs(points) {
  const legs = []
  let start = 0
  while (start < points.length - 1) {
    const end = Math.min(start + MAX_WAYPOINTS + 1, points.length - 1)
    legs.push(points.slice(start, end + 1))
    start = end
  }
  return legs
}

/** 百度返回「1小时20分钟」「46分钟」，按字符串解析再累加，避免猜 false 分支的数值单位 */
function parseDurationToMinutes(text) {
  const source = String(text || '')
  const hours = /(\d+)\s*小时/.exec(source)
  const minutes = /(\d+)\s*分钟/.exec(source)
  let total = 0
  if (hours) total += Number(hours[1]) * 60
  if (minutes) total += Number(minutes[1])
  return total
}

/** 百度返回「16.2公里」「800米」 */
function parseDistanceToMeters(text) {
  const source = String(text || '')
  const km = /([\d.]+)\s*公里/.exec(source)
  if (km) return Number(km[1]) * 1000
  const m = /([\d.]+)\s*米/.exec(source)
  if (m) return Number(m[1])
  return 0
}

function searchDrivingLeg(map, BMapGL, leg) {
  return new Promise((resolve) => {
    let settled = false
    const finish = (value) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve(value)
    }
    const timer = setTimeout(() => finish(null), 15000)
    const driving = new BMapGL.DrivingRoute(map, {
      renderOptions: { map, autoViewport: false },
      onSearchComplete: (results) => {
        if (!bmapStatusOk(driving.getStatus())) {
          finish(null)
          return
        }
        const plan = results.getPlan(0)
        if (!plan) {
          finish(null)
          return
        }
        finish({
          minutes: parseDurationToMinutes(plan.getDuration(true)),
          meters: parseDistanceToMeters(plan.getDistance(true))
        })
      }
    })
    const mids = leg.slice(1, -1).map(p => p.point)
    driving.search(leg[0].point, leg[leg.length - 1].point, { waypoints: mids })
  })
}

function formatDuration(minutes) {
  const total = Math.max(0, Math.round(minutes))
  if (total < 60) return total + '分钟'
  const h = Math.floor(total / 60)
  const m = total % 60
  return m === 0 ? h + '小时' : h + '小时' + m + '分钟'
}

function formatDistance(meters) {
  if (meters < 1000) return Math.round(meters) + '米'
  return (meters / 1000).toFixed(1) + '公里'
}

/** 视野：先把中心落到第一个点（同步生效），再用 setViewport 尝试把全部点位框进来 */
function fitPoints(map, BMapGL, points) {
  if (!points || !points.length) return
  map.centerAndZoom(points[0].point, 13)
  const all = points.map(p => p.point)
  if (typeof map.setViewport === 'function') {
    map.setViewport(all)
  }
}

/**
 * 全局串行队列：一个页面可能同时有「总体路线」和多天「当日路线」多张地图，
 * 每张都要做 N 次 POI 检索 + 路线规划。百度 JSAPI 个人版按 QPS 限流，
 * 并发一高就整批返回失败（表现为"可定位地点不足 2 个"），因此同一时刻只放一张地图去请求。
 * shortcut: 前端串行 + 250ms 间隔；若要支持同屏大量地图，需改用服务端 Web 服务 API 并申请更高配额。
 */
let routeQueue = Promise.resolve()

function enqueue(task) {
  const run = routeQueue.then(task, task)
  routeQueue = run.catch(() => {})
  return run
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * 把一天（或一整趟行程）的地点串成真实道路路线并画到地图上。
 * 超过 10 个途经点时自动分段串联，仍是一条完整动线。
 *
 * @returns {Promise<{ok:boolean, reason?:string, points:Array, failed:string[],
 *                    duration?:string, distance?:string, truncated?:boolean, segments?:number}>}
 */
export function renderItineraryRoute(options) {
  return enqueue(() => renderItineraryRouteInternal(options))
}

/** 一个地点距另一个地点的直线距离（公里），足够做可信度判断 */
function distanceKm(a, b) {
  const dx = (a.lng - b.lng) * 111 * Math.cos((a.lat * Math.PI) / 180)
  const dy = (a.lat - b.lat) * 111
  return Math.sqrt(dx * dx + dy * dy)
}

/** 单日行程合理半径上限：超出说明 POI 名被错误解析到了外地（实测出现过 363 公里的假路线） */
const MAX_TRUSTED_KM = 150

async function renderItineraryRouteInternal({ map, BMapGL, stops, mode = 'driving' }) {
  // LocalSearch 是按「地图当前城市」检索的：默认中心在北京时，「黄鹤楼」会被匹配成
  // 北京的同名点位，算出 3 公里、9 分钟的假路线（实测踩过）。所以先用地址解析把
  // 视野锚到首个景点所在城市，再逐个检索，命中城市才正确。
  const anchor = await geocodeByAddress(BMapGL, stops[0].name)
  if (anchor) {
    map.centerAndZoom(anchor, 12)
  }

  const points = []
  const failed = []
  for (let i = 0; i < stops.length; i++) {
    if (i > 0) {
      // 相邻两次检索之间留间隔，稳定命中 QPS 限制以内
      await sleep(250)
    }
    let hit = await geocodePoi(map, BMapGL, stops[i].name)
    if (!hit) {
      // 检索不到时退回地址解析，至少拿到一个可用坐标
      const point = await geocodeByAddress(BMapGL, stops[i].name)
      if (point) hit = { point, title: stops[i].name }
    }
    // 同一天的景点不该相隔几百公里：超出的多半是把 POI 名当路名解析了，宁可当作未定位
    if (hit && points.length && distanceKm(points[0].point, hit.point) > MAX_TRUSTED_KM) {
      failed.push(stops[i].name)
      continue
    }
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
        renderOptions: { map, autoViewport: false }
      })
      walk.search(points[i].point, points[i + 1].point)
    }
    fitPoints(map, BMapGL, points)
    return { ok: true, points, failed, segments: points.length - 1 }
  }

  const legs = splitLegs(points)
  const legResults = await Promise.all(legs.map(leg => searchDrivingLeg(map, BMapGL, leg)))
  const okLegs = legResults.filter(Boolean)
  // 单段失败不影响其余段已画出的路线，但完全无结果要如实报失败
  if (okLegs.length === 0) {
    return { ok: false, reason: '路线规划失败，请稍后重试', points, failed }
  }
  const minutes = okLegs.reduce((sum, r) => sum + r.minutes, 0)
  const meters = okLegs.reduce((sum, r) => sum + r.meters, 0)

  // 多段各自 autoViewport 会互相覆盖视野，统一在最后把全部点位框进来
  fitPoints(map, BMapGL, points)

  return {
    ok: true,
    points,
    failed,
    duration: minutes > 0 ? formatDuration(minutes) : undefined,
    distance: meters > 0 ? formatDistance(meters) : undefined,
    segments: legs.length,
    truncated: legResults.some(r => r === null)
  }
}

