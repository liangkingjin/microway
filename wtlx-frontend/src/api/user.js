import request from './request'

// 用户相关接口（原页面内直接 axios.post 的写法统一收敛到本文件）
export function login(form) {
  // POST /user/login 成功返回 token 字符串（响应体 data 字段）
  return request.post('/user/login', form)
}

export function sendCode(phone) {
  // POST /user/code?phone= 发送验证码，响应体 data 为验证码字符串（原页面自动填充输入框）
  return request.post('/user/code?phone=' + phone)
}
