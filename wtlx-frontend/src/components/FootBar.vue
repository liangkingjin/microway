<template>
  <div class="foot">
    <div class="foot-box" :class="{ active: activeBtn === 1 }" @click="toPage(1)">
      <div class="foot-view"><el-icon><HomeFilled /></el-icon></div>
      <div class="foot-text">首页</div>
    </div>
    <div class="foot-box" :class="{ active: activeBtn === 2 }" @click="toPage(2)">
      <div class="foot-view"><el-icon><MagicStick /></el-icon></div>
      <div class="foot-text">AI</div>
    </div>
    <div class="foot-box foot-center" @click="toPage(3)">
      <div class="foot-add"><el-icon><Plus /></el-icon></div>
      <div class="foot-text">发布</div>
    </div>
    <div class="foot-box" :class="{ active: activeBtn === 4 }" @click="toPage(4)">
      <div class="foot-view"><el-icon><User /></el-icon></div>
      <div class="foot-text">我的</div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

// 底部导航 4 tab：首页 / AI / 发布 / 我的
// activeBtn 沿用 1/2/4 语义；3 是发布动作（无页面需要高亮），0 表示不高亮
const props = defineProps({
  activeBtn: { type: Number, default: 1 }
})

const router = useRouter()
const ROUTE_MAP = {
  1: '/home',
  2: '/ai-assistant',
  3: '/publish-select',
  4: '/profile'
}
function toPage(i) {
  router.push(ROUTE_MAP[i] || '/home')
}
</script>

<style scoped>
/* 基础布局由全局 main.css 的 .foot/.foot-box/.foot-view/.foot-text 提供 */
.foot-center { color: #0f6e56; }
.foot-add {
  width: 40px;
  height: 40px;
  margin-top: -18px;
  border-radius: 50%;
  background: #1d9e75;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 3px 10px rgba(29, 158, 117, 0.3);
}
</style>
