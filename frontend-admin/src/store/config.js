import { defineStore } from 'pinia'
import { getConfig } from '@/api/config'

export const useConfigStore = defineStore('config', {
  state: () => ({
    companyName: '物业管理系统',
    logoUrl: null
  }),
  
  actions: {
    async fetchConfig() {
      const res = await getConfig()
      if (res.data) {
        this.companyName = res.data.companyName || '物业管理系统'
        this.logoUrl = res.data.logoUrl
      }
      return res
    },
    
    setCompanyName(name) {
      this.companyName = name
    },
    
    setLogoUrl(url) {
      this.logoUrl = url
    }
  },
  
  persist: {
    key: 'property-config',
    storage: localStorage
  }
})
