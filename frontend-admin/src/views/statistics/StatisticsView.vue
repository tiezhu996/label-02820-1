<template>
  <div class="page-container">
    <div class="search-area">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="费用类型">
          <el-select v-model="searchForm.feeType" style="width: 150px;">
            <el-option label="物业费" value="PROPERTY" />
            <el-option label="车位费" value="PARKING" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker v-model="searchForm.dateRange" type="daterange" start-placeholder="开始" end-placeholder="结束" style="width: 260px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPaymentDetail">缴费明细</el-button>
          <el-button type="warning" @click="loadArrearsDetail">欠费明细</el-button>
          <el-button type="info" @click="loadSummary">汇总统计</el-button>
          <el-button type="success" @click="loadBuildingCollection">楼栋缴费率</el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <div v-if="showSummary" class="summary-area">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">缴费笔数</div>
              <div class="stat-value">{{ summaryData.paidCount || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">缴费金额</div>
              <div class="stat-value success">￥{{ summaryData.totalPaidAmount || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">欠费笔数</div>
              <div class="stat-value">{{ summaryData.arrearsCount || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="stat-item">
              <div class="stat-label">欠费金额</div>
              <div class="stat-value danger">￥{{ summaryData.totalArrearsAmount || 0 }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div v-if="currentType === 'building'" class="building-area" v-loading="loading">
      <div class="table-toolbar">
        <span class="threshold-tip">欠费率高亮阈值：
          <el-input-number v-model="threshold" :min="0" :max="100" :precision="2" size="small" style="width: 120px;" @change="onThresholdChange" />
          <span style="margin-left:6px;">%</span>
        </span>
        <el-button type="danger" :disabled="selectedOwnerIds.length === 0" @click="openBatchReminder">
          生成催缴单（{{ selectedOwnerIds.length }}）
        </el-button>
      </div>

      <el-table
        :data="buildingRows"
        row-key="buildingNo"
        :expand-row-keys="expandedKeys"
        @expand-change="onExpandBuilding"
        :row-class-name="buildingRowClass"
        stripe
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="unit-table">
              <el-table
                :data="row.units || []"
                v-loading="row.loadingUnits"
                row-key="unitNo"
                size="small"
              >
                <el-table-column type="expand">
                  <template #default="{ row: unitRow }">
                    <el-table :data="unitRow.rooms || []" size="small">
                      <el-table-column label="业主" prop="ownerName" min-width="100" />
                      <el-table-column label="房间" min-width="120">
                        <template #default="{ row: r }">{{ r.buildingNo }}-{{ r.unitNo }}-{{ r.roomNo }}</template>
                      </el-table-column>
                      <el-table-column label="应收" prop="receivableAmount" min-width="100" />
                      <el-table-column label="实收" prop="paidAmount" min-width="100" />
                      <el-table-column label="欠费" prop="arrearsAmount" min-width="100">
                        <template #default="{ row: r }">
                          <span :class="{ 'arrears-danger': Number(r.arrearsAmount) > 0 }">￥{{ r.arrearsAmount }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="缴费率" min-width="100">
                        <template #default="{ row: r }">{{ r.collectionRate }}%</template>
                      </el-table-column>
                      <el-table-column label="操作" width="120">
                        <template #default="{ row: r }">
                          <el-button v-if="Number(r.arrearsAmount) > 0" type="primary" link @click="toggleSelectOwner(r.ownerId)">
                            {{ selectedOwnerIds.includes(r.ownerId) ? '取消选中' : '选中催缴' }}
                          </el-button>
                        </template>
                      </el-table-column>
                    </el-table>
                  </template>
                </el-table-column>
                <el-table-column label="单元" prop="unitNo" min-width="80" />
                <el-table-column label="应收" prop="receivableAmount" min-width="100" />
                <el-table-column label="实收" prop="paidAmount" min-width="100" />
                <el-table-column label="欠费" min-width="100">
                  <template #default="{ row: u }">
                    <span :class="{ 'arrears-danger': Number(u.arrearsAmount) > 0 }">￥{{ u.arrearsAmount }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="缴费率" min-width="100">
                  <template #default="{ row: u }">{{ u.collectionRate }}%</template>
                </el-table-column>
                <el-table-column label="欠费率" min-width="100">
                  <template #default="{ row: u }">{{ u.arrearsRate }}%</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="楼栋号" prop="buildingNo" min-width="100" />
        <el-table-column label="应收金额" prop="receivableAmount" min-width="120" />
        <el-table-column label="实收金额" prop="paidAmount" min-width="120" />
        <el-table-column label="欠费金额" min-width="120">
          <template #default="{ row }">
            <span :class="{ 'arrears-danger': row.highlighted }">￥{{ row.arrearsAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="缴费率" min-width="100">
          <template #default="{ row }">{{ row.collectionRate }}%</template>
        </el-table-column>
        <el-table-column label="欠费率" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="row.highlighted" type="danger">{{ row.arrearsRate }}%</el-tag>
            <span v-else>{{ row.arrearsRate }}%</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <div class="table-area" v-if="!showSummary && currentType !== 'building'">
      <div class="table-toolbar">
        <el-button type="success" @click="handleExport">导出Excel</el-button>
        <el-button @click="handlePrint">打印</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <template v-if="currentType === 'payment'">
          <el-table-column prop="paymentNo" label="缴费单号" min-width="200" />
          <el-table-column prop="ownerName" label="业主" min-width="100" />
          <el-table-column prop="roomInfo" label="房间" min-width="120" />
          <el-table-column prop="feeName" label="费用" min-width="100" />
          <el-table-column prop="amount" label="金额" min-width="100" />
          <el-table-column prop="paymentMethod" label="方式" min-width="100" />
          <el-table-column prop="paymentTime" label="时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.paymentTime) }}</template>
          </el-table-column>
        </template>
        <template v-else>
          <el-table-column prop="billNo" label="账单编号" min-width="200" />
          <el-table-column prop="ownerName" label="业主" min-width="100" />
          <el-table-column prop="roomInfo" label="房间" min-width="120" />
          <el-table-column prop="feeName" label="费用" min-width="100" />
          <el-table-column prop="amount" label="欠费金额" min-width="100" />
          <el-table-column prop="period" label="周期" min-width="200" />
          <el-table-column prop="dueDate" label="截止日期" min-width="120">
            <template #default="{ row }">{{ formatDate(row.dueDate) }}</template>
          </el-table-column>
        </template>
      </el-table>
    </div>

    <BatchReminderDialog
      v-model="showReminderDialog"
      :owner-ids="selectedOwnerIds"
      :period-start="periodStartParam"
      :period-end="periodEndParam"
      @success="onReminderGenerated"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPaymentDetail, getArrearsDetail, getSummary,
  exportPaymentDetail, exportArrearsDetail,
  getBuildingCollection, getBuildingUnits
} from '@/api/statistics'
import { formatDate, formatDateTime } from '@/utils'
import BatchReminderDialog from '@/components/BatchReminderDialog.vue'

const loading = ref(false)
const tableData = ref([])
const currentType = ref('payment')
const showSummary = ref(false)
const summaryData = ref({})

const searchForm = reactive({
  feeType: 'PROPERTY',
  dateRange: []
})

const buildingRows = ref([])
const expandedKeys = ref([])
const threshold = ref(30)
const selectedOwnerIds = ref([])
const showReminderDialog = ref(false)

const periodStartParam = computed(() => formatRange()?.startDate || null)
const periodEndParam = computed(() => formatRange()?.endDate || null)

const formatRange = () => {
  if (searchForm.dateRange?.length !== 2) return null
  const fmt = (date) => {
    const d = new Date(date)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }
  return { startDate: fmt(searchForm.dateRange[0]), endDate: fmt(searchForm.dateRange[1]) }
}

const getParams = () => {
  const params = { feeType: searchForm.feeType }
  const range = formatRange()
  if (range) {
    params.startDate = range.startDate
    params.endDate = range.endDate
  }
  return params
}

const loadPaymentDetail = async () => {
  if (!searchForm.dateRange?.length) { ElMessage.warning('请选择日期范围'); return }
  loading.value = true
  currentType.value = 'payment'
  showSummary.value = false
  try {
    const res = await getPaymentDetail(getParams())
    tableData.value = res.data
  } finally { loading.value = false }
}

const loadArrearsDetail = async () => {
  if (!searchForm.dateRange?.length) { ElMessage.warning('请选择日期范围'); return }
  loading.value = true
  currentType.value = 'arrears'
  showSummary.value = false
  try {
    const res = await getArrearsDetail(getParams())
    tableData.value = res.data
  } finally { loading.value = false }
}

const loadSummary = async () => {
  if (!searchForm.dateRange?.length) { ElMessage.warning('请选择日期范围'); return }
  loading.value = true
  showSummary.value = true
  try {
    const res = await getSummary(getParams())
    summaryData.value = res.data
  } finally { loading.value = false }
}

const loadBuildingCollection = async () => {
  loading.value = true
  currentType.value = 'building'
  showSummary.value = false
  expandedKeys.value = []
  selectedOwnerIds.value = []
  try {
    const params = { feeType: searchForm.feeType, threshold: threshold.value }
    const range = formatRange()
    if (range) { params.startDate = range.startDate; params.endDate = range.endDate }
    const res = await getBuildingCollection(params)
    threshold.value = Number(res.data.threshold ?? threshold.value)
    buildingRows.value = (res.data.buildings || []).map(b => ({ ...b, units: null, loadingUnits: false }))
  } finally { loading.value = false }
}

const onThresholdChange = () => {
  if (currentType.value === 'building') {
    buildingRows.value.forEach(row => {
      row.highlighted = Number(row.arrearsRate) > Number(threshold.value)
    })
  }
}

const onExpandBuilding = async (row, expanded) => {
  if (!expanded || row.units) return
  row.loadingUnits = true
  try {
    const params = { feeType: searchForm.feeType }
    const range = formatRange()
    if (range) { params.startDate = range.startDate; params.endDate = range.endDate }
    const res = await getBuildingUnits(row.buildingNo, params)
    row.units = res.data || []
  } finally {
    row.loadingUnits = false
  }
}

const buildingRowClass = ({ row }) => row.highlighted ? 'row-highlight-arrears' : ''

const toggleSelectOwner = (ownerId) => {
  const idx = selectedOwnerIds.value.indexOf(ownerId)
  if (idx >= 0) selectedOwnerIds.value.splice(idx, 1)
  else selectedOwnerIds.value.push(ownerId)
}

const openBatchReminder = () => {
  if (selectedOwnerIds.value.length === 0) {
    ElMessage.warning('请先选择需要催缴的欠费户')
    return
  }
  showReminderDialog.value = true
}

const onReminderGenerated = () => {
  ElMessage.success('催缴单已生成')
  selectedOwnerIds.value = []
}

const handleExport = async () => {
  if (!searchForm.dateRange?.length) { ElMessage.warning('请选择日期范围'); return }
  try {
    const exportFn = currentType.value === 'payment' ? exportPaymentDetail : exportArrearsDetail
    const res = await exportFn(getParams())
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.download = currentType.value === 'payment' ? '缴费明细.xlsx' : '欠费明细.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

const handlePrint = () => {
  const title = currentType.value === 'payment' ? '缴费明细' : '欠费明细'
  const headers = currentType.value === 'payment' 
    ? ['缴费单号', '业主', '房间', '费用', '金额', '方式', '时间']
    : ['账单编号', '业主', '房间', '费用', '欠费金额', '周期', '截止日期']
  const keys = currentType.value === 'payment'
    ? ['paymentNo', 'ownerName', 'roomInfo', 'feeName', 'amount', 'paymentMethod', 'paymentTime']
    : ['billNo', 'ownerName', 'roomInfo', 'feeName', 'amount', 'period', 'dueDate']
  
  const today = new Date().toLocaleDateString('zh-CN')
  
  let tableRows = ''
  tableData.value.forEach(row => {
    tableRows += '<tr>'
    keys.forEach(k => tableRows += `<td>${row[k] || ''}</td>`)
    tableRows += '</tr>'
  })
  
  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="UTF-8">
      <title>${title}</title>
      <style>
        @page { size: A4; margin: 15mm 20mm; }
        @media print { body { margin: 0; padding: 0; } }
        body { font-family: "SimSun", "宋体", serif; font-size: 12pt; line-height: 1.6; color: #000; }
        .print-container { width: 170mm; margin: 0 auto; padding: 10mm 0; }
        .print-title { text-align: center; font-size: 18pt; font-weight: bold; margin-bottom: 20px; }
        .print-info { text-align: right; font-size: 10pt; color: #666; margin-bottom: 10px; }
        .print-table { width: 100%; border-collapse: collapse; font-size: 10pt; }
        .print-table th, .print-table td { border: 1px solid #000; padding: 6px 8px; text-align: left; }
        .print-table th { background-color: #f5f5f5; font-weight: bold; }
        .print-footer { margin-top: 30px; text-align: right; font-size: 10pt; }
      </style>
    </head>
    <body>
      <div class="print-container">
        <h2 class="print-title">${title}</h2>
        <div class="print-info">打印日期：${today}</div>
        <table class="print-table">
          <thead><tr>${headers.map(h => `<th>${h}</th>`).join('')}</tr></thead>
          <tbody>${tableRows}</tbody>
        </table>
        <div class="print-footer">共 ${tableData.value.length} 条记录</div>
      </div>
    </body>
    </html>
  `
  
  const printWindow = window.open('', '_blank')
  printWindow.document.write(html)
  printWindow.document.close()
  printWindow.print()
}
</script>

<style lang="scss" scoped>
.summary-area { margin-bottom: 20px; }
.stat-item { text-align: center; padding: 10px; }
.stat-value { font-size: 24px; font-weight: bold; margin-top: 8px; color: var(--text-primary); &.success { color: var(--success-color); } &.danger { color: var(--danger-color); } }
.stat-label { color: var(--text-secondary); font-size: 14px; }
.table-toolbar { margin-bottom: 16px; display: flex; align-items: center; gap: 12px; }
.threshold-tip { display: inline-flex; align-items: center; color: var(--text-secondary); }
.building-area { margin-top: 8px; }
.unit-table { padding: 8px 32px; background-color: #fafafa; }
.arrears-danger { color: var(--danger-color); font-weight: bold; }
:deep(.row-highlight-arrears) { background-color: #fff1f0 !important; }
</style>
