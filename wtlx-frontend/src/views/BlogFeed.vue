<template>
  <div class="watch-page">
    <HeaderBar title="看游记" :show-back="true" />
    <div class="blog-list" @scroll="onScroll">
      <div class="blog-box" v-for="b in blogs" :key="b.id">
        <div class="blog-img" @click="toBlogDetail(b)"><img :src="b.img" alt=""></div>
        <div class="blog-title">{{ b.title }}</div>
        <div class="blog-foot">
          <div class="blog-user-icon"><img :src="b.icon || '/imgs/icons/default-icon.jpg'" alt=""></div>
          <div class="blog-user-name">{{ b.name }}</div>
          <div class="blog-liked" @click="addLike(b)">
            <svg t="1646634642977" class="icon" viewBox="0 0 1024 1024" version="1.1"
              xmlns="http://www.w3.org/2000/svg" width="14" height="14">
              <path d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z m0-64h-44.9C427.2 0 400 27.2 400 60.7c0 127.1-39.1 251.2-112 355.3v484.1c0 68.4 55.5 123.9 123.9 123.9h317c122.7 0 227.2-89.3 246.3-210.5l47.9-303.4c7.8-49.4-30.4-94.1-80.4-94.1H671.6c-50.9 0-90.5-44.4-84.6-95l23.5-203.4C617.7 55 568.7 0 505.6 0z"
              :fill="b.isLike ? '#ff6633' : '#82848a'"></path>
            </svg>
            {{ b.liked }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'

const router = useRouter()

const isReachBottom = ref(false)
const blogs = ref([])
const current = ref(1)

onMounted(() => {
  queryTypes()
  queryHotBlogsScroll()
})

function queryTypes() {
  request.get('/shop-type/list')
    .then(() => {})
    .catch(err => ElMessage.error(err))
}

function queryHotBlogsScroll() {
  request.get('/blog/hot?current=' + current.value)
    .then(res => {
      const data = res.data || []
      data.forEach(b => b.img = b.images.split(',')[0])
      blogs.value = blogs.value.concat(data)
    })
    .catch(err => ElMessage.error(err))
}

function addLike(b) {
  request.put('/blog/like/' + b.id)
    .then(() => {
      queryBlogById(b)
    })
    .catch(err => ElMessage.error(err))
}

function queryBlogById(b) {
  request.get('/blog/' + b.id)
    .then(res => {
      const data = res.data
      b.liked = data.liked
      b.isLike = data.isLike
    })
    .catch(() => {
      b.liked++
    })
}

function onScroll(e) {
  const scrollTop = e.target.scrollTop
  const offsetHeight = e.target.offsetHeight
  const scrollHeight = e.target.scrollHeight
  if (scrollTop + offsetHeight > scrollHeight && !isReachBottom.value) {
    isReachBottom.value = true
    current.value++
    queryHotBlogsScroll()
  } else {
    isReachBottom.value = false
  }
}

function toBlogDetail(b) {
  router.push('/blog-detail?id=' + b.id)
}
</script>

<style scoped>
/* 样式迁移自原 watch.html <style> 内联块 */
.watch-page {
  background: #f8f8f8;
  min-height: 100vh;
}
.blog-list {
  background-color: #f8f8f8;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  overflow-y: auto;
  padding: 12px;
  min-height: calc(100vh - 100px);
}
.blog-list::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.blog-list::-webkit-scrollbar-track {
  background: #f5f5f5;
  border-radius: 3px;
}
.blog-list::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 3px;
}
.blog-box {
  width: calc(50% - 6px);
  background-color: #fff;
  margin-bottom: 12px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  position: relative;
}
.blog-img {
  position: relative;
  padding-bottom: 75%;
  overflow: hidden;
  background: #f5f5f5;
}
.blog-img img {
  position: absolute;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.blog-title {
  padding: 12px 14px;
  font-size: 14px;
  color: #333;
  line-height: 1.5;
  height: 42px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 4px;
  font-weight: 500;
}
.blog-foot {
  display: flex;
  align-items: center;
  padding: 0 14px 14px;
}
.blog-user-icon {
  width: 28px;
  height: 28px;
  margin-right: 8px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.blog-user-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.blog-user-name {
  flex: 1;
  font-size: 13px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.blog-liked {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 20px;
  background: #f7f7f7;
  margin-left: 8px;
  cursor: pointer;
  user-select: none;
}
.blog-liked svg {
  margin-right: 4px;
}
</style>
