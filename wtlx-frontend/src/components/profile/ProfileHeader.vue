<template>
  <div class="profile-header">
    <div class="profile-header__avatar">
      <img :src="avatar" :alt="nickName">
    </div>

    <div class="profile-header__main">
      <div class="profile-header__name wt-ellipsis">{{ nickName }}</div>
      <div class="wt-pill wt-pill--ghost" @click="emit('edit')">编辑资料</div>
    </div>

    <div class="wt-pill wt-pill--ghost profile-header__logout" @click="emit('logout')">
      退出登录
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { DEFAULT_AVATAR } from '../../constants'

// 个人主页头部区块：头像 + 昵称 + 编辑资料 / 退出登录
// 只做展示与事件上抛，跳转与接口调用由父级视图决定
const props = defineProps({
  user: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['edit', 'logout'])

const avatar = computed(() => props.user.icon || DEFAULT_AVATAR)
const nickName = computed(() => props.user.nickName || '')
</script>

<style scoped>
.profile-header {
  display: flex;
  align-items: center;
  gap: var(--wt-space-4);
  padding: calc(var(--wt-space-6) + env(safe-area-inset-top, 0px)) var(--wt-gutter)
    var(--wt-space-6);
  /* 渐变收尾色用页面底色，避免与 .wt-page 背景出现色差断层 */
  background-image: radial-gradient(
      120% 90% at 12% -10%,
      rgba(255, 255, 255, 0.26) 0%,
      rgba(255, 255, 255, 0) 55%
    ),
    linear-gradient(
      180deg,
      #3183dd 0%,
      var(--wt-color-brand) 38%,
      #86bdf7 70%,
      #d6e8fa 88%,
      var(--wt-bg-page) 100%
    );
}
.profile-header__avatar {
  flex: 0 0 auto;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid #fff;
  background: #fff;
  box-shadow: 0 4px 14px rgba(23, 62, 102, 0.18);
}
.profile-header__avatar img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.profile-header__main {
  flex: 1 1 auto;
  min-width: 0; /* 长昵称不再把右侧按钮挤出屏幕 */
  color: #fff;
}
.profile-header__name {
  margin: 0 0 10px;
  font-size: var(--wt-fs-xl);
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: 0.2px;
}
.profile-header__logout {
  flex: 0 0 auto;
}

@media (min-width: 768px) {
  .profile-header {
    padding-top: calc(32px + env(safe-area-inset-top, 0px));
    padding-bottom: 28px;
  }
  .profile-header__avatar {
    width: 96px;
    height: 96px;
    border-width: 4px;
  }
  .profile-header__name {
    font-size: 24px;
  }
  .profile-header .wt-pill {
    height: 34px;
    padding: 0 18px;
    font-size: var(--wt-fs-sm);
  }
}

@media (max-width: 360px) {
  .profile-header {
    gap: var(--wt-space-3);
  }
  .profile-header__avatar {
    width: 68px;
    height: 68px;
  }
  .profile-header__name {
    font-size: 18px;
  }
  .profile-header .wt-pill {
    padding: 0 10px;
  }
}
</style>
