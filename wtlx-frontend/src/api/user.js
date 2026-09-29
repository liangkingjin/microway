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

/** 当前登录用户（GET /user/me） */
export function getMe() {
  return request.get('/user/me')
}

/** 用户扩展资料（GET /user/info/:id） */
export function getUserInfo(userId) {
  return request.get('/user/info/' + userId)
}

/** 更新当前用户资料（PUT /user/update） */
export function updateUser(payload) {
  return request.put('/user/update', payload)
}

/** 退出登录（POST /user/logout） */
export function logout() {
  return request.post('/user/logout')
}

/** 上传头像（POST /upload/blog，multipart/form-data） */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/upload/blog', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
