import { defineStore } from 'pinia'
import { login, logout, getUserInfo } from '@/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: null,
    user: null,
    permissions: []
  }),
  
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    hasPermission: (state) => (permission) => {
      if (state.user?.role === 'ADMIN') return true
      return state.permissions.includes(permission)
    }
  },
  
  actions: {
    async loginAction(username, password) {
      const res = await login({ username, password })
      this.token = res.data.token
      this.user = res.data.user
      this.permissions = res.data.permissions || []
      return res
    },
    
    async logoutAction() {
      try {
        await logout()
      } finally {
        this.token = null
        this.user = null
        this.permissions = []
      }
    },
    
    async fetchUserInfo() {
      const res = await getUserInfo()
      this.user = res.data.user
      this.permissions = res.data.permissions || []
      return res
    },
    
    setToken(token) {
      this.token = token
    }
  },
  
  persist: {
    key: 'property-auth',
    storage: localStorage,
    paths: ['token']
  }
})
