<template>
  <div class="wt-card wt-card--interactive profile-blog-card" v-on="handlers" @click="onClick">
    <div class="profile-blog-card__cover">
      <img :src="cover" :alt="blog.title">
      <!-- 显式删除入口：长按虽然也能删，但没人会想到去长按，入口必须可见 -->
      <button
        type="button"
        class="profile-blog-card__more"
        aria-label="删除这篇游记"
        title="删除这篇游记"
        @mousedown.stop
        @touchstart.stop
        @click.stop="emit('delete', blog)"
      >
        <el-icon><Delete /></el-icon>
      </button>
    </div>

    <div class="profile-blog-card__body">
      <div class="profile-blog-card__title wt-clamp-2">{{ blog.title }}</div>
      <span class="profile-blog-card__stat">
        <img src="/imgs/thumbup.png" alt=""> {{ blog.liked }}
      </span>
      <span class="profile-blog-card__stat">
        <el-icon><ChatDotRound /></el-icon> {{ blog.comments }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ChatDotRound, Delete } from '@element-plus/icons-vue'
import { useLongPress } from '../../composables/useLongPress'

// 游记卡片：右上角按钮 / 长按都可触发删除确认（手势逻辑抽到 useLongPress，事件上抛给父级）
const props = defineProps({
  blog: { type: Object, required: true }
})
const emit = defineEmits(['long-press', 'open', 'delete'])

// 长按删除 / 点击打开详情共存：长按触发后的那次 click 不跳转
const { handlers, justTriggered } = useLongPress(() => emit('long-press', props.blog))

function onClick() {
  if (justTriggered.value) {
    justTriggered.value = false
    return
  }
  emit('open', props.blog)
}

const cover = computed(() => (props.blog.images || '').split(',')[0])
</script>

<style scoped>
.profile-blog-card {
  cursor: pointer;
  display: flex;
  align-items: stretch;
  overflow: hidden;
}
.profile-blog-card:hover {
  transform: translateY(-2px);
}
.profile-blog-card__cover {
  flex: 0 0 40%;
  align-self: flex-start;
  /* aspect-ratio 精准控比（旧的 padding-bottom 百分比基准是卡片整宽，会撑坏布局） */
  aspect-ratio: 4 / 3;
  position: relative;
  overflow: hidden;
  background: #eef1f5;
}
.profile-blog-card__cover img {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
/* 删除按钮：压在封面右上角，半透明底保证任何图片上都看得见 */
.profile-blog-card__more {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 2;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.38);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s;
}
.profile-blog-card__more:hover {
  background: rgba(220, 38, 38, 0.9);
}
.profile-blog-card__body {
  flex: 1 1 auto;
  min-width: 0;
  padding: var(--wt-space-3) var(--wt-space-4);
}
.profile-blog-card__title {
  margin: 0 0 var(--wt-space-2);
  font-size: var(--wt-fs-md);
  font-weight: 500;
  line-height: 1.5;
  color: var(--wt-text-1);
  word-break: break-word;
}
.profile-blog-card__stat {
  display: inline-flex;
  align-items: center;
  gap: var(--wt-space-1);
  margin-right: var(--wt-space-3);
  font-size: var(--wt-fs-xs);
  line-height: 1;
  color: var(--wt-text-3);
  vertical-align: middle;
}
.profile-blog-card__stat img {
  display: block;
  width: 14px;
  height: 14px;
  object-fit: contain;
}
.profile-blog-card__stat .el-icon {
  font-size: var(--wt-fs-md);
}

@media (min-width: 768px) {
  .profile-blog-card__cover {
    flex: 0 0 240px;
  }
  .profile-blog-card__body {
    padding: var(--wt-space-4);
  }
}

@media (max-width: 360px) {
  .profile-blog-card__cover {
    flex: 0 0 36%;
  }
}
</style>
