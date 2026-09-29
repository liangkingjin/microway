<template>
  <div class="page-container">
    <HeaderBar title="组团游" :show-back="true" />

    <div class="search-box" v-if="activeTab === 'search'">
      <el-input
        placeholder="寻找队伍"
        v-model="searchQuery"
        @input="handleSearch"
      >
        <template #prefix><el-icon style="color: #909399; margin: 0 5px;"><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- 内容区域 -->
    <div class="content-container">
      <!-- 查找队伍 -->
      <div v-if="activeTab === 'search'">
        <div v-if="filteredTeams.length > 0">
          <div class="team-card" v-for="team in filteredTeams" :key="team.id">
            <div class="team-header">
              <img :src="team.avatar || '/imgs/icons/default-icon.jpg'" class="team-avatar">
              <div class="team-info">
                <div class="team-name">{{ team.name }}</div>
                <div class="team-creator">{{ team.creatorName }}</div>
              </div>
            </div>
            <div class="team-content">
              <div class="team-destination">目的地：{{ team.destination }}</div>
              <div class="team-description">{{ team.description }}</div>
              <div class="team-meta">
                <span>人数：{{ team.currentMembers }}/{{ team.maxMembers }}</span>
                <span>出发时间：{{ team.departureDate }}</span>
              </div>
            </div>
            <div class="team-actions">
              <el-button size="small" type="primary" @click="joinTeam(team)">加入队伍</el-button>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          <el-icon style="font-size: 48px; margin-bottom: 10px; color: #909399;"><Search /></el-icon>
          <p>暂无符合条件的队伍</p>
        </div>
      </div>

      <!-- 我的队伍 -->
      <div v-if="activeTab === 'myTeams'">
        <div v-if="myTeams.length > 0">
          <div class="team-card" v-for="team in myTeams" :key="team.id">
            <div class="team-header">
              <img :src="team.avatar || '/imgs/icons/default-icon.jpg'" class="team-avatar">
              <div class="team-info">
                <div class="team-name">{{ team.name }}</div>
                <div class="team-creator">
                  <span v-if="team.isCreator">我创建的</span>
                  <span v-else>{{ team.creatorName }}</span>
                </div>
              </div>
            </div>
            <div class="team-content">
              <div class="team-destination">目的地：{{ team.destination }}</div>
              <div class="team-description">{{ team.description }}</div>
              <div class="team-meta">
                <span>人数：{{ team.currentMembers }}/{{ team.maxMembers }}</span>
                <span>出发时间：{{ team.departureDate }}</span>
              </div>
            </div>
            <div class="team-actions">
              <el-button size="small" @click="viewTeamMembers(team)">查看成员</el-button>
              <el-button v-if="team.isCreator" size="small" type="danger" @click="disbandTeam(team)">解散队伍</el-button>
              <el-button v-else size="small" type="danger" @click="quitTeam(team)">退出队伍</el-button>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          <el-icon style="font-size: 48px; margin-bottom: 10px; color: #909399;"><User /></el-icon>
          <p>您还没有加入任何队伍</p>
        </div>

        <!-- 创建队伍按钮 -->
        <div class="create-team-btn" @click="showCreateTeamDialog = true">
          <el-icon><Plus /></el-icon>
        </div>
      </div>
    </div>

    <!-- 底部选项卡 -->
    <div class="tab-container">
      <div class="tab-item" :class="{ active: activeTab === 'search' }" @click="activeTab = 'search'">
        <el-icon style="font-size: 20px; margin-bottom: 2px;"><Search /></el-icon>
        <span>查找队伍</span>
      </div>
      <div class="tab-item" :class="{ active: activeTab === 'myTeams' }" @click="activeTab = 'myTeams'">
        <el-icon style="font-size: 20px; margin-bottom: 2px;"><User /></el-icon>
        <span>我的队伍</span>
      </div>
    </div>

    <!-- 创建队伍对话框 -->
    <el-dialog title="创建队伍" v-model="showCreateTeamDialog" width="95%" class="mobile-dialog">
      <div class="create-team-form">
        <!-- 头像上传 -->
        <div class="upload-avatar">
          <input type="file" @change="fileSelected" ref="fileInputRef" style="display: none" accept="image/*">
          <div v-if="!newTeam.avatar" class="upload-avatar-btn" @click="openFileDialog">
            <el-icon style="font-size: 24px; color: #8c939d; margin-bottom: 5px;"><Camera /></el-icon>
            <div style="font-size: 12px; line-height: 12px">上传队伍头像</div>
          </div>
          <div v-else class="upload-avatar-preview">
            <img :src="newTeam.avatar" :alt="newTeam.name">
            <el-icon class="delete-icon" @click="deleteAvatar"><Close /></el-icon>
          </div>
        </div>

        <el-form :model="newTeam" :rules="createTeamRules" ref="createTeamFormRef" label-width="80px" class="mobile-form">
          <el-form-item label="队伍名称" prop="name">
            <el-input v-model="newTeam.name" placeholder="请输入队伍名称"></el-input>
          </el-form-item>
          <el-form-item label="目的地" prop="destination">
            <el-input v-model="newTeam.destination" placeholder="请输入目的地"></el-input>
          </el-form-item>
          <el-form-item label="队伍描述" prop="description">
            <el-input type="textarea" v-model="newTeam.description" placeholder="请输入队伍描述"></el-input>
          </el-form-item>
          <el-form-item label="最大人数" prop="maxMembers">
            <el-input-number v-model="newTeam.maxMembers" :min="2" :max="20"></el-input-number>
          </el-form-item>
          <el-form-item label="出发日期" prop="departureDate">
            <el-date-picker v-model="newTeam.departureDate" type="date" placeholder="选择日期" style="width: 100%;"></el-date-picker>
          </el-form-item>
          <el-form-item label="姓名" prop="leaderName">
            <el-input v-model="newTeam.leaderName" placeholder="请输入您的姓名"></el-input>
          </el-form-item>
          <el-form-item label="手机号码" prop="leaderPhone">
            <el-input v-model="newTeam.leaderPhone" placeholder="请输入手机号码"></el-input>
          </el-form-item>
          <el-form-item label="QQ号" prop="leaderQQ">
            <el-input v-model="newTeam.leaderQQ" placeholder="请输入QQ号"></el-input>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="showCreateTeamDialog = false">取消</el-button>
        <el-button type="primary" @click="createTeam">创建</el-button>
      </template>
    </el-dialog>

    <!-- 加入队伍对话框 -->
    <el-dialog
      :title="`加入队伍：${currentTeam ? currentTeam.name : ''}`"
      v-model="showJoinFormDialog"
      width="95%"
      class="mobile-dialog"
      center
      :close-on-click-modal="false"
      :show-close="false"
      append-to-body
    >
      <el-form ref="joinFormRef" :model="joinForm" :rules="joinFormRules" label-width="80px" class="mobile-form">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="joinForm.name" placeholder="请输入您的姓名"></el-input>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="joinForm.phone" placeholder="请输入您的手机号"></el-input>
        </el-form-item>
        <el-form-item label="QQ号" prop="qq">
          <el-input v-model="joinForm.qq" placeholder="请输入您的QQ号"></el-input>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input type="textarea" v-model="joinForm.remark" placeholder="可选：添加备注信息"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showJoinFormDialog = false">取消</el-button>
        <el-button type="primary" @click="submitJoinForm" round>立即加入</el-button>
      </template>
    </el-dialog>

    <!-- 队伍成员对话框 -->
    <el-dialog :title="`${currentTeam ? currentTeam.name : ''} - 队伍成员`" v-model="showMembersDialog" width="95%" append-to-body>
      <div v-if="currentTeamMembers && currentTeamMembers.length > 0">
        <div v-for="member in currentTeamMembers" :key="member.id" class="member-item" @click="viewMemberDetail(member)">
          <img :src="member.avatar || '/imgs/icons/default-icon.jpg'" :alt="member.name" class="member-avatar">
          <div class="member-info">
            <div class="member-name">
              {{ member.name }}
              <el-tag size="small" type="success" v-if="member.isLeader">队长</el-tag>
            </div>
            <div class="member-join-time">加入时间：{{ member.joinTime }}</div>
          </div>
          <el-icon style="color: #909399;"><ArrowRight /></el-icon>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-icon style="font-size: 48px; margin-bottom: 10px; color: #909399;"><User /></el-icon>
        <p>暂无成员</p>
      </div>
    </el-dialog>

    <!-- 成员详情对话框 -->
    <el-dialog title="成员详情" v-model="showMemberDetailDialog" width="95%" append-to-body>
      <div class="member-detail">
        <img :src="currentMember.avatar || '/imgs/icons/default-icon.jpg'" :alt="currentMember.name" class="member-detail-avatar">
        <div class="member-detail-name">{{ currentMember.name }}</div>
        <div class="member-detail-role">
          <el-tag type="success" v-if="currentMember.isLeader">队长</el-tag>
          <el-tag v-else>队员</el-tag>
        </div>

        <div class="member-detail-item">
          <div class="member-detail-label">手机号码</div>
          <div class="member-detail-value">
            {{ currentMember.phone }}
            <el-button type="text" @click="callPhone(currentMember.phone)">
              <el-icon style="margin-right: 4px;"><Phone /></el-icon>拨打
            </el-button>
          </div>
        </div>

        <div class="member-detail-item">
          <div class="member-detail-label">QQ号码</div>
          <div class="member-detail-value">
            {{ currentMember.qq }}
            <el-button type="text" @click="copyQQ(currentMember.qq)">
              <el-icon style="margin-right: 4px;"><DocumentCopy /></el-icon>复制
            </el-button>
          </div>
        </div>

        <div class="member-detail-item">
          <div class="member-detail-label">加入时间</div>
          <div class="member-detail-value">{{ currentMember.joinTime }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'

const activeTab = ref('search')
const searchQuery = ref('')
const showCreateTeamDialog = ref(false)

const newTeam = ref({
  name: '',
  destination: '',
  description: '',
  maxMembers: 5,
  departureDate: '',
  leaderName: '',
  leaderPhone: '',
  leaderQQ: '',
  avatar: ''
})

const showMembersDialog = ref(false)
const showMemberDetailDialog = ref(false)
const showJoinFormDialog = ref(false)
const currentTeam = ref(null)
const currentTeamMembers = ref([])
const currentMember = ref({})

const joinForm = ref({
  name: '',
  phone: '',
  qq: '',
  remark: ''
})

const joinFormRules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 10, message: '长度在2到10个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ],
  qq: [
    { required: true, message: '请输入QQ号', trigger: 'blur' },
    { pattern: /^\d{5,12}$/, message: '请输入正确的QQ号码', trigger: 'blur' }
  ]
}

