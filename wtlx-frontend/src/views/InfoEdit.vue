<template>
  <div class="wt-page profile-edit-page">
    <HeaderBar title="资料编辑" :show-back="true" />

    <div class="profile-edit-page__body wt-container">
      <div class="profile-edit-page__group wt-card">
        <ProfileFieldRow
          label="头像"
          :avatar="user.icon || DEFAULT_AVATAR"
          @click="editAvatar"
        />
        <div class="wt-divider"></div>
        <ProfileFieldRow label="昵称" :value="user.nickName" @click="editNickname" />
        <div class="wt-divider"></div>
        <ProfileFieldRow
          label="个人介绍"
          :value="info.introduce"
          placeholder="介绍一下自己"
          @click="editIntroduce"
        />
      </div>

      <div class="profile-edit-page__group wt-card">
        <ProfileFieldRow label="性别" :value="genderText" placeholder="选择" @click="editGender" />
        <div class="wt-divider"></div>
        <ProfileFieldRow label="城市" :value="info.city" placeholder="选择" @click="editCity" />
        <div class="wt-divider"></div>
        <ProfileFieldRow label="生日" :value="info.birthday" placeholder="添加" @click="editBirthday" />
      </div>
    </div>

    <FootBar :active-btn="4" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import FootBar from '../components/FootBar.vue'
import ProfileFieldRow from '../components/profile/ProfileFieldRow.vue'
import {
  getMe,
  getUserInfo,
  updateUser,
  uploadAvatar
} from '../api/user'
import { DEFAULT_AVATAR } from '../constants'

/**
 * 资料编辑页
 * 本文件只维护 user / info 两份数据与各字段的编辑弹窗交互，
 * 行样式与截断逻辑统一由 ProfileFieldRow 承担。
 */
const USER_INFO_KEY = 'userInfo'

const user = ref({})
const info = ref({})

/** 后端可能返回 0/1 或 '男'/'女'，统一折算为文案 */
const genderText = computed(() => {
  const g = info.value.gender
  if (g === 0 || g === '男') return '男'
  if (g === 1 || g === '女') return '女'
  return ''
})

onMounted(() => {
  loadUser()
})

async function loadUser() {
  try {
    const res = await getMe()
    user.value = res.data
    try {
      info.value = JSON.parse(sessionStorage.getItem(USER_INFO_KEY)) || {}
    } catch (e) {
      info.value = {}
    }
  } catch (err) {
    ElMessage.error(err)
  }
}

/** 更新用户信息通用方法（gender 需折算为 0/1） */
async function updateUserInfo(field, value) {
  const payload = {}
  if (field === 'gender') {
    payload.gender = value === '男' ? 0 : 1
  } else if (field === 'signature') {
    payload.introduce = value
  } else {
    payload[field] = value
  }

  try {
    await updateUser(payload)
    if (field === 'signature') {
      info.value.introduce = value
    } else {
      info.value[field] = value
    }
    sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(info.value))
    ElMessage.success('更新成功')
    refreshUserInfo()
  } catch (err) {
    console.error('更新用户信息失败', err)
    ElMessage.error('更新失败：' + (err.message || err))
  }
}

async function refreshUserInfo() {
  try {
    const meRes = await getMe()
    user.value = meRes.data
    const res = await getUserInfo(meRes.data.id)
    if (res.data) {
      info.value = res.data
      sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(res.data))
    }
  } catch (err) {
    console.error('刷新用户信息失败', err)
    ElMessage.error('刷新信息失败：' + (err.message || err))
  }
}

function editAvatar() {
  const fileInput = document.createElement('input')
  fileInput.type = 'file'
  fileInput.accept = 'image/*'
  fileInput.onchange = e => {
    const file = e.target.files[0]
    if (!file) return
    uploadAvatar(file)
      .then(res => {
        if (res.data) {
          const iconPath = '/imgs' + res.data
          user.value.icon = iconPath
          updateUserInfo('icon', iconPath)
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
  })
    .then(({ value }) => {
      if (value === user.value.nickName) return
      updateUserInfo('nickName', value)
    })
    .catch(() => {})
}

function editIntroduce() {
  ElMessageBox.prompt('请输入个人介绍', '修改个人介绍', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: info.value.introduce || '',
    inputType: 'textarea',
    inputPlaceholder: '介绍一下自己吧'
  })
    .then(({ value }) => {
      if (value === info.value.introduce) return
      updateUserInfo('introduce', value)
    })
    .catch(() => {})
}

function editGender() {
  ElMessageBox.confirm('请选择您的性别', '选择性别', {
    confirmButtonText: '男',
    cancelButtonText: '女',
    distinguishCancelAndClose: true,
    customClass: 'gender-select-dialog'
  })
    .then(() => {
      if (info.value.gender !== 0 && info.value.gender !== '男') {
        updateUserInfo('gender', '男')
      }
    })
    .catch(action => {
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
  })
    .then(({ value }) => {
      if (value === info.value.city) return
      updateUserInfo('city', value)
    })
    .catch(() => {})
}

function editBirthday() {
  ElMessageBox.prompt('请输入生日 (格式: YYYY-MM-DD)', '修改生日', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: info.value.birthday || '',
    inputPattern: /^\d{4}-\d{2}-\d{2}$/,
    inputErrorMessage: '请按照YYYY-MM-DD格式输入日期，例如：1990-01-01'
  })
    .then(({ value }) => {
      if (value === info.value.birthday) return
      if (isNaN(new Date(value).getTime())) {
        ElMessage.error('请输入有效的日期')
        return
      }
      updateUserInfo('birthday', value)
    })
    .catch(() => {})
}
</script>

<style scoped>
.profile-edit-page__body {
  display: flex;
  flex-direction: column;
  gap: var(--wt-space-4);
  padding-block: var(--wt-space-4) var(--wt-space-6);
}
.profile-edit-page__group {
  overflow: hidden; /* 行 hover 背景不溢出圆角 */
}

@media (min-width: 768px) {
  .profile-edit-page__body {
    padding-top: var(--wt-space-6);
  }
}
</style>
