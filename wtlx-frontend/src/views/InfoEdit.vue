<template>
  <div class="info-edit-page">
    <HeaderBar title="资料编辑" :show-back="true" />
    <div class="edit-container">
      <div class="info-box">
        <div class="info-item" @click="editAvatar">
          <div class="info-label">头像</div>
          <div class="info-btn">
            <img width="35" :src="user.icon || '/imgs/icons/default-icon.jpg'" alt="">
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="divider"></div>
        <div class="info-item" @click="editNickname">
          <div class="info-label">昵称</div>
          <div class="info-btn">
            <div>{{ user.nickName }}</div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="divider"></div>
        <div class="info-item" @click="editIntroduce">
          <div class="info-label">个人介绍</div>
          <div class="info-btn">
            <div style="overflow: hidden; width: 150px;text-align: right">{{ info.introduce || '介绍一下自己' }}</div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
      <div class="info-box">
        <div class="info-item" @click="editGender">
          <div class="info-label">性别</div>
          <div class="info-btn">
            <div>{{ info.gender === 0 || info.gender === '男' ? '男' : (info.gender === 1 || info.gender === '女' ? '女' : '选择') }}</div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="divider"></div>
        <div class="info-item" @click="editCity">
          <div class="info-label">城市</div>
          <div class="info-btn">
            <div>{{ info.city || '选择' }}</div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="divider"></div>
        <div class="info-item" @click="editBirthday">
          <div class="info-label">生日</div>
          <div class="info-btn">
            <div>{{ info.birthday || '添加' }}</div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </div>
    <FootBar :active-btn="4" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import FootBar from '../components/FootBar.vue'
import request from '../api/request'

const router = useRouter()

const user = ref({})
const info = ref({})

onMounted(() => {
  checkLogin()
})

function checkLogin() {
  request.get('/user/me')
    .then(res => {
      user.value = res.data
      try {
        info.value = JSON.parse(sessionStorage.getItem('userInfo')) || {}
      } catch (e) {
        info.value = {}
      }
    })
    .catch(err => {
      ElMessage.error(err)
    })
}

// 更新用户信息通用方法
function updateUserInfo(field, value) {
  const updateData = {}
  if (field === 'gender') {
    updateData[field] = value === '男' ? 0 : 1
  } else if (field === 'signature') {
    updateData['introduce'] = value
  } else {
    updateData[field] = value
  }

  request.put('/user/update', updateData)
    .then(() => {
      if (field === 'signature') {
        info.value['introduce'] = value
      } else {
        info.value[field] = value
      }
      sessionStorage.setItem('userInfo', JSON.stringify(info.value))
      ElMessage.success('更新成功')
      refreshUserInfo()
    })
    .catch(err => {
      console.error('更新用户信息失败', err)
      ElMessage.error('更新失败：' + (err.message || err))
    })
}

function refreshUserInfo() {
  request.get('/user/me')
    .then(res => {
      user.value = res.data
      return request.get('/user/info/' + res.data.id)
    })
    .then(res => {
      if (res.data) {
        info.value = res.data
        sessionStorage.setItem('userInfo', JSON.stringify(res.data))
      }
    })
    .catch(err => {
      console.error('刷新用户信息失败', err)
      ElMessage.error('刷新信息失败：' + (err.message || err))
    })
}

