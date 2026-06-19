<template>
  <div class="statistics-view">
    <el-card>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="缴费/欠费明细" name="detail">
          <el-form :inline="true" :model="detailQuery" class="search-form">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="detailQuery.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
            <el-form-item label="费用类型">
              <el-select v-model="detailQuery.feeType" placeholder="全部" clearable style="width: 150px">
                <el-option label="物业费" value="PROPERTY" />
                <el-option label="车位费" value="PARKING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadDetail">查询</el-button>
              <el-button @click="resetDetail">重置</el-button>
            </el-form-item>
          </el-form>

          <el-tabs v-model="detailTab" type="card">
            <el-tab-pane label="缴费明细" name="payment">
              <div class="table-toolbar">
                <el-button type="success" @click="exportData('payment')">
                  <el-icon><Download /></el-icon>
                  导出
                </el-button>
              </div>
              <el-table :data="paymentList" v-loading="detailLoading" border stripe>
                <el-table-column prop="paymentNo" label="缴费单号" width="180" />
                <el-table-column prop="ownerName" label="业主" width="120" />
                <el-table-column prop="roomNo" label="房号" width="100" />
                <el-table-column prop="feeType" label="费用类型" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.feeType === 'PROPERTY' ? 'primary' : 'warning'" size="small">
                      {{ row.feeType === 'PROPERTY' ? '物业费' : '车位费' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="amount" label="缴费金额" width="120">
                  <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="paymentMethod" label="支付方式" width="100" />
                <el-table-column prop="payTime" label="缴费时间" width="180" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="欠费明细" name="arrears">
              <div class="table-toolbar">
                <el-button type="success" @click="exportData('arrears')">
                  <el-icon><Download /></el-icon>
                  导出
                </el-button>
              </div>
              <el-table :data="arrearsList" v-loading="detailLoading" border stripe>
                <el-table-column prop="billNo" label="账单编号" width="180" />
                <el-table-column prop="ownerName" label="业主" width="120" />
                <el-table-column prop="roomNo" label="房号" width="100" />
                <el-table-column prop="feeType" label="费用类型" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.feeType === 'PROPERTY' ? 'primary' : 'warning'" size="small">
                      {{ row.feeType === 'PROPERTY' ? '物业费' : '车位费' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="period" label="账期" width="120" />
                <el-table-column prop="amount" label="应收金额" width="120">
                  <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="paidAmount" label="已收金额" width="120">
                  <template #default="{ row }">¥{{ row.paidAmount?.toFixed(2) }}</template>
                </el-table-column>
                <el-table-column label="欠费金额" width="120">
                  <template #default="{ row }">
                    <span class="arrears-amount">¥{{ (row.amount - row.paidAmount)?.toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag type="danger" size="small">{{ row.status === 'OVERDUE' ? '逾期' : '未缴' }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <el-tab-pane label="楼栋缴费率" name="building">
          <el-form :inline="true" :model="buildingQuery" class="search-form">
            <el-form-item label="账期月份">
              <el-date-picker
                v-model="buildingQuery.periodMonth"
                type="month"
                placeholder="选择账期"
                value-format="YYYY-MM"
                format="YYYY年MM月"
              />
            </el-form-item>
            <el-form-item label="费用类型">
              <el-select v-model="buildingQuery.feeType" placeholder="全部" clearable style="width: 150px">
                <el-option label="物业费" value="PROPERTY" />
                <el-option label="车位费" value="PARKING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadBuildingSummary">查询</el-button>
              <el-button @click="resetBuilding">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="stat-cards">
            <div class="stat-card">
              <div class="stat-label">楼栋总数</div>
              <div class="stat-value">{{ buildingStats.totalBuildings }}</div>
            </div>
            <div class="stat-card warning">
              <div class="stat-label">欠费楼栋数</div>
              <div class="stat-value">{{ buildingStats.arrearsBuildings }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">总应收({{ currentPeriodMonth }})</div>
              <div class="stat-value">¥{{ buildingStats.totalReceivable?.toFixed(2) }}</div>
            </div>
            <div class="stat-card danger">
              <div class="stat-label">总欠费({{ currentPeriodMonth }})</div>
              <div class="stat-value">¥{{ buildingStats.totalArrears?.toFixed(2) }}</div>
            </div>
          </div>

          <div class="table-toolbar" style="margin-top: 20px">
            <el-button type="warning" @click="batchRemindSelected" :disabled="selectedBuildingOwners.length === 0">
              <el-icon><Bell /></el-icon>
              批量催缴 ({{ selectedBuildingOwners.length }}户)
            </el-button>
            <span v-if="arrearsThreshold > 0" style="margin-left: 12px; color: #909399; font-size: 13px">
              欠费率 ≥ {{ arrearsThreshold }}% 的楼栋高亮显示
            </span>
          </div>

          <el-table
            :data="buildingList"
            v-loading="buildingLoading"
            border
            stripe
            row-key="buildingNo"
            :row-class-name="buildingRowClassName"
            @expand-change="handleBuildingExpand"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="building-detail" v-loading="row.detailLoading">
                  <div v-for="unit in row.units" :key="unit.unitNo" class="unit-section">
                    <div class="unit-header">
                      <span>{{ unit.unitNo }}单元</span>
                      <span style="margin-left: 20px; color: #909399; font-size: 13px">
                        应收: ¥{{ unit.receivableAmount?.toFixed(2) }} |
                        实收: ¥{{ unit.paidAmount?.toFixed(2) }} |
                        欠费: <span class="arrears-amount">¥{{ unit.arrearsAmount?.toFixed(2) }}</span>
                      </span>
                    </div>
                    <el-table :data="unit.rooms" border size="small" style="margin: 8px 0 16px 20px">
                      <el-table-column label="选择" width="55">
                        <template #default="{ row: room }">
                          <el-checkbox
                            v-model="room.selected"
                            :disabled="!room.hasArrears"
                            @change="handleRoomSelectionChange"
                          />
                        </template>
                      </el-table-column>
                      <el-table-column prop="roomNo" label="房号" width="100" />
                      <el-table-column prop="ownerName" label="业主" width="120" />
                      <el-table-column prop="phone" label="电话" width="140" />
                      <el-table-column label="应收" width="120">
                        <template #default="{ row }">¥{{ row.receivableAmount?.toFixed(2) }}</template>
                      </el-table-column>
                      <el-table-column label="实收" width="120">
                        <template #default="{ row }">¥{{ row.paidAmount?.toFixed(2) }}</template>
                      </el-table-column>
                      <el-table-column label="欠费" width="120">
                        <template #default="{ row }">
                          <span :class="{ 'arrears-amount': row.hasArrears }">¥{{ row.arrearsAmount?.toFixed(2) }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="操作" width="120">
                        <template #default="{ row: room }">
                          <el-button
                            v-if="room.hasArrears"
                            type="warning"
                            link
                            size="small"
                            @click="remindSingleOwner(room)"
                          >催缴</el-button>
                          <span v-else style="color: #67c23a; font-size: 13px">已缴清</span>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="buildingNo" label="楼栋号" width="100" />
            <el-table-column prop="ownerCount" label="总户数" width="90" />
            <el-table-column prop="arrearsOwnerCount" label="欠费户数" width="100">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.arrearsOwnerCount > 0 }">{{ row.arrearsOwnerCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="应收金额" width="130">
              <template #default="{ row }">¥{{ row.receivableAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="实收金额" width="130">
              <template #default="{ row }">¥{{ row.paidAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="欠费金额" width="130">
              <template #default="{ row }">
                <span class="arrears-amount">¥{{ row.arrearsAmount?.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="缴费率" width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.collectionRate"
                  :color="row.collectionRate >= 90 ? '#67c23a' : row.collectionRate >= 70 ? '#e6a23c' : '#f56c6c'"
                  :stroke-width="16"
                />
              </template>
            </el-table-column>
            <el-table-column label="欠费率" width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.arrearsRate"
                  :color="row.arrearsRate >= arrearsThreshold ? '#f56c6c' : row.arrearsRate >= 10 ? '#e6a23c' : '#67c23a'"
                  :stroke-width="16"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="selectAllArrearsInBuilding(row)">选择欠费户</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <ReminderGenerateDialog
      v-model="reminderDialogVisible"
      :owners="reminderOwners"
      @success="handleReminderSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Download, Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPaymentDetail, getArrearsDetail, exportPaymentDetail, exportArrearsDetail, getBuildingSummary, getBuildingDetail } from '@/api/statistics'
import ReminderGenerateDialog from '@/components/ReminderGenerateDialog.vue'

const activeTab = ref('detail')
const detailTab = ref('payment')
const detailLoading = ref(false)
const buildingLoading = ref(false)

const detailQuery = reactive({
  startDate: '',
  endDate: '',
  feeType: '',
  dateRange: []
})

const buildingQuery = reactive({
  periodMonth: '',
  feeType: ''
})

const paymentList = ref([])
const arrearsList = ref([])
const buildingList = ref([])
const currentPeriodMonth = ref('')
const arrearsThreshold = ref(20)

const buildingStats = computed(() => {
  const totalBuildings = buildingList.value.length
  const arrearsBuildings = buildingList.value.filter(b => (b.arrearsAmount || 0) > 0).length
  let totalReceivable = 0
  let totalArrears = 0
  buildingList.value.forEach(b => {
    totalReceivable += Number(b.receivableAmount || 0)
    totalArrears += Number(b.arrearsAmount || 0)
  })
  return {
    totalBuildings,
    arrearsBuildings,
    totalReceivable,
    totalArrears
  }
})

const selectedBuildingOwners = ref([])

const reminderDialogVisible = ref(false)
const reminderOwners = ref([])

const buildingRowClassName = ({ row }) => {
  if (row.highlight) {
    return 'highlight-row'
  }
  return ''
}

const loadDetail = () => {
  detailLoading.value = true
  if (detailQuery.dateRange && detailQuery.dateRange.length === 2) {
    detailQuery.startDate = detailQuery.dateRange[0]
    detailQuery.endDate = detailQuery.dateRange[1]
  }
  Promise.all([
    getPaymentDetail(detailQuery),
    getArrearsDetail(detailQuery)
  ]).then(([paymentRes, arrearsRes]) => {
    paymentList.value = paymentRes.data || []
    arrearsList.value = arrearsRes.data || []
  }).finally(() => {
    detailLoading.value = false
  })
}

const resetDetail = () => {
  detailQuery.dateRange = []
  detailQuery.startDate = ''
  detailQuery.endDate = ''
  detailQuery.feeType = ''
  loadDetail()
}

const loadBuildingSummary = () => {
  buildingLoading.value = true
  getBuildingSummary(buildingQuery).then(res => {
    buildingList.value = (res.data.buildings || []).map(b => ({
      ...b,
      units: [],
      detailLoading: false,
      detailLoaded: false
    }))
    currentPeriodMonth.value = res.data.periodMonth || ''
    arrearsThreshold.value = Number(res.data.arrearsThreshold) || 20
    selectedBuildingOwners.value = []
  }).finally(() => {
    buildingLoading.value = false
  })
}

const resetBuilding = () => {
  buildingQuery.periodMonth = ''
  buildingQuery.feeType = ''
  loadBuildingSummary()
}

const handleBuildingExpand = (row, expandedRows) => {
  if (expandedRows.includes(row) && !row.detailLoaded) {
    row.detailLoading = true
    getBuildingDetail({
      buildingNo: row.buildingNo,
      feeType: buildingQuery.feeType,
      periodMonth: buildingQuery.periodMonth
    }).then(res => {
      row.units = (res.data.units || []).map(u => ({
        ...u,
        rooms: (u.rooms || []).map(r => ({ ...r, selected: false }))
      }))
      row.detailLoaded = true
    }).finally(() => {
      row.detailLoading = false
    })
  }
}

const handleRoomSelectionChange = () => {
  const selected = []
  buildingList.value.forEach(building => {
    building.units.forEach(unit => {
      unit.rooms.forEach(room => {
        if (room.selected && room.hasArrears) {
          selected.push({
            ownerId: room.ownerId,
            ownerName: room.ownerName,
            buildingNo: building.buildingNo,
            unitNo: unit.unitNo,
            roomNo: room.roomNo,
            phone: room.phone,
            cumulativeArrears: room.arrearsAmount
          })
        }
      })
    })
  })
  selectedBuildingOwners.value = selected
}

const selectAllArrearsInBuilding = (row) => {
  if (!row.detailLoaded) {
    ElMessage.info('请先展开楼栋查看明细')
    return
  }
  let hasUnselected = false
  row.units.forEach(unit => {
    unit.rooms.forEach(room => {
      if (room.hasArrears && !room.selected) {
        room.selected = true
        hasUnselected = true
      }
    })
  })
  handleRoomSelectionChange()
  if (!hasUnselected) {
    ElMessage.info('该楼栋所有欠费户已选中')
  }
}

const remindSingleOwner = (room) => {
  let buildingNo = ''
  let unitNo = ''
  for (const b of buildingList.value) {
    for (const u of b.units) {
      if (u.rooms.some(r => r.ownerId === room.ownerId)) {
        buildingNo = b.buildingNo
        unitNo = u.unitNo
        break
      }
    }
    if (buildingNo) break
  }
  reminderOwners.value = [{
    ownerId: room.ownerId,
    ownerName: room.ownerName,
    buildingNo,
    unitNo,
    roomNo: room.roomNo,
    phone: room.phone,
    cumulativeArrears: room.arrearsAmount
  }]
  reminderDialogVisible.value = true
}

const batchRemindSelected = () => {
  if (selectedBuildingOwners.value.length === 0) {
    ElMessage.warning('请先选择欠费业主')
    return
  }
  reminderOwners.value = [...selectedBuildingOwners.value]
  reminderDialogVisible.value = true
}

const handleReminderSuccess = () => {
  selectedBuildingOwners.value = []
  buildingList.value.forEach(b => {
    b.units.forEach(u => {
      u.rooms.forEach(r => {
        r.selected = false
      })
    })
  })
  ElMessage.success('催缴单已生成')
}

const exportData = (type) => {
  const params = { ...detailQuery }
  if (type === 'payment') {
    window.open(`/api/statistics/payment-detail/export?startDate=${params.startDate || ''}&endDate=${params.endDate || ''}&feeType=${params.feeType || ''}`, '_blank')
  } else {
    window.open(`/api/statistics/arrears-detail/export?startDate=${params.startDate || ''}&endDate=${params.endDate || ''}&feeType=${params.feeType || ''}`, '_blank')
  }
}

const getCurrentMonth = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

onMounted(() => {
  const currentMonth = getCurrentMonth()
  buildingQuery.periodMonth = currentMonth
  loadDetail()
  loadBuildingSummary()
})
</script>

<style scoped>
.statistics-view {
  padding: 20px;
}

.search-form {
  margin-bottom: 20px;
}

.table-toolbar {
  margin-bottom: 12px;
}

.arrears-amount {
  color: #f56c6c;
  font-weight: bold;
}

.text-danger {
  color: #f56c6c;
  font-weight: bold;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-top: 10px;
}

.stat-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  padding: 20px;
  color: white;
}

.stat-card.warning {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-card.danger {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
}

.stat-card .stat-label {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 8px;
}

.stat-card .stat-value {
  font-size: 28px;
  font-weight: bold;
}

.building-detail {
  padding: 10px 20px;
  background: #f5f7fa;
}

.unit-section {
  margin-bottom: 12px;
}

.unit-header {
  font-weight: bold;
  font-size: 14px;
  color: #303133;
  padding: 8px 0;
}

:deep(.highlight-row) {
  background-color: #fef0f0 !important;
}

:deep(.highlight-row td) {
  background-color: #fef0f0 !important;
}
</style>
