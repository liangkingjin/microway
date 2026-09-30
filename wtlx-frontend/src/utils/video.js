import { ElMessage } from 'element-plus'

/**
 * 浏览器自动播放策略：带声音的 video.play() 会被直接拒绝（NotAllowedError），
 * 表现为"点了没反应 / 黑屏"。统一策略：
 *   1) 自动播放场景先静音播放（静音是唯一被允许的无手势播放）；
 *   2) 用户点击属于真实手势，此时解除静音再播放。
 */
export function safeAutoPlay(el) {
  if (!el) return
  el.muted = true
  const p = el.play()
  if (p && typeof p.catch === 'function') {
    p.catch(() => {})
  }
}

/** 用户点击：暂停/继续，并在手势内解除静音 */
export function toggleVideo(el) {
  if (!el) return
  if (el.paused) {
    el.muted = false
    const p = el.play()
    if (p && typeof p.catch === 'function') {
      p.catch(() => {})
    }
  } else {
    el.pause()
  }
}

/** 视频加载失败（404 / 编码不支持 / 网络中断）时给出可见反馈，避免只看到黑屏 */
export function onVideoError(title) {
  ElMessage.error(title || '视频加载失败，请检查视频地址或网络')
}
