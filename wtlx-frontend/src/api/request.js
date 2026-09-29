import axios from 'axios'

// 业务后端统一请求实例（原 js/common.js 的 Vue2 版 axios 全局配置迁移）
// 约定：浏览器经 /api 前缀访问（nginx 反代 127.0.0.1:9528 并 rewrite 掉前缀）
// 响应体 { success, data, errorMsg }：success 为真才 resolve，返回整个响应体（与原行为一致，调用方取 res.data）
const request = axios.create({
  baseURL: '/api',
  timeout: 2000
})

// 请求拦截器：携带 sessionStorage 中的 token（原 authorization 头）
request.interceptors.request.use(
  config => {
    const token = sessionStorage.getItem('token')
    if (token) config.headers['authorization'] = token
    return config
  },
  error => {
    console.log(error)
    return Promise.reject(error)
  }
)

// 响应拦截器：success 校验 + 401 跳登录
request.interceptors.response.use(
  function (response) {
    if (!response.data.success) {
      return Promise.reject(response.data.errorMsg)
    }
    return response.data
  },
  function (error) {
    console.log(error)
    if (error.response && error.response.status === 401) {
      setTimeout(() => {
        if (window.location.hash !== '#/login') {
          window.location.hash = '#/login'
        }
      }, 200)
      return Promise.reject('请先登录')
    }
    return Promise.reject('服务器异常')
  }
)

// 参数序列化：跳过空值（与原 paramsSerializer 行为一致）
request.defaults.paramsSerializer = {
  serialize(params) {
    let p = ''
    Object.keys(params).forEach(k => {
      if (params[k]) p = p + '&' + k + '=' + params[k]
    })
    return p
  }
}

export default request
