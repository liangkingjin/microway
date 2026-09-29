import { defineStore } from 'pinia'

// 登录态：token 存 sessionStorage（与原页面行为一致）
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: sessionStorage.getItem('token') || ''
  }),
  getters: {
    isLoggedIn: state => !!state.token
  },
  actions: {
    setToken(token) {
      this.token = token
      sessionStorage.setItem('token', token)
    },
    clearToken() {
      this.token = ''
      sessionStorage.removeItem('token')
    }
  }
})
