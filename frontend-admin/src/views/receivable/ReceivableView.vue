<template>
  <div class="page-container">
    <div class="search-area">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="账期">
          <el-date-picker v-model="searchForm.periodMonth" type="month" placeholder="选择月份" value-format="YYYY-MM" style="width: 150px;" />
        </el-form-item>
        <el-form-item label="费用类型">
          <el-select v-model="searchForm.feeType" placeholder="请选择" style="width: 150px;">
            <el-option label="物业费" value="PROPERTY" />
            <el-option label="车位费" value="PARKING" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExport">导出Excel</el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <div class="table-area">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="ownerName" label="业主" min-width="100" />
        <el-table-column prop="roomInfo" label="房间" min-width="120" />
        <el-table-column prop="feeType" label="费用类型" min-width="100">
          <template #default="{ row }">{{ row.feeType === 'PROPERTY' ? '物业费' : '车位费' }}</template>
        </el-table-column>
        <el-table-column prop="periodMonth" label="账期" min-width="100" />
        <el-table-column prop="amount" label="应收金额" min-width="100" />
        <el-table-column prop="paidAmount" label="已缴金额" min-width="100" />
        <el-table-column prop="cumulativeAmount" label="累计应收" min-width="100" />
        <el-table-column prop="isLocked" label="状态" min-width="80">
          <template #default="{ row }">
            <el-tag :type="row.isLocked ? 'danger' : 'success'">{{ row.isLocked ? '已锁定' : '未锁定' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="info" link @click="handleNotice(row)">通知单</el-button>
            <template v-if="isAdmin">
              <el-button type="primary" link @click="handleEdit(row)" :disabled="row.isLocked === 1">修改</el-button>
              <el-button type="warning" link @click="handleLock(row)" v-if="!row.isLocked">锁定</el-button>
              <el-button type="success" link @click="handleUnlock(row)" v-else>解锁</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size" :total="pagination.total" layout="total, prev, pager, next" @current-change="loadData" />
      </div>
    </div>
    
    <el-dialog v-model="showEditDialog" title="修改应收账款" width="400px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="应收金额">
          <el-input-number v-model="editForm.amount" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="已缴金额">
          <el-input-number v-model="editForm.paidAmount" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleEditSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const editLoading = ref(false)
const tableData = ref([])
const showEditDialog = ref(false)
const currentRow = ref(null)
const pagination = reactive({ page: 1, size: 10, total: 0 })
const searchForm = reactive({ periodMonth: '', feeType: '' })
const editForm = reactive({ amount: 0, paidAmount: 0 })

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/receivables', { params: { page: pagination.page, size: pagination.size, ...searchForm } })
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { periodMonth: '', feeType: '' }); handleSearch() }

const handleExport = async () => {
  try {
    const res = await request.get('/receivables/export', { params: searchForm, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.download = '应收账款.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

const handleEdit = (row) => {
  currentRow.value = row
  editForm.amount = row.amount
  editForm.paidAmount = row.paidAmount
  showEditDialog.value = true
}

const handleEditSubmit = async () => {
  editLoading.value = true
  try {
    await request.put(`/receivables/${currentRow.value.id}`, editForm)
    ElMessage.success('修改成功')
    showEditDialog.value = false
    loadData()
  } finally { editLoading.value = false }
}

const handleLock = async (row) => {
  await request.put(`/receivables/${row.id}/lock`)
  ElMessage.success('锁定成功')
  loadData()
}

const handleUnlock = async (row) => {
  await request.put(`/receivables/${row.id}/unlock`)
  ElMessage.success('解锁成功')
  loadData()
}

const handleNotice = async (row) => {
  try {
    const res = await request.get(`/receivables/${row.id}/notice`)
    const printWindow = window.open('', '_blank')
    printWindow.document.write(res.data)
    printWindow.document.close()
    printWindow.print()
  } catch (e) {
    ElMessage.error('生成通知单失败')
  }
}

onMounted(() => loadData())
</script>
