<template>
  <div class="login-container">
    <HeaderBar title="密码登录" :show-back="true" />

    <div class="content">
      <div class="login-form">
        <el-input placeholder="请输入手机号" v-model="form.phone"></el-input>
        <div style="height: 5px"></div>
        <el-input placeholder="请输入密码" v-model="form.password" show-password></el-input>
        <div style="text-align: center; color: #8c939d; margin: 5px 0"><a href="javascript:void(0)">忘记密码</a></div>
        <el-button @click="login" style="width: 100%; background-color: #f63; color: #fff;">登录</el-button>
        <div style="text-align: right; color: #333333; margin: 5px 0"><router-link to="/login" style="color: inherit; text-decoration: none;">验证码登录</router-link></div>
      </div>
      <div class="login-radio">
        <div>
          <input type="radio" name="readed" v-model="radio" value="1">
          <label for="readed"></label>
        </div>
        <div>我已阅读并同意
          <a href="javascript:void(0)">《微途旅行用户服务协议》</a>、
          <a href="javascript:void(0)">《隐私政策》</a>
          等，接受免除或者限制责任、诉讼管辖约定等粗体标示条款
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import { login as apiLogin } from '../api/user'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const radio = ref('')
const form = ref({})

function login() {
  if (!radio.value) {
    ElMessage.error('请先确认阅读用户协议！')
    return
  }
  apiLogin(form.value)
    .then(res => {
      const data = res.data
      if (data) {
        auth.setToken(data)
      }
      router.push('/info')
    })
    .catch(err => ElMessage.error(err))
}
</script>

<style scoped>
.content {
  padding: 20px 15px;
}
.login-form {
  margin-bottom: 20px;
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
:deep(.el-input__inner:focus) {
  border: 1px solid #F63;
}
</style>
