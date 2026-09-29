<template>
  <div class="login-container">
    <HeaderBar title="手机号码快捷登录" :show-back="true" />

    <div class="content">
      <div class="logo-welcome">
        <img src="/imgs/LOGO1.png" alt="微途旅行" class="login-logo">
        <div class="welcome-subtitle">登录后开启您的旅行之旅</div>
      </div>
      <div class="login-form">
        <div style="display: flex; justify-content: space-between; margin-bottom: 10px;">
          <el-input style="width: 60%" placeholder="请输入手机号" v-model="form.phone">
            <template #prefix><el-icon style="color: #909399; margin: 0 5px;"><Iphone /></el-icon></template>
          </el-input>
          <el-button style="width: 38%" @click="sendCode" type="success" :disabled="disabled">{{ codeBtnMsg }}</el-button>
        </div>

        <el-input placeholder="请输入验证码" v-model="form.code">
          <template #prefix><el-icon style="color: #909399; margin: 0 5px;"><Message /></el-icon></template>
        </el-input>
        <div class="form-tip">未注册的手机号码验证后自动创建账户</div>
        <el-button @click="login" class="login-btn" style="width: 100%; background-color: rgb(51, 184, 255); color: #fff;">登录</el-button>
      </div>
      <div class="login-radio">
        <input type="radio" id="agreement" name="readed" v-model="radio" value="1">
        <div>我已阅读并同意
          <router-link to="/user-agreement">《微途旅行用户服务协议》</router-link>和
          <router-link to="/privacy-policy">《隐私政策》</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import { login as apiLogin, sendCode as apiSendCode } from '../api/user'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const radio = ref('')
const disabled = ref(false)
const codeBtnMsg = ref('发送验证码')
const form = ref({})

function login() {
  if (!radio.value) {
    ElMessage.error('请先确认阅读用户协议！')
    return
  }
  if (!form.value.phone || !form.value.code) {
    ElMessage.error('手机号和验证码不能为空！')
    return
  }
  apiLogin(form.value)
    .then(res => {
      const data = res.data
      if (data) {
        auth.setToken(data)
        console.log('登录成功，token已保存')
        ElMessage.success('登录成功')
      } else {
        ElMessage.error('登录失败，服务器未返回token')
        return
      }
      setTimeout(() => {
        router.push('/index')
      }, 1000)
    })
    .catch(err => {
      console.error('登录失败:', err)
      ElMessage.error('登录失败: ' + (err.response?.data?.message || err.message || '未知错误'))
    })
}

function sendCode() {
  if (!form.value.phone) {
    ElMessage.error('手机号不能为空')
    return
  }
  apiSendCode(form.value.phone)
    .then(Response => {
      // 响应体 data 为验证码字符串，自动填充输入框（与原页面行为一致）
      form.value.code = Response.data
      ElMessage({
        message: '验证码已自动填充',
        type: 'success',
        duration: 3000
      })
      ElMessageBox.alert(
        `<div style="text-align:center;">
          <div style="font-size:14px;margin-bottom:5px;">您的验证码是</div>
          <div style="font-size:20px;font-weight:bold;color:#409EFF;margin:8px 0;">${Response.data}</div>
          <div style="font-size:12px;color:#909399;margin-top:5px;">验证码有效期为5分钟</div>
        </div>`,
        '验证码',
        {
          dangerouslyUseHTMLString: true,
          confirmButtonText: '确定',
          customClass: 'verification-code-dialog'
        }
      )
    })
    .catch(err => {
      console.log(err)
      ElMessage.error(err)
    })
  disabled.value = true
  let i = 60
  codeBtnMsg.value = (i--) + '秒后可重发'
  const taskId = setInterval(() => { codeBtnMsg.value = (i--) + '秒后可重发' }, 1000)
  setTimeout(() => {
    disabled.value = false
    clearInterval(taskId)
    codeBtnMsg.value = '发送验证码'
  }, 59000)
}
</script>

<style scoped>
.login-container {
  padding-bottom: 20px;
}
.content {
  padding: 20px 15px;
}
.logo-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
  background: linear-gradient(to bottom, rgba(240, 249, 255, 0.8), rgba(255, 255, 255, 0));
}
.login-logo {
  width: 120px;
  height: auto;
  margin-bottom: 15px;
}
.welcome-subtitle {
  font-size: 14px;
  color: #8c939d;
}
.login-form {
  margin-bottom: 20px;
}
:deep(.el-input__inner) {
  border-radius: 30px;
  height: 40px;
}
:deep(.el-button) {
  border-radius: 4px;
  height: 40px;
}
.login-btn {
  margin-top: 15px;
  background-color: rgb(51, 184, 255);
  border-color: rgb(51, 184, 255);
  font-size: 16px;
  font-weight: 500;
}
.form-tip {
  text-align: center;
  color: #8c939d;
  font-size: 13px;
  margin: 10px 0;
}
.login-radio {
  display: flex;
  align-items: flex-start;
  margin-top: 15px;
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}
.login-radio input[type="radio"] {
  margin-right: 8px;
  margin-top: 3px;
}
.login-radio a {
  color: rgb(51, 184, 255);
  text-decoration: none;
}
</style>