const allTeams = ref([])
const myTeams = ref([])

const createTeamRules = {
  name: [
    { required: true, message: '请输入队伍名称', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在2到20个字符', trigger: 'blur' }
  ],
  destination: [
    { required: true, message: '请输入目的地', trigger: 'blur' }
  ],
  departureDate: [
    { required: true, message: '请选择出发日期', trigger: 'change' }
  ],
  leaderName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 10, message: '长度在2到10个字符', trigger: 'blur' }
  ],
  leaderPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ],
  leaderQQ: [
    { required: true, message: '请输入QQ号', trigger: 'blur' },
    { pattern: /^\d{5,12}$/, message: '请输入正确的QQ号码', trigger: 'blur' }
  ],
  avatar: [
    { required: true, message: '请上传队伍头像', trigger: 'change' }
  ]
}

const createTeamFormRef = ref(null)
const joinFormRef = ref(null)
const fileInputRef = ref(null)

const filteredTeams = computed(() => {
  if (!searchQuery.value) {
    return allTeams.value
  }
  const query = searchQuery.value.toLowerCase()
  return allTeams.value.filter(team =>
    team.name.toLowerCase().includes(query) ||
    team.destination.toLowerCase().includes(query) ||
    team.description.toLowerCase().includes(query)
  )
})