function editAvatar() {
  const fileInput = document.createElement('input')
  fileInput.type = 'file'
  fileInput.accept = 'image/*'
  fileInput.onchange = (e) => {
    const file = e.target.files[0]
    if (!file) return
    const formData = new FormData()
    formData.append('file', file)
    request.post('/upload/blog', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
      .then(res => {
        if (res.data) {
          const iconPath = '/imgs' + res.data
          updateUserInfo('icon', iconPath)
          user.value.icon = iconPath
        }
      })
      .catch(err => {
        console.error('上传头像失败', err)
        ElMessage.error('上传失败：' + (err.message || err))
      })
  }
  fileInput.click()
}

function editNickname() {
  ElMessageBox.prompt('请输入新的昵称', '修改昵称', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: user.value.nickName,
    inputPattern: /^.{1,20}$/,
    inputErrorMessage: '昵称长度应在1-20个字符之间'
  }).then(({ value }) => {
    if (value === user.value.nickName) return
    request.put('/user/update', { nickName: value })
      .then(() => {
        user.value.nickName = value
        ElMessage.success('昵称修改成功')
        refreshUserInfo()
      })
      .catch(err => {
        console.error('修改昵称失败', err)
        ElMessage.error('修改失败：' + (err.message || err))
      })
  }).catch(() => {})
}

function editIntroduce() {
  ElMessageBox.prompt('请输入个人介绍', '修改个人介绍', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: info.value.introduce || '',
    inputType: 'textarea',
    inputPlaceholder: '介绍一下自己吧'
  }).then(({ value }) => {
    if (value === info.value.introduce) return
    updateUserInfo('introduce', value)
  }).catch(() => {})
}

function editGender() {
  ElMessageBox.confirm('请选择您的性别', '选择性别', {
    confirmButtonText: '男',
    cancelButtonText: '女',
    distinguishCancelAndClose: true,
    customClass: 'gender-select-dialog'
  }).then(() => {
    if (info.value.gender !== 0 && info.value.gender !== '男') {
      updateUserInfo('gender', '男')
    }
  }).catch(action => {
    if (action === 'cancel' && info.value.gender !== 1 && info.value.gender !== '女') {
      updateUserInfo('gender', '女')
    }
  })
}

function editCity() {
  ElMessageBox.prompt('请输入所在城市', '修改城市', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: info.value.city || '',
    inputPattern: /^.{1,20}$/,
    inputErrorMessage: '城市名称长度应在1-20个字符之间'
  }).then(({ value }) => {
    if (value === info.value.city) return
    updateUserInfo('city', value)
  }).catch(() => {})
}

function editBirthday() {
  ElMessageBox.prompt('请输入生日 (格式: YYYY-MM-DD)', '修改生日', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: info.value.birthday || '',
    inputPattern: /^\d{4}-\d{2}-\d{2}$/,
    inputErrorMessage: '请按照YYYY-MM-DD格式输入日期，例如：1990-01-01'
  }).then(({ value }) => {
    if (value === info.value.birthday) return
    const date = new Date(value)
    if (isNaN(date.getTime())) {
      ElMessage.error('请输入有效的日期')
      return
    }
    updateUserInfo('birthday', value)
  }).catch(() => {})
}
</script>

<style scoped>
/* ==========================================================================
   资料编辑页 — 布局与样式统一（设计令牌与 Info.vue 个人主页保持一致）
   说明：template / script 结构与交互逻辑未改动，本文件仅重写样式层。
   1) 移除遗留的 70px 顶部占位（HeaderBar 为 sticky，本身占据文档流高度）
   2) 左右边距统一走 --pp-gutter，与 Info.vue 版心对齐
   3) 修复右侧取值文字继承 html 的 12px 字号的层级错乱、头像 35px 尺寸不一、
      模板内写死 width:150px 导致的窄屏挤压
   ========================================================================== */
