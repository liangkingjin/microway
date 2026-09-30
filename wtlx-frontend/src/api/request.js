import axios from 'axios'

// 业务后端统一请求实例（原 js/common.js 的 Vue2 版 axios 全局配置迁移）
// 约定：浏览器经 /api 前缀访问（nginx 反代 127.0.0.1:9528 并 rewrite 掉前缀）
// 响应体 { success, data, errorMsg }：success 为真才 resolve，返回整个响应体（与原行为一致，调用方取 res.data）

// 普通业务请求超时时间（30s）
const NORMAL_TIMEOUT = 30000

function applyInterceptors(instance) {
  // 请求拦截器：携带 sessionStorage 中的 token（原 authorization 头）
  instance.interceptors.request.use(
    config => {
      const token = sessionStorage.getItem('token')
      if (token) config.headers['authorization'] = token
      return config
    },
    error => Promise.reject(error)
  )

  // 响应拦截器：success 校验 + 401 跳登录
  instance.interceptors.response.use(
    function (response) {
      if (!response.data.success) {
        return Promise.reject(response.data.errorMsg || '操作失败')
      }
      return response.data
    },
    function (error) {
      if (error.response) {
        if (error.response.status === 401) {
          setTimeout(() => {
            if (window.location.hash !== '#/login') {
              window.location.hash = '#/login'
            }
          }, 200)
          return Promise.reject('请先登录')
        }
        // 透出后端 errorMsg，避免统一被吞成“服务器异常”导致无法定位问题
        const msg = error.response.data && error.response.data.errorMsg
        return Promise.reject(msg || `请求失败（HTTP ${error.response.status}）`)
      }
      if (error.code === 'ECONNABORTED') {
        return Promise.reject('请求超时，请稍后重试')
      }
      return Promise.reject('网络连接失败，请检查后端服务是否已启动')
    }
  )

  // 参数序列化：跳过空值（与原 paramsSerializer 行为一致）
  instance.defaults.paramsSerializer = {
    serialize(params) {
      let p = ''
      Object.keys(params).forEach(k => {
        if (params[k]) p = p + '&' + k + '=' + params[k]
      })
      return p
    }
  }
}

const request = axios.create({
  baseURL: '/api',
  timeout: NORMAL_TIMEOUT
})

// 上传专用实例：不设超时（图片/视频动辄几十 MB，固定超时必然中断）
const uploadRequest = axios.create({
  baseURL: '/api',
  timeout: 0
})

applyInterceptors(request)
applyInterceptors(uploadRequest)

export default request
export { uploadRequest }
