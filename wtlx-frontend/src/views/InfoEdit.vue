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
/* 样式合并自原 info-edit.html <style> 内联块；body/#app 全局重置不迁移 */
.info-edit-page {
  background: #f5f7fa;
  min-height: 100vh;
}
.edit-container {
  padding: 70px 16px 20px;
}
.info-box {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
}
.info-item:hover {
  background-color: #f5f7fa;
}
.info-label {
  font-size: 16px;
  color: #303133;
  font-weight: 500;
}
.info-btn {
  display: flex;
  align-items: center;
  color: #909399;
}
.info-btn img {
  width: 35px;
  height: 35px;
  border-radius: 50%;
  object-fit: cover;
  margin-right: 8px;
}
.info-btn .arrow {
  margin-left: 8px;
  color: #c0c4cc;
  font-size: 14px;
}
.divider {
  height: 1px;
  background-color: #ebeef5;
  margin: 0 16px;
}
:deep(.el-message-box) {
  width: 85% !important;
  max-width: 320px !important;
}
:deep(.el-textarea__inner) {
  min-height: 80px !important;
}
</style>
