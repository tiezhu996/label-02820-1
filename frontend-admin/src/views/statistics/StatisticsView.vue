<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" type="card">
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

      <el-tab-pane label="楼栋汇总" name="building">
        <div class="search-area">
          <el-form :inline="true" :model="buildingSearchForm">
            <el-form-item label="费用类型">
              <el-select v-model="buildingSearchForm.feeType" placeholder="全部" clearable style="width: 150px;">
                <el-option label="物业费" value="PROPERTY" />
                <el-option label="车位费" value="PARKING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadBuildingSummary">查询</el-button>
            </el-form-item>
            <el-form-item>
              <span v-if="buildingData" class="period-info">当前账期：<strong>{{ buildingData.currentPeriod }}</strong></span>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="buildingData" class="building-summary-area">
          <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
            <template #title>
              欠费率阈值：<strong>{{ buildingData.threshold }}%</strong>，超过阈值的楼栋将以红色高亮显示。仅统计当前账期数据。
            </template>
          </el-alert>

          <el-row :gutter="20" style="margin-bottom: 20px;">
            <el-col :span="4">
              <el-card shadow="hover">
                <div class="stat-item">
                  <div class="stat-label">总户数</div>
                  <div class="stat-value">{{ buildingData.totalOwners || 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover">
                <div class="stat-item">
                  <div class="stat-label">欠费户数</div>
                  <div class="stat-value danger">{{ buildingData.totalArrearsOwners || 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover">
                <div class="stat-item">
                  <div class="stat-label">总应收</div>
                  <div class="stat-value">￥{{ buildingData.totalReceivable || 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover">
                <div class="stat-item">
                  <div class="stat-label">总实收</div>
                  <div class="stat-value success">￥{{ buildingData.totalReceived || 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover">
                <div class="stat-item">
                  <div class="stat-label">总欠费</div>
                  <div class="stat-value danger">￥{{ buildingData.totalArrears || 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="hover">
                <div class="stat-item">
                  <div class="stat-label">整体缴费率</div>
                  <div class="stat-value" :class="{ danger: buildingData.totalCollectionRate < (100 - buildingData.threshold) }">
                    {{ buildingData.totalCollectionRate || 0 }}%
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <div class="table-toolbar" style="margin-bottom: 16px;">
            <el-button type="warning" :disabled="selectedRoomOwners.length === 0" @click="openBatchGenerate(selectedRoomOwners)">
              批量生成催缴单 ({{ selectedRoomOwners.length }})
            </el-button>
          </div>

          <el-table
            :data="buildingData.buildings"
            v-loading="buildingLoading"
            stripe
            border
            style="width: 100%;"
            row-key="buildingNo"
            :row-class-name="buildingRowClassName"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="unit-detail" style="padding: 10px 50px;">
                  <el-table
                    :data="row.units"
                    row-key="unitNo"
                    border
                    size="small"
                    style="width: 100%;"
                  >
                    <el-table-column type="expand">
                      <template #default="{ row: unitRow }">
                        <div style="padding: 10px 50px;">
                          <el-table
                            :data="unitRow.rooms"
                            border
                            size="small"
                            @selection-change="(sel) => handleRoomSelectionChange(sel, row.buildingNo, unitRow.unitNo)"
                            :row-key="(r) => r.ownerId"
                            style="width: 100%;"
                          >
                            <el-table-column type="selection" width="50" :selectable="isRoomArrears" />
                            <el-table-column prop="roomNo" label="房间号" min-width="80" />
                            <el-table-column prop="ownerName" label="业主" min-width="100" />
                            <el-table-column prop="phone" label="电话" min-width="120" />
                            <el-table-column prop="feeName" label="费用明细" min-width="200">
                              <template #default="{ row: room }">
                                <span v-html="room.feeName"></span>
                              </template>
                            </el-table-column>
                            <el-table-column prop="receivable" label="应收(元)" min-width="90" />
                            <el-table-column prop="received" label="已收(元)" min-width="90" />
                            <el-table-column prop="arrears" label="欠费(元)" min-width="90">
                              <template #default="{ row: room }">
                                <span v-if="room.arrears > 0" class="danger-text" style="font-weight: bold;">￥{{ room.arrears }}</span>
                                <span v-else style="color: #67c23a;">-</span>
                              </template>
                            </el-table-column>
                            <el-table-column label="操作" width="100">
                              <template #default="{ row: room }">
                                <el-button v-if="room.hasArrears" type="warning" link size="small" @click="openBatchGenerate([room.ownerId])">
                                  催缴
                                </el-button>
                              </template>
                            </el-table-column>
                          </el-table>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column prop="unitNo" label="单元号" min-width="100" />
                    <el-table-column prop="receivable" label="应收金额(元)" min-width="110" />
                    <el-table-column prop="received" label="实收金额(元)" min-width="110" />
                    <el-table-column prop="arrears" label="欠费金额(元)" min-width="110">
                      <template #default="{ row: unit }">
                        <span :class="{ 'danger-text': unit.arrears > 0 }">￥{{ unit.arrears }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="collectionRate" label="缴费率" min-width="90">
                      <template #default="{ row: unit }">
                        <span :class="{ 'danger-text': (100 - unit.collectionRate) > buildingData.threshold }">
                          {{ unit.collectionRate }}%
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="roomCount" label="户数" min-width="70" />
                  </el-table>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="buildingNo" label="楼栋" min-width="120">
              <template #default="{ row }">
                <strong>{{ row.buildingNo }}栋</strong>
                <el-tag v-if="row.highlighted" type="danger" size="small" style="margin-left: 8px;">高欠费</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="户数/欠费户" min-width="110">
              <template #default="{ row }">
                {{ row.roomCount }} / <span :class="{ 'danger-text': row.arrearsRoomCount > 0 }">{{ row.arrearsRoomCount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="receivable" label="应收金额(元)" min-width="130" sortable />
            <el-table-column prop="received" label="实收金额(元)" min-width="130" sortable />
            <el-table-column prop="arrears" label="欠费金额(元)" min-width="130" sortable>
              <template #default="{ row }">
                <span class="danger-text" style="font-weight: bold;">￥{{ row.arrears }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="collectionRate" label="缴费率" min-width="150" sortable>
              <template #default="{ row }">
                <el-progress
                  :percentage="row.collectionRate"
                  :color="row.highlighted ? '#f56c6c' : '#67c23a'"
                  :stroke-width="16"
                />
              </template>
            </el-table-column>
            <el-table-column prop="arrearsRate" label="欠费率" min-width="100">
              <template #default="{ row }">
                <span :class="{ 'danger-text': row.highlighted, 'high-rate': row.highlighted }">
                  {{ row.arrearsRate }}%
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showReminderDialog" title="生成催缴单" width="500px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="已选业主">
          <span>{{ pendingGenerateIds.length }} 户</span>
        </el-form-item>
        <el-form-item label="选择模板">
          <el-select v-model="selectedTemplateId" placeholder="请选择催缴模板" style="width: 100%;">
            <el-option
              v-for="t in reminderTemplates"
              :key="t.id"
              :label="t.templateName"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReminderDialog = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerateReminders">生成并预览</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPaymentDetail, getArrearsDetail, getSummary, getBuildingSummary,
  exportPaymentDetail, exportArrearsDetail
} from '@/api/statistics'
import { getTemplates, batchGenerateReminders } from '@/api/template'
import { formatDate, formatDateTime } from '@/utils'

const activeTab = ref('detail')
const loading = ref(false)
const buildingLoading = ref(false)
const tableData = ref([])
const currentType = ref('payment')
const showSummary = ref(false)
const summaryData = ref({})
const buildingData = ref(null)

const unitSelectionMap = reactive(new Map())

const selectedRoomOwners = computed(() => {
  const all = new Set()
  for (const ids of unitSelectionMap.values()) {
    ids.forEach(id => all.add(id))
  }
  return [...all]
})

const showReminderDialog = ref(false)
const selectedTemplateId = ref(null)
const reminderTemplates = ref([])
const generating = ref(false)

const searchForm = reactive({
  feeType: 'PROPERTY',
  dateRange: []
})

const buildingSearchForm = reactive({
  feeType: ''
})

const getParams = () => {
  const params = { feeType: searchForm.feeType }
  if (searchForm.dateRange?.length === 2) {
    const fmt = (date) => {
      const d = new Date(date)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    }
    params.startDate = fmt(searchForm.dateRange[0])
    params.endDate = fmt(searchForm.dateRange[1])
  }
  return params
}

const getBuildingParams = () => {
  const params = {}
  if (buildingSearchForm.feeType) params.feeType = buildingSearchForm.feeType
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

const loadBuildingSummary = async () => {
  buildingLoading.value = true
  unitSelectionMap.clear()
  try {
    const res = await getBuildingSummary(getBuildingParams())
    buildingData.value = res.data
  } finally { buildingLoading.value = false }
}

const buildingRowClassName = ({ row }) => {
  return row.highlighted ? 'highlighted-row' : ''
}

const isRoomArrears = (row) => {
  return row.hasArrears === true
}

const handleRoomSelectionChange = (selection, buildingNo, unitNo) => {
  const key = `${buildingNo}-${unitNo}`
  const ids = selection.filter(s => s.hasArrears).map(s => s.ownerId)
  if (ids.length === 0) {
    unitSelectionMap.delete(key)
  } else {
    unitSelectionMap.set(key, ids)
  }
}

const pendingGenerateIds = ref([])

const openBatchGenerate = async (ownerIds) => {
  if (!reminderTemplates.value.length) {
    const res = await getTemplates({ templateType: 'REMINDER' })
    reminderTemplates.value = res.data
  }
  pendingGenerateIds.value = ownerIds && ownerIds.length > 0
    ? [...new Set(ownerIds)]
    : selectedRoomOwners.value
  selectedTemplateId.value = reminderTemplates.value[0]?.id || null
  showReminderDialog.value = true
}

const handleGenerateReminders = async () => {
  if (!selectedTemplateId.value) {
    ElMessage.warning('请选择模板')
    return
  }
  if (!pendingGenerateIds.value.length) {
    ElMessage.warning('请先选择欠费户')
    return
  }
  generating.value = true
  try {
    const res = await batchGenerateReminders({
      ownerIds: pendingGenerateIds.value,
      templateId: selectedTemplateId.value
    })
    showReminderDialog.value = false
    const printWindow = window.open('', '_blank')
    printWindow.document.write(res.data)
    printWindow.document.close()
    setTimeout(() => printWindow.print(), 300)
    ElMessage.success('催缴单已生成，请在新窗口中查看和打印')
  } finally {
    generating.value = false
  }
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

onMounted(() => {
  loadBuildingSummary()
})
</script>

<style lang="scss" scoped>
.summary-area { margin-bottom: 20px; }
.building-summary-area { margin-top: 10px; }
.period-info { color: #606266; font-size: 14px; }
.stat-item { text-align: center; padding: 10px; }
.stat-label { color: var(--text-secondary); font-size: 14px; }
.stat-value { font-size: 24px; font-weight: bold; margin-top: 8px; color: var(--text-primary); &.success { color: var(--success-color); } &.danger { color: var(--danger-color); } }
.table-toolbar { margin-bottom: 16px; }
.danger-text { color: #f56c6c; }
.high-rate { font-weight: bold; font-size: 15px; }
.unit-detail { background-color: #fafafa; }

:deep(.highlighted-row) {
  background-color: #fef0f0 !important;
  td { background-color: #fef0f0 !important; }
  &:hover td { background-color: #fde2e2 !important; }
}
</style>
