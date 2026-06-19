<template>
  <div class="page-container">
    <div class="search-area">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="费用类型">
          <el-select v-model="searchForm.feeType" placeholder="请选择" style="width: 150px;">
            <el-option label="物业费" value="PROPERTY" />
            <el-option label="车位费" value="PARKING" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" style="width: 120px;">
            <el-option label="未缴" value="UNPAID" />
            <el-option label="已缴" value="PAID" />
            <el-option label="欠费" value="OVERDUE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <div class="table-area">
      <div class="table-toolbar">
        <el-button type="primary" @click="showGenerateDialog = true" v-if="isAdmin">生成账单</el-button>
        <el-button type="success" @click="handleExport">导出Excel</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="billNo" label="账单编号" min-width="200" />
        <el-table-column prop="ownerName" label="业主" min-width="100" />
        <el-table-column prop="roomInfo" label="房间" min-width="120" />
        <el-table-column prop="feeName" label="费用名称" min-width="120" />
        <el-table-column prop="amount" label="应收金额" min-width="100" />
        <el-table-column prop="paidAmount" label="已缴金额" min-width="100" />
        <el-table-column label="账单周期" min-width="200">
          <template #default="{ row }">{{ formatDate(row.periodStart) }} ~ {{ formatDate(row.periodEnd) }}</template>
        </el-table-column>
        <el-table-column prop="dueDate" label="截止日期" min-width="120">
          <template #default="{ row }">{{ formatDate(row.dueDate) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="80">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePrint(row)">打印</el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-if="isAdmin">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>
    
    <el-dialog v-model="showGenerateDialog" title="生成账单" width="500px">
      <el-form ref="generateFormRef" :model="generateForm" :rules="generateRules" label-width="100px">
        <el-form-item label="费用类型" prop="feeType">
          <el-select v-model="generateForm.feeType" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in feeStandards" :key="item.feeType" :label="item.feeName" :value="item.feeType" />
          </el-select>
        </el-form-item>
        <el-form-item label="账单周期" prop="period">
          <el-date-picker
            v-model="generateForm.period"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="缴费截止" prop="dueDate">
          <el-date-picker v-model="generateForm.dueDate" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" :loading="generateLoading" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showPrintDialog" title="打印预览" width="800px">
      <div v-html="printHtml" class="print-preview"></div>
      <template #footer>
        <el-button @click="showPrintDialog = false">关闭</el-button>
        <el-button type="primary" @click="doPrint">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { exportBills, getBillPrintHtml } from '@/api/bill'
import { useAuthStore } from '@/store/auth'
import { formatDate } from '@/utils'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const generateLoading = ref(false)
const tableData = ref([])
const feeStandards = ref([])
const showGenerateDialog = ref(false)
const showPrintDialog = ref(false)
const printHtml = ref('')
const generateFormRef = ref(null)

const searchForm = reactive({ feeType: '', status: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const generateForm = reactive({ feeType: '', period: [], dueDate: null })
const generateRules = {
  feeType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
  period: [{ required: true, message: '请选择账单周期', trigger: 'change' }],
  dueDate: [{ required: true, message: '请选择缴费截止日期', trigger: 'change' }]
}

const statusType = (status) => ({ UNPAID: 'warning', PAID: 'success', OVERDUE: 'danger' }[status] || 'info')
const statusText = (status) => ({ UNPAID: '未缴', PAID: '已缴', OVERDUE: '欠费' }[status] || status)

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/bills', { params: { page: pagination.page, size: pagination.size, ...searchForm } })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const loadFeeStandards = async () => {
  try {
    const res = await request.get('/fee-standards')
    feeStandards.value = res.data
  } catch (e) { /* ignore */ }
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { feeType: '', status: '' }); handleSearch() }

const handleGenerate = async () => {
  const valid = await generateFormRef.value.validate().catch(() => false)
  if (!valid) return
  
  generateLoading.value = true
  try {
    const periodStart = formatDate(generateForm.period?.[0])
    const periodEnd = formatDate(generateForm.period?.[1])
    const dueDate = formatDate(generateForm.dueDate) || periodEnd

    const data = {
      feeType: generateForm.feeType,
      periodStart,
      periodEnd,
      dueDate
    }
    const res = await request.post('/bills/generate', data)
    ElMessage.success(`成功生成 ${res.data} 条账单`)
    showGenerateDialog.value = false
    loadData()
  } finally {
    generateLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定要删除该账单吗？', '提示', { type: 'warning' })
  await request.delete(`/bills/${row.id}`)
  ElMessage.success('删除成功')
  loadData()
}

const handleExport = async () => {
  try {
    const res = await exportBills(searchForm)
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.download = '账单列表.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

const handlePrint = async (row) => {
  const res = await getBillPrintHtml(row.id)
  printHtml.value = res.data
  showPrintDialog.value = true
}

const doPrint = () => {
  const printWindow = window.open('', '_blank')
  printWindow.document.write(`<html><head><title>打印账单</title></head><body>${printHtml.value}</body></html>`)
  printWindow.document.close()
  printWindow.print()
}

onMounted(() => {
  loadData()
  loadFeeStandards()
})
</script>

<style lang="scss" scoped>
.table-toolbar { margin-bottom: 16px; }
.print-preview { padding: 20px; border: 1px solid #eee; }
</style>
