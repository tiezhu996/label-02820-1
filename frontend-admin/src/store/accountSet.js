import { defineStore } from 'pinia'
import { getAccountSetList, switchAccountSet } from '@/api/accountSet'

export const useAccountSetStore = defineStore('accountSet', {
  state: () => ({
    currentAccountSet: null,
    accountSetList: []
  }),
  
  getters: {
    currentAccountSetId: (state) => state.currentAccountSet?.id,
    currentAccountSetName: (state) => state.currentAccountSet?.name || '默认账套'
  },
  
  actions: {
    async fetchAccountSetList() {
      const res = await getAccountSetList()
      this.accountSetList = res.data || []
      if (!this.currentAccountSet && this.accountSetList.length > 0) {
        this.currentAccountSet = this.accountSetList[0]
      }
      return res
    },
    
    async switchAccountSetAction(accountSet) {
      await switchAccountSet(accountSet.id)
      this.currentAccountSet = accountSet
    },
    
    setCurrentAccountSet(accountSet) {
      this.currentAccountSet = accountSet
    }
  },
  
  persist: {
    key: 'property-account-set',
    storage: localStorage,
    paths: ['currentAccountSet']
  }
})
