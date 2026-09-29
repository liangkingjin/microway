import { onScopeDispose } from 'vue'

/**
 * 长按手势（鼠标 + 触摸双通道）
 * 返回事件绑定对象，模板里用 v-on="longPress.handlers" 一次性挂载。
 *
 * @param {Function} handler 长按触发回调
 * @param {number} delay 触发阈值（毫秒），默认 800ms
 */
export function useLongPress(handler, delay = 800) {
  let timer = null

  function clear() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  function start() {
    clear()
    timer = setTimeout(() => {
      timer = null
      handler()
    }, delay)
  }

  // 组件卸载 / 作用域销毁时清理，避免回调打到已销毁的组件
  onScopeDispose(clear)

  return {
    start,
    clear,
    handlers: {
      touchstart: start,
      touchend: clear,
      touchmove: clear,
      mousedown: start,
      mouseup: clear,
      mouseleave: clear
    }
  }
}