function handleSearch() {
  // 搜索功能已通过计算属性实现
}

function loadAllTeams() {
  request.get('/teams/list')
    .then(res => {
      allTeams.value = res.data || []
    })
    .catch(err => {
      console.error('获取队伍列表失败', err)
      ElMessage.error('获取队伍列表失败')
    })
}

function loadMyTeams() {
  request.get('/teams/of/user')
    .then(res => {
      myTeams.value = res.data || []
    })
    .catch(err => {
      console.error('获取我的队伍失败', err)
      ElMessage.error('获取我的队伍失败')
    })
}

function joinTeam(team) {
  if (team.currentMembers >= team.maxMembers) {
    ElMessage.error('该队伍已满，无法加入')
    return
  }
  currentTeam.value = team
  showJoinFormDialog.value = true
}

function quitTeam(team) {
  ElMessageBox.confirm('确定要退出该队伍吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    customClass: 'mobile-dialog',
    center: true,
    closeOnClickModal: false,
    showClose: false,
    appendToBody: true
  }).then(() => {
    request.post(`/team-members/quit/${team.id}`)
      .then(() => {
        const index = myTeams.value.findIndex(t => t.id === team.id)
        if (index !== -1) {
          myTeams.value.splice(index, 1)
        }
        ElMessage.success('已退出队伍')
      })
      .catch(err => {
        console.error('退出队伍失败', err)
        ElMessage.error('退出队伍失败')
      })
  }).catch(() => {})
}

