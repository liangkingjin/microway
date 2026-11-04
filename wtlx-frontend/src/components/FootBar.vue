<template>
  <div class="foot">
    <div class="foot-box" :class="{ active: activeBtn === 1 }" @click="toPage(1)">
      <div class="foot-view"><el-icon><HomeFilled /></el-icon></div>
      <div class="foot-text">首页</div>
    </div>
    <div class="foot-box" :class="{ active: activeBtn === 2 }" @click="toPage(2)">
      <div class="foot-view"><el-icon><MapLocation /></el-icon></div>
      <div class="foot-text">行程</div>
    </div>
    <div class="foot-box" :class="{ active: activeBtn === 4 }" @click="toPage(4)">
      <div class="foot-view"><el-icon><User /></el-icon></div>
      <div class="foot-text">我的</div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

// 底部导航（原 js/footer.js 全局 footBar 组件迁移，跳转逻辑与原一致）
const props = defineProps({
  activeBtn: { type: Number, default: 1 }
})

const router = useRouter()
const ROUTE_MAP = {
  1: '/index',
  2: '/map',
  4: '/info'
}
function toPage(i) {
  router.push(ROUTE_MAP[i] || '/index')
}
</script>

<style scoped>
/* 样式在全局 main.css 中（.foot/.foot-box 等），无需重复 */
</style>
