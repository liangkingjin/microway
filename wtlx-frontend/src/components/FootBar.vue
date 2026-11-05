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

// 底部导航：首页 / 行程 / 我的（activeBtn 1/2/4，0 表示不高亮）
const props = defineProps({
  activeBtn: { type: Number, default: 1 }
})

const router = useRouter()
const ROUTE_MAP = {
  1: '/home',
  2: '/map-navigation',
  4: '/profile'
}
function toPage(i) {
  router.push(ROUTE_MAP[i] || '/home')
}
</script>

<style scoped>
/* 布局与配色由全局 main.css 提供 */
</style>