.info-edit-page {
  /* ---- 间距节奏 ---- */
  --pp-gap-xs: 4px;
  --pp-gap-sm: 8px;
  --pp-gap-md: 12px;
  --pp-gap-lg: 16px;
  --pp-gap-xl: 24px;

  /* ---- 圆角 ---- */
  --pp-radius-sm: 8px;
  --pp-radius-md: 12px;

  /* ---- 配色 ---- */
  --pp-brand: #409eff;
  --pp-bg: #f5f7fa;
  --pp-surface: #ffffff;
  --pp-surface-muted: #f7f9fc;
  --pp-surface-active: #eff2f7;
  --pp-text-1: #303133;
  --pp-text-2: #606266;
  --pp-text-3: #909399;
  --pp-text-4: #c0c4cc;
  --pp-border: #ebeef5;

  /* ---- 阴影 ---- */
  --pp-shadow-card: 0 2px 12px rgba(31, 45, 61, 0.06);

  /* ---- 字体与字阶 ---- */
  --pp-font: -apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB",
    "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  --pp-fs-lg: 16px;
  --pp-fs-md: 14px;
  --pp-fs-sm: 13px;

  /* ---- 版心 ---- */
  --pp-page-max: 720px;
  --pp-gutter: max(var(--pp-gap-lg), calc((100% - var(--pp-page-max)) / 2));

  background: var(--pp-bg);
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: calc(66px + env(safe-area-inset-bottom, 0px));
  font-family: var(--pp-font);
  -webkit-tap-highlight-color: transparent;
}

.edit-container {
  padding: var(--pp-gap-lg) var(--pp-gutter) var(--pp-gap-xl);
}

.info-box {
  background: var(--pp-surface);
  border-radius: var(--pp-radius-md);
  overflow: hidden;
  margin-bottom: var(--pp-gap-lg);
  box-shadow: var(--pp-shadow-card);
}
.info-box:last-child {
  margin-bottom: 0;
}

.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pp-gap-md);
  min-height: 60px; /* 统一行高：头像行（36px 图 + 24px 内边距）与文字行一致 */
  padding: var(--pp-gap-md) var(--pp-gap-lg);
  cursor: pointer;
  transition: background-color 0.2s ease;
}
.info-item:hover {
  background-color: var(--pp-surface-muted);
}
.info-item:active {
  background-color: var(--pp-surface-active);
}

.info-label {
  flex: 0 0 auto;
  font-size: var(--pp-fs-lg);
  font-weight: 500;
  line-height: 1.5;
  color: var(--pp-text-1);
}

.info-btn {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--pp-gap-sm);
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--pp-fs-md); /* 原继承 html 的 12px，与左侧 16px 标签层级失衡 */
  line-height: 1.5;
  color: var(--pp-text-2);
}
.info-btn > div {
  min-width: 0;
  max-width: min(46vw, 320px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
}
/* 模板内写死的 width:150px 在窄屏会挤压行内容，改为自适应 + 上限截断 */
.info-btn > div[style] {
  width: auto !important;
}
.info-btn img {
  flex: 0 0 auto;
  display: block;
  width: 36px; /* 原 35px，与统一后的行高对齐 */
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--pp-border);
  object-fit: cover;
  margin-right: 0;
}
.info-btn .arrow {
  flex: 0 0 auto;
  margin-left: 0;
  color: var(--pp-text-4);
  font-size: var(--pp-fs-md);
}

.divider {
  height: 1px;
  margin: 0 var(--pp-gap-lg);
  background-color: var(--pp-border);
}

/* 弹窗宽度沿用原规则（ElMessageBox 挂载在 body，作用域样式不生效，仅作兜底声明） */
:deep(.el-message-box) {
  width: 85% !important;
  max-width: 320px !important;
}
:deep(.el-textarea__inner) {
  min-height: 80px !important;
}

/* ---------------------- 响应式 ---------------------- */
@media (min-width: 768px) {
  .info-edit-page {
    --pp-page-max: 760px;
  }
  .edit-container {
    padding-top: var(--pp-gap-xl);
  }
  .info-item {
    min-height: 64px;
    padding: var(--pp-gap-md) var(--pp-gap-xl);
  }
  .divider {
    margin: 0 var(--pp-gap-xl);
  }
  .info-btn > div {
    max-width: 380px;
  }
}

@media (max-width: 360px) {
  .info-label {
    font-size: var(--pp-fs-md);
  }
  .info-item {
    padding: var(--pp-gap-md) var(--pp-gap-md);
  }
  .divider {
    margin: 0 var(--pp-gap-md);
  }
}
</style>
