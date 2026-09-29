import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyBlogs, deleteBlog } from '../api/blog'

/**
 * 个人主页 · 游记区块状态与操作
 */
export function useProfileBlogs() {
  const blogs = ref([])

  async function load() {
    try {
      const res = await getMyBlogs()
      blogs.value = res.data || []
    } catch (err) {
      ElMessage.error(err)
    }
  }

  /** 删除游记：成功后直接从列表中剔除，无需重新请求 */
  async function remove(blogId) {
    try {
      await deleteBlog(blogId)
      ElMessage.success('删除成功')
      blogs.value = blogs.value.filter(item => item.id !== blogId)
    } catch (err) {
      console.error('删除博客失败', err)
      ElMessage.error('删除失败：' + (err.message || err))
    }
  }

  return { blogs, load, remove }
}
