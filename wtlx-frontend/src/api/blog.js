import request from './request'

/**
 * 游记接口（个人主页"游记"页签使用）
 * 原 Profile.vue 内联的 axios 调用统一收敛到本文件
 */

/** 我的游记列表（GET /blog/of/me） */
export function getMyBlogs() {
  return request.get('/blog/of/me')
}

/** 删除游记（DELETE /blog/:id） */
export function deleteBlog(blogId) {
  return request.delete(`/blog/${blogId}`)
}
