<template>
  <el-container class="main-layout">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo-container">
        <img v-if="configStore.logoUrl" :src="configStore.logoUrl" alt="Logo" class="logo" />
        <span v-if="!isCollapse" class="logo-text">{{ configStore.companyName }}</span>
      </div>
      
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        
        <el-sub-menu index="sub-1">
          <template #title>
            <el-icon><User /></el-icon>
            <span>业主管理</span>
          </template>
          <el-menu-item index="/owners">业主列表</el-menu-item>
          <el-menu-item index="/parkings">车位管理</el-menu-item>
        </el-sub-menu>
        
        <el-sub-menu index="sub-2">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>账单管理</span>
          </template>
          <el-menu-item index="/fee-standards">收费标准</el-menu-item>
          <el-menu-item index="/bill-schedules" v-if="authStore.isAdmin">账单调度</el-menu-item>
          <el-menu-item index="/bills">账单列表</el-menu-item>
          <el-menu-item index="/payments">缴费管理</el-menu-item>
          <el-menu-item index="/receivables">应收账款</el-menu-item>
        </el-sub-menu>
        
        <el-menu-item index="/statistics">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>统计分析</template>
        </el-menu-item>
        
        <el-sub-menu index="sub-3">
          <template #title>
            <el-icon><OfficeBuilding /></el-icon>
            <span>小区布局</span>
          </template>
          <el-menu-item index="/layout/buildings">楼栋布局</el-menu-item>
          <el-menu-item index="/layout/parkings">车位布局</el-menu-item>
        </el-sub-menu>
        
        <el-menu-item index="/templates">
          <el-icon><Files /></el-icon>
          <template #title>模板管理</template>
        </el-menu-item>
        
        <el-sub-menu v-if="authStore.isAdmin" index="sub-4">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/users">用户管理</el-menu-item>
          <el-menu-item index="/account-sets">账套管理</el-menu-item>
          <el-menu-item index="/config">系统配置</el-menu-item>
          <el-menu-item index="/logs">操作日志</el-menu-item>
          <el-menu-item index="/backup">备份恢复</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <div class="header-right">
          <el-select
            v-model="currentAccountSetId"
            placeholder="选择账套"
            size="small"
            style="width: 150px; margin-right: 16px;"
            @change="handleAccountSetChange"
          >
            <el-option
              v-for="item in accountSetStore.accountSetList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          
          <el-switch
            v-model="themeStore.isDark"
            inline-prompt
            active-text="🌙"
            inactive-text="☀️"
          />
          
          <el-dropdown trigger="click" class="user-dropdown">
            <span class="user-info">
              <el-avatar :size="32" icon="UserFilled" />
              <span class="username">{{ authStore.user?.realName || authStore.user?.username }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      
      <el-main class="main-content">
        <router-view :key="viewRefreshKey" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Fold, Expand, SwitchButton,
  Odometer, User, Document, DataAnalysis, 
  OfficeBuilding, Files, Setting 
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { useConfigStore } from '@/store/config'
import { useAccountSetStore } from '@/store/accountSet'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const configStore = useConfigStore()
const accountSetStore = useAccountSetStore()

const isCollapse = ref(false)
const currentAccountSetId = ref(null)

const activeMenu = computed(() => route.path)
const viewRefreshKey = computed(() => `${route.fullPath}-${themeStore.isDark ? 'dark' : 'light'}`)

onMounted(async () => {
  try {
    await configStore.fetchConfig()
    await accountSetStore.fetchAccountSetList()
    currentAccountSetId.value = accountSetStore.currentAccountSetId
  } catch (error) {
    console.error('加载配置失败', error)
  }
})

const handleAccountSetChange = async (id) => {
  const accountSet = accountSetStore.accountSetList.find(item => item.id === id)
  if (accountSet) {
    await accountSetStore.switchAccountSetAction(accountSet)
    ElMessage.success('账套切换成功')
    location.reload()
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await authStore.logoutAction()
    router.push('/login')
    ElMessage.success('已退出登录')
  } catch (error) {
    // 取消操作
  }
}
</script>

<style lang="scss" scoped>
.main-layout {
  height: 100vh;
}

.sidebar {
  background-color: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  transition: width 0.3s;
  overflow-x: hidden;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  
  .logo-container {
    height: 60px;
    min-height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 16px;
    border-bottom: 1px solid var(--border-color);
    
    .logo {
      width: 32px;
      height: 32px;
    }
    
    .logo-text {
      margin-left: 12px;
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary);
      white-space: nowrap;
    }
  }
  
  .sidebar-menu {
    border-right: none;
    background-color: transparent;
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
  }
}

.header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background-color: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  box-shadow: var(--shadow);
  
  .header-left {
    display: flex;
    align-items: center;
    
    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      margin-right: 16px;
      color: var(--text-regular);
      
      &:hover {
        color: var(--primary-color);
      }
    }
  }
  
  .header-right {
    display: flex;
    align-items: center;
    
    .user-dropdown {
      margin-left: 16px;
      cursor: pointer;
      
      .user-info {
        display: flex;
        align-items: center;
        
        .username {
          margin-left: 8px;
          color: var(--text-primary);
        }
      }
    }
  }
}

.main-content {
  background-color: var(--bg-color);
  padding: 20px;
  overflow-y: auto;
}
</style>
