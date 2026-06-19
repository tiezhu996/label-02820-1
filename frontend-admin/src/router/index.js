import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'owners',
        name: 'Owners',
        component: () => import('@/views/owner/OwnerListView.vue'),
        meta: { title: '业主管理', icon: 'User' }
      },
      {
        path: 'parkings',
        name: 'Parkings',
        component: () => import('@/views/parking/ParkingListView.vue'),
        meta: { title: '车位管理', icon: 'Van' }
      },
      {
        path: 'fee-standards',
        name: 'FeeStandards',
        component: () => import('@/views/fee/FeeStandardView.vue'),
        meta: { title: '收费标准', icon: 'PriceTag' }
      },
      {
        path: 'bills',
        name: 'Bills',
        component: () => import('@/views/bill/BillListView.vue'),
        meta: { title: '账单管理', icon: 'Document' }
      },
      {
        path: 'bill-schedules',
        name: 'BillSchedules',
        component: () => import('@/views/bill/BillScheduleView.vue'),
        meta: { title: '账单调度', icon: 'Timer', adminOnly: true }
      },
      {
        path: 'payments',
        name: 'Payments',
        component: () => import('@/views/payment/PaymentListView.vue'),
        meta: { title: '缴费管理', icon: 'Wallet' }
      },
      {
        path: 'receivables',
        name: 'Receivables',
        component: () => import('@/views/receivable/ReceivableView.vue'),
        meta: { title: '应收账款', icon: 'Money' }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/StatisticsView.vue'),
        meta: { title: '统计分析', icon: 'DataAnalysis' }
      },
      {
        path: 'layout/buildings',
        name: 'LayoutBuildings',
        component: () => import('@/views/layout/LayoutBuildingView.vue'),
        meta: { title: '楼栋布局', icon: 'OfficeBuilding' }
      },
      {
        path: 'layout/parkings',
        name: 'LayoutParkings',
        component: () => import('@/views/layout/LayoutParkingView.vue'),
        meta: { title: '车位布局', icon: 'Place' }
      },
      {
        path: 'templates',
        name: 'Templates',
        component: () => import('@/views/template/TemplateView.vue'),
        meta: { title: '模板管理', icon: 'Files' }
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/system/LogView.vue'),
        meta: { title: '操作日志', icon: 'Notebook' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/system/UserListView.vue'),
        meta: { title: '用户管理', icon: 'UserFilled', adminOnly: true }
      },
      {
        path: 'account-sets',
        name: 'AccountSets',
        component: () => import('@/views/system/AccountSetView.vue'),
        meta: { title: '账套管理', icon: 'FolderOpened', adminOnly: true }
      },
      {
        path: 'config',
        name: 'Config',
        component: () => import('@/views/system/ConfigView.vue'),
        meta: { title: '系统配置', icon: 'Setting', adminOnly: true }
      },
      {
        path: 'backup',
        name: 'Backup',
        component: () => import('@/views/system/BackupView.vue'),
        meta: { title: '备份恢复', icon: 'Upload', adminOnly: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  
  // 未登录检查
  if (to.meta.requiresAuth !== false && !authStore.token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }
  
  // 已登录访问登录页
  if (to.name === 'Login' && authStore.token) {
    next({ name: 'Dashboard' })
    return
  }
  
  // 有token但没有用户信息时，获取用户信息
  if (authStore.token && !authStore.user) {
    try {
      await authStore.fetchUserInfo()
    } catch (e) {
      // 获取失败，清除token并跳转登录
      authStore.token = null
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }
  }
  
  // 管理员专属页面检查
  if (to.meta.adminOnly && !authStore.isAdmin) {
    next({ name: 'Dashboard' })
    return
  }
  
  next()
})

export default router
