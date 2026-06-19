<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="明细查询" name="detail">
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
        
        <div class="table-area" v-if="!showSummary">
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
      </el-tab-pane>
      
      <el-tab-pane label="楼栋缴费率" name="building">
        <div class="search-area">
          <el-form :inline="true" :model="buildingSearchForm">
            <el-form-item label="费用类型">
              <el-select v-model="buildingSearchForm.feeType" style="width: 150px;">
                <el-option label="物业费" value="PROPERTY" />
                <el-option label="车位费" value="PARKING" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker v-model="buildingSearchForm.dateRange" type="daterange" start-placeholder="开始" end-placeholder="结束" style="width: 260px;" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadBuildingSummary">查询</el-button>
            </el-form-item>
            <el-form-item v-if="arrearsThreshold">
              <span class="threshold-tip">高亮阈值: 欠费率 ≥ {{ arrearsThreshold }}%</span>
            </el-form-item>
          </el-form>
        </div>
        
        <div class="table-toolbar" v-if="selectedArrearsOwners.length > 0">
          <el-alert :title="`已选中 ${selectedArrearsOwners.length} 户欠费业主`" type="info" show-icon :closable="false" style="margin-right: 16px;" />
          <el-button type="danger" @click="showReminderDialog = true">批量生成催缴单</el-button>
        </div>
        
        <div class="building-summary-area">
          <el-table 
            :data="buildingData" 
            v-loading="buildingLoading" 
            stripe 
            border
            style="width: 100%;"
            row-key="buildingNo"
            @expand-change="handleBuildingExpand"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="unit-detail">
                  <div v-if="!(row.buildingNo in unitDetails)" class="loading-unit">加载中...</div>
                  <template v-else>
                    <el-table 
                      :data="unitDetails[row.buildingNo] || []" 
                      border 
                      size="small"
                      row-key="unitNo"
                      style="margin: 10px 50px;"
                      :default-expand-all="false"
                    >
                      <el-table-column type="expand">
                        <template #default="{ row: unitRow }">
                          <el-table :data="unitRow.rooms || []" border size="small" style="margin: 10px 50px;">
                            <el-table-column label="选择" width="60" align="center">
                              <template #default="{ row: roomRow }">
                                <el-checkbox 
                                  v-if="roomRow.totalArrears > 0"
                                  :model-value="selectedArrearsOwners.includes(roomRow.ownerId)"
                                  @change="(val) => toggleRoomSelection(roomRow, val)"
                                />
                              </template>
                            </el-table-column>
                            <el-table-column prop="roomNo" label="房间号" width="100" />
                            <el-table-column prop="ownerName" label="业主" width="100" />
                            <el-table-column prop="phone" label="电话" width="130" />
                            <el-table-column prop="totalReceivable" label="应收(元)" width="110" align="right">
                              <template #default="{ row }">￥{{ row.totalReceivable || 0 }}</template>
                            </el-table-column>
                            <el-table-column prop="totalPaid" label="实收(元)" width="110" align="right">
                              <template #default="{ row }">￥{{ row.totalPaid || 0 }}</template>
                            </el-table-column>
                            <el-table-column prop="totalArrears" label="欠费(元)" width="110" align="right">
                              <template #default="{ row }">
                                <span :class="{'text-danger': row.totalArrears > 0}">￥{{ row.totalArrears || 0 }}</span>
                              </template>
                            </el-table-column>
                            <el-table-column prop="collectionRate" label="缴费率" width="100" align="center">
                              <template #default="{ row }">{{ row.collectionRate || 0 }}%</template>
                            </el-table-column>
                            <el-table-column prop="arrearsRate" label="欠费率" width="100" align="center">
                              <template #default="{ row }">
                                <span :class="{'text-danger': row.arrearsRate >= arrearsThreshold}">{{ row.arrearsRate || 0 }}%</span>
                              </template>
                            </el-table-column>
                          </el-table>
                        </template>
                      </el-table-column>
                      <el-table-column prop="unitNo" label="单元号" width="120" />
                      <el-table-column prop="totalReceivable" label="应收金额(元)" min-width="120" align="right">
                        <template #default="{ row }">￥{{ row.totalReceivable || 0 }}</template>
                      </el-table-column>
                      <el-table-column prop="totalPaid" label="实收金额(元)" min-width="120" align="right">
                        <template #default="{ row }">￥{{ row.totalPaid || 0 }}</template>
                      </el-table-column>
                      <el-table-column prop="totalArrears" label="欠费金额(元)" min-width="120" align="right">
                        <template #default="{ row }">
                          <span :class="{'text-danger': row.totalArrears > 0}">￥{{ row.totalArrears || 0 }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column prop="collectionRate" label="缴费率" width="110" align="center">
                        <template #default="{ row }">{{ row.collectionRate || 0 }}%</template>
                      </el-table-column>
                      <el-table-column prop="arrearsRate" label="欠费率" width="110" align="center">
                        <template #default="{ row }">
                          <el-tag :type="row.highlight ? 'danger' : 'success'" size="small">{{ row.arrearsRate || 0 }}%</el-tag>
                        </template>
                      </el-table-column>
                    </el-table>
                  </template>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="buildingNo" label="楼栋号" width="120">
              <template #default="{ row }">{{ row.buildingNo }}栋</template>
            </el-table-column>
            <el-table-column prop="unitCount" label="单元数" width="90" align="center" />
            <el-table-column prop="totalReceivable" label="应收金额(元)" min-width="130" align="right">
              <template #default="{ row }">￥{{ row.totalReceivable || 0 }}</template>
            </el-table-column>
            <el-table-column prop="totalPaid" label="实收金额(元)" min-width="130" align="right">
              <template #default="{ row }">￥{{ row.totalPaid || 0 }}</template>
            </el-table-column>
            <el-table-column prop="totalArrears" label="欠费金额(元)" min-width="130" align="right">
              <template #default="{ row }">
                <span :class="{'text-danger': row.totalArrears > 0}">￥{{ row.totalArrears || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="collectionRate" label="缴费率" width="110" align="center">
              <template #default="{ row }">
                <span class="rate-text">{{ row.collectionRate || 0 }}%</span>
              </template>
            </el-table-column>
            <el-table-column prop="arrearsRate" label="欠费率" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.highlight ? 'danger' : 'success'" size="small">{{ row.arrearsRate || 0 }}%</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
    
    <ReminderDialog v-model="showReminderDialog" :owner-ids="selectedArrearsOwners" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPaymentDetail, getArrearsDetail, getSummary, exportPaymentDetail, exportArrearsDetail, getBuildingSummary, getBuildingDetail, getArrearsThreshold } from '@/api/statistics'
import { formatDate, formatDateTime } from '@/utils'
import ReminderDialog from '@/components/ReminderDialog.vue'

const loading = ref(false)
const tableData = ref([])
const currentType = ref('payment')
const showSummary = ref(false)
const summaryData = ref({})
const activeTab = ref('detail')

const searchForm = reactive({
  feeType: 'PROPERTY',
  dateRange: []
})

const buildingLoading = ref(false)
const buildingData = ref([])
const unitDetails = ref({})
const expandedBuilding = ref(null)
const arrearsThreshold = ref(20)
const selectedArrearsOwners = ref([])
const showReminderDialog = ref(false)

const buildingSearchForm = reactive({
  feeType: 'PROPERTY',
  dateRange: []
})

onMounted(() => {
  loadArrearsThreshold()
})

const loadArrearsThreshold = async () => {
  try {
    const res = await getArrearsThreshold()
    arrearsThreshold.value = res.data || 20
  } catch (e) {}
}

const getDetailParams = () => {
  const params = { feeType: searchForm.feeType }
  if (searchForm.dateRange?.length === 2) {
    const formatDateStr = (date) => {
      const d = new Date(date)
      const year = d.getFullYear()
      const month = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${year}-${month}-${day}`
    }
    params.startDate = formatDateStr(searchForm.dateRange[0])
    params.endDate = formatDateStr(searchForm.dateRange[1])
  }
  return params
}

const getBuildingParams = () => {
  const params = { feeType: buildingSearchForm.feeType }
  if (buildingSearchForm.dateRange?.length === 2) {
    const formatDateStr = (date) => {
      const d = new Date(date)
      const year = d.getFullYear()
      const month = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${year}-${month}-${day}`
    }
    params.startDate = formatDateStr(buildingSearchForm.dateRange[0])
    params.endDate = formatDateStr(buildingSearchForm.dateRange[1])
  }
  return params
}

const loadPaymentDetail = async () => {
  if (!searchForm.dateRange?.length) { ElMessage.warning('请选择日期范围'); return }
  loading.value = true
  currentType.value = 'payment'
  showSummary.value = false
  try {
    const res = await getPaymentDetail(getDetailParams())
    tableData.value = res.data
  } finally { loading.value = false }
}

const loadArrearsDetail = async () => {
  if (!searchForm.dateRange?.length) { ElMessage.warning('请选择日期范围'); return }
  loading.value = true
  currentType.value = 'arrears'
  showSummary.value = false
  try {
    const res = await getArrearsDetail(getDetailParams())
    tableData.value = res.data
  } finally { loading.value = false }
}

const loadSummary = async () => {
  if (!searchForm.dateRange?.length) { ElMessage.warning('请选择日期范围'); return }
  loading.value = true
  showSummary.value = true
  try {
    const res = await getSummary(getDetailParams())
    summaryData.value = res.data
  } finally { loading.value = false }
}

const loadBuildingSummary = async () => {
  buildingLoading.value = true
  selectedArrearsOwners.value = []
  try {
    const res = await getBuildingSummary(getBuildingParams())
    buildingData.value = res.data || []
    unitDetails.value = {}
  } finally { buildingLoading.value = false }
}

const handleTabChange = () => {
  if (activeTab.value === 'building' && buildingData.value.length === 0) {
    loadBuildingSummary()
  }
}

const handleBuildingExpand = async (row, expandedRows) => {
  const isExpanded = expandedRows.some(r => r.buildingNo === row.buildingNo)
  if (isExpanded) {
    expandedBuilding.value = row.buildingNo
    if (!(row.buildingNo in unitDetails.value)) {
      try {
        const res = await getBuildingDetail(row.buildingNo, getBuildingParams())
        unitDetails.value[row.buildingNo] = res.data || []
      } catch (e) {
        ElMessage.error('加载单元明细失败')
      }
    }
  }
}

const toggleRoomSelection = (room, selected) => {
  if (selected) {
    if (!selectedArrearsOwners.value.includes(room.ownerId)) {
      selectedArrearsOwners.value.push(room.ownerId)
    }
  } else {
    const idx = selectedArrearsOwners.value.indexOf(room.ownerId)
    if (idx > -1) selectedArrearsOwners.value.splice(idx, 1)
  }
}

const handleExport = async () => {
  if (!searchForm.dateRange?.length) { ElMessage.warning('请选择日期范围'); return }
  try {
    const exportFn = currentType.value === 'payment' ? exportPaymentDetail : exportArrearsDetail
    const res = await exportFn(getDetailParams())
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
.stat-label { color: var(--text-secondary); font-size: 14px; }
.stat-value { font-size: 24px; font-weight: bold; margin-top: 8px; color: var(--text-primary); &.success { color: var(--success-color); } &.danger { color: var(--danger-color); } }
.table-toolbar { margin-bottom: 16px; display: flex; align-items: center; }
.threshold-tip { font-size: 13px; color: var(--el-text-color-secondary); margin-left: 8px; }
.building-summary-area { margin-top: 10px; }
.unit-detail { padding: 10px 0; }
.loading-unit { text-align: center; color: #909399; padding: 20px; }
.text-danger { color: var(--el-color-danger); font-weight: 600; }
.rate-text { font-weight: 600; }
</style>
