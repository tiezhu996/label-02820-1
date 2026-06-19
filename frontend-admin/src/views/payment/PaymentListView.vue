<template>
  <div class="page-container">
    <div class="search-area">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="缴费方式">
          <el-select v-model="searchForm.paymentMethod" placeholder="请选择" style="width: 150px;">
            <el-option v-for="m in paymentMethods" :key="m.id" :label="m.methodName" :value="m.methodName" />
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
        <el-button type="primary" @click="showPayDialog = true">缴费</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="paymentNo" label="缴费单号" min-width="200" />
        <el-table-column prop="ownerName" label="业主" min-width="100" />
        <el-table-column prop="amount" label="缴费金额" min-width="100" />
        <el-table-column prop="discountRate" label="优惠比例(%)" min-width="100" />
        <el-table-column prop="actualAmount" label="实际金额" min-width="100" />
        <el-table-column prop="paymentMethod" label="缴费方式" min-width="100" />
        <el-table-column prop="paymentPeriod" label="缴费区间" min-width="100">
          <template #default="{ row }">{{ periodText(row.paymentPeriod) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" min-width="100" />
        <el-table-column prop="createTime" label="缴费时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
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
    
    <el-dialog v-model="showPayDialog" title="缴费" width="500px" destroy-on-close>
      <el-form ref="payFormRef" :model="payForm" :rules="payRules" label-width="100px">
        <el-form-item label="选择账单" prop="billId">
          <el-select v-model="payForm.billId" placeholder="请选择账单" filterable style="width: 100%" @change="handleBillChange">
            <el-option
              v-for="bill in unpaidBills"
              :key="bill.id"
              :label="`${bill.billNo} - ${bill.ownerName} - ¥${bill.amount}`"
              :value="bill.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="缴费金额" prop="amount">
          <el-input-number v-model="payForm.amount" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="优惠比例(%)">
          <el-input-number v-model="payForm.discountRate" :precision="2" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="缴费方式" prop="paymentMethod">
          <el-select v-model="payForm.paymentMethod" placeholder="请选择" style="width: 100%">
            <el-option v-for="m in paymentMethods" :key="m.id" :label="m.methodName" :value="m.methodName" />
          </el-select>
        </el-form-item>
        <el-form-item label="缴费区间">
          <el-select v-model="payForm.paymentPeriod" placeholder="请选择" style="width: 100%">
            <el-option label="月度" value="MONTHLY" />
            <el-option label="季度" value="QUARTERLY" />
            <el-option label="年度" value="YEARLY" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPayDialog = false">取消</el-button>
        <el-button type="primary" :loading="payLoading" @click="handlePay">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { formatDateTime } from '@/utils'

const loading = ref(false)
const payLoading = ref(false)
const tableData = ref([])
const unpaidBills = ref([])
const paymentMethods = ref([])
const showPayDialog = ref(false)
const payFormRef = ref(null)

const searchForm = reactive({ paymentMethod: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const payForm = reactive({ billId: null, amount: 0, discountRate: 0, paymentMethod: '', paymentPeriod: 'MONTHLY' })
const payRules = {
  billId: [{ required: true, message: '请选择账单', trigger: 'change' }],
  amount: [{ required: true, message: '请输入缴费金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择缴费方式', trigger: 'change' }]
}

const periodText = (p) => ({ MONTHLY: '月度', QUARTERLY: '季度', YEARLY: '年度' }[p] || p || '-')

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/payments', { params: { page: pagination.page, size: pagination.size, ...searchForm } })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const loadUnpaidBills = async () => {
  const res = await request.get('/bills', { params: { status: 'UNPAID', size: 1000 } })
  unpaidBills.value = res.data.records
}

const loadPaymentMethods = async () => {
  const res = await request.get('/fee-standards/payment-methods')
  paymentMethods.value = res.data || []
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { searchForm.paymentMethod = ''; handleSearch() }

const handleBillChange = (billId) => {
  const bill = unpaidBills.value.find(b => b.id === billId)
  if (bill) {
    payForm.amount = bill.amount - bill.paidAmount
  }
}

const handlePay = async () => {
  const valid = await payFormRef.value.validate().catch(() => false)
  if (!valid) return
  
  payLoading.value = true
  try {
    await request.post('/payments', payForm)
    ElMessage.success('缴费成功')
    showPayDialog.value = false
    loadData()
    loadUnpaidBills()
  } finally {
    payLoading.value = false
  }
}

onMounted(() => { loadData(); loadUnpaidBills(); loadPaymentMethods() })
</script>

<style lang="scss" scoped>
.table-toolbar { margin-bottom: 16px; }
</style>
