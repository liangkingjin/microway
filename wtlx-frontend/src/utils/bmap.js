/**
 * 百度地图 GL（BMapGL）异步加载器。
 *
 * 关键点：直接 appendChild 加载 `api?type=webgl&v=1.0&ak=xxx` 时，百度返回的是一段
 * 用 document.write 注入真实脚本（getscript）的引导代码。而"异步加载的外部脚本中的
 * document.write"会被浏览器直接忽略（控制台警告：A call to document.write() ... was ignored），
 * 结果 window.BMapGL 永远是 undefined，new BMapGL.Map() 抛错，地图容器一片空白。
 *
 * 加上 &callback=<全局函数名> 后，百度会改用 createElement + appendChild 加载 getscript，
 * 并在加载完成后回调该全局函数 —— 这才是官方支持的异步加载方式。
 */
const cache = new Map()

export function loadBMapGL(ak, callbackName = 'onBMapGLLoaded') {
  if (window.BMapGL) return Promise.resolve(window.BMapGL)
  if (cache.has(ak)) return cache.get(ak)

  const p = new Promise((resolve, reject) => {
    window[callbackName] = () => {
      delete window[callbackName]
      if (window.BMapGL) resolve(window.BMapGL)
      else reject(new Error('百度地图脚本已加载，但未挂载 window.BMapGL'))
    }
    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = `https://api.map.baidu.com/api?type=webgl&v=1.0&ak=${ak}&callback=${callbackName}`
    script.onerror = () => reject(new Error('百度地图脚本加载失败，请检查网络或 AK 配置'))
    document.head.appendChild(script)
  })

  cache.set(ak, p)
  return p
}
