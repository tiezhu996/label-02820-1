import { defineStore } from 'pinia'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    isDark: false
  }),
  
  actions: {
    toggleTheme() {
      this.isDark = !this.isDark
    },
    
    setTheme(isDark) {
      this.isDark = isDark
    }
  },
  
  persist: {
    key: 'property-theme',
    storage: localStorage
  }
})