function disbandTeam(team) {
  ElMessageBox.confirm('确定要解散该队伍吗? 此操作不可恢复!', '警告', {
    confirmButtonText: '确定解散',
    cancelButtonText: '取消',
    type: 'warning',
    customClass: 'mobile-dialog',
    center: true,
    closeOnClickModal: false,
    showClose: false,
    appendToBody: true,
    confirmButtonClass: 'el-button--danger'
  }).then(() => {
    if (!team) return
    request.post(`/teams/disband/${team.id}`)
      .then(() => {
        const index = myTeams.value.findIndex(t => t.id === team.id)
        if (index !== -1) {
          myTeams.value.splice(index, 1)
        }
        const allIndex = allTeams.value.findIndex(t => t.id === team.id)
        if (allIndex !== -1) {
          allTeams.value.splice(allIndex, 1)
        }
        ElMessage.success('队伍已解散')
      })
      .catch(err => {
        console.error('解散队伍失败', err)
        ElMessage.error('解散队伍失败')
      })
  }).catch(() => {})
}

async function createTeam() {
  try {
    await createTeamFormRef.value.validate()
  } catch (e) {
    return
  }

  const teamData = {
    name: newTeam.value.name,
    destination: newTeam.value.destination,
    description: newTeam.value.description,
    maxMembers: newTeam.value.maxMembers,
    departureDate: formatDate(newTeam.value.departureDate),
    creatorName: newTeam.value.leaderName,
    creatorPhone: newTeam.value.leaderPhone,
    creatorQq: newTeam.value.leaderQQ,
    avatar: newTeam.value.avatar
  }

  request.post('/teams/create', teamData)
    .then(() => {
      loadMyTeams()
      loadAllTeams()
      ElMessage.success('创建队伍成功！')
      showCreateTeamDialog.value = false
      createTeamFormRef.value.resetFields()
      newTeam.value.avatar = ''
    })
    .catch(err => {
      console.error('创建队伍失败', err)
      ElMessage.error('创建队伍失败：' + (err.response?.data || err.message))
    })
}

function formatDate(date) {
  if (!date) return ''
  if (typeof date === 'string') return date
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function viewTeamMembers(team) {
  currentTeam.value = team
  request.get(`/team-members/${team.id}`)
    .then(res => {
      currentTeamMembers.value = res.data || []
      showMembersDialog.value = true
    })
    .catch(err => {
      console.error('获取队伍成员失败', err)
      ElMessage.error('获取队伍成员失败')
    })
}

function viewMemberDetail(member) {
  currentMember.value = member
  showMemberDetailDialog.value = true
}

function callPhone(phone) {
  const a = document.createElement('a')
  a.href = `tel:${phone}`
  a.click()
}

function copyQQ(qq) {
  const textarea = document.createElement('textarea')
  textarea.value = qq
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    const successful = document.execCommand('copy')
    if (successful) {
      ElMessage.success('已复制QQ号')
    } else {
      ElMessage.error('复制失败，请手动复制')
    }
  } catch (err) {
    ElMessage.error('复制失败，请手动复制')
    console.error('复制失败:', err)
  }
  document.body.removeChild(textarea)
}

