<template>
  <div class="page-container">
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-value">{{ dashboardData.totalOwners || 0 }}</div>
        <div class="stat-label">总业主数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dashboardData.occupiedOwners || 0 }}</div>
        <div class="stat-label">已入住</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dashboardData.totalParkings || 0 }}</div>
        <div class="stat-label">总车位数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dashboardData.usedParkings || 0 }}</div>
        <div class="stat-label">已使用车位</div>
      </div>
    </div>
    
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-value" style="color: var(--success-color)">
          {{ formatMoney(dashboardData.paidAmount) }}
        </div>
        <div class="stat-label">已收金额(元)</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color: var(--warning-color)">
          {{ formatMoney(dashboardData.unpaidAmount) }}
        </div>
        <div class="stat-label">待收金额(元)</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color: var(--danger-color)">
          {{ dashboardData.overdueCount || 0 }}
        </div>
        <div class="stat-label">欠费账单数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dashboardData.collectionRate || 0 }}%</div>
        <div class="stat-label">收缴率</div>
      </div>
    </div>
    
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-value" style="color: var(--danger-color)">{{ dashboardData.arrearsRate || 0 }}%</div>
        <div class="stat-label">欠费率</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dashboardData.avgOverdueDays || 0 }}天</div>
        <div class="stat-label">平均逾期天数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value" style="color: var(--danger-color)">{{ dashboardData.maxOverdueDays || 0 }}天</div>
        <div class="stat-label">最大逾期天数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ formatMoney(dashboardData.totalAmount) }}</div>
        <div class="stat-label">应收总额(元)</div>
      </div>
    </div>
    
    <div class="card">
      <div class="card-header">
        <span class="title">收费概览</span>
      </div>
      <div class="chart-container">
        <v-chart :option="chartOption" autoresize style="height: 300px;" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getDashboard } from '@/api/statistics'
import { formatMoney } from '@/utils'
import { useThemeStore } from '@/store/theme'

use([CanvasRenderer, PieChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const themeStore = useThemeStore()
const dashboardData = ref({})

const chartOption = computed(() => {
  const textColor = themeStore.isDark ? '#e5eaf3' : '#303133'
  
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: themeStore.isDark ? '#1d1d1d' : '#fff',
      borderColor: themeStore.isDark ? '#4c4d4f' : '#dcdfe6',
      textStyle: {
        color: textColor
      }
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      textStyle: {
        color: textColor
      }
    },
    series: [
      {
        name: '收费情况',
        type: 'pie',
        radius: '50%',
        data: [
          { value: dashboardData.value.paidAmount || 0, name: '已收金额', itemStyle: { color: '#67c23a' } },
          { value: dashboardData.value.unpaidAmount || 0, name: '待收金额', itemStyle: { color: '#e6a23c' } }
        ],
        label: {
          color: textColor
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
})

onMounted(async () => {
  try {
    const res = await getDashboard()
    dashboardData.value = res.data
  } catch (error) {
    console.error('加载仪表盘数据失败', error)
  }
})
</script>

<style lang="scss" scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.chart-container {
  padding: 20px 0;
}

@media (max-width: 1200px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }
}
</style>