async function submitJoinForm() {
  try {
    await joinFormRef.value.validate()
  } catch (e) {
    return
  }

  const joinData = {
    teamId: currentTeam.value.id,
    name: joinForm.value.name,
    phone: joinForm.value.phone,
    qq: joinForm.value.qq,
    remark: joinForm.value.remark
  }

  request.post('/team-members/join', joinData)
    .then(() => {
      loadMyTeams()
      loadAllTeams()
      ElMessage.success('已成功加入队伍！')
      showJoinFormDialog.value = false
      joinFormRef.value.resetFields()
    })
    .catch(err => {
      console.error('加入队伍失败', err)
      ElMessage.error('加入队伍失败：' + (err?.message || err))
    })
}

function openFileDialog() {
  fileInputRef.value.click()
}

function fileSelected() {
  const file = fileInputRef.value.files[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  const config = {
    headers: {
      'Content-Type': 'multipart/form-data;boundary=' + new Date().getTime()
    }
  }

  request.post('/upload/blog', formData, config)
    .then(res => {
      newTeam.value.avatar = '/imgs' + res.data
    })
    .catch(err => {
      ElMessage.error('上传失败：' + err.message)
    })
}

function deleteAvatar() {
  if (!newTeam.value.avatar) return
  request.get('/upload/blog/delete?name=' + newTeam.value.avatar)
    .then(() => {
      newTeam.value.avatar = ''
    })
    .catch(err => {
      ElMessage.error('删除失败：' + err.message)
    })
}

onMounted(() => {
  loadAllTeams()
  loadMyTeams()
})
</script>

<style scoped>
.page-container {
  background: #f8f8f8;
  min-height: 100vh;
}
.search-box {
  padding: 10px 15px;
  background: #fff;
}
.tab-container {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  background: #fff;
  border-top: 1px solid #eee;
  height: 50px;
  z-index: 100;
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #909399;
  font-size: 12px;
}
.tab-item.active {
  color: #409EFF;
}
.content-container {
  padding: 10px 15px;
  margin-bottom: 60px;
}
.team-card {
  background: #fff;
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 15px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
.team-header {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}
.team-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 10px;
  object-fit: cover;
}
.team-info {
  flex: 1;
}
.team-name {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 4px;
}
.team-creator {
  font-size: 12px;
  color: #909399;
}
.team-content {
  margin-bottom: 10px;
}
.team-destination {
  font-size: 14px;
  margin-bottom: 5px;
}
.team-description {
  font-size: 14px;
  color: #606266;
  margin-bottom: 10px;
}
.team-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}
.team-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.create-team-btn {
  position: fixed;
  right: 20px;
  bottom: 70px;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: #409EFF;
  color: #fff;
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.2);
  z-index: 99;
}
.create-team-form {
  padding: 0 15px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: #909399;
}
.member-item {
  display: flex;
  align-items: center;
  padding: 15px;
  border-bottom: 1px solid #eee;
}
.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 10px;
  object-fit: cover;
}
.member-info {
  flex: 1;
}
.member-name {
  font-size: 14px;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
}
.member-join-time {
  font-size: 12px;
  color: #909399;
}
.member-detail {
  text-align: center;
  padding: 20px 0;
}
.member-detail-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  margin-bottom: 10px;
}
.member-detail-name {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 10px;
}
.member-detail-role {
  margin-bottom: 20px;
}
.member-detail-item {
  display: flex;
  padding: 10px 20px;
  border-bottom: 1px solid #eee;
}
.member-detail-label {
  width: 80px;
  color: #909399;
}
.member-detail-value {
  flex: 1;
  text-align: right;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.member-detail-value :deep(.el-button) {
  margin-left: 10px;
}
.upload-avatar {
  width: 100%;
  padding: 15px;
  background: #fff;
  margin-bottom: 10px;
  text-align: center;
}
.upload-avatar-btn {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  border: 1px dashed #d9d9d9;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  margin: 0 auto;
  position: relative;
}
.upload-avatar-preview {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  position: relative;
  margin: 0 auto;
}
.upload-avatar-preview img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
.upload-avatar-preview .delete-icon {
  position: absolute;
  top: -8px;
  right: -8px;
  background: rgba(0,0,0,0.5);
  color: #fff;
  border-radius: 50%;
  padding: 4px;
  font-size: 12px;
  cursor: pointer;
}
.mobile-form :deep(.el-form-item) {
  margin-bottom: 15px;
}
.mobile-form :deep(.el-input-number) {
  width: 100%;
}
:deep(.el-message-box) {
  width: 90% !important;
  max-width: 300px !important;
  border-radius: 8px !important;
}
</style>
