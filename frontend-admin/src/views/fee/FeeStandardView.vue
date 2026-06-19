<template>
  <div class="page-container">
    <div class="table-area">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd" v-if="isAdmin">新增收费标准</el-button>
        <el-button type="success" @click="showMethodDialog = true" v-if="isAdmin">缴费方式管理</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="feeType" label="费用类型" min-width="120">
          <template #default="{ row }">{{ feeTypeText(row.feeType) }}</template>
        </el-table-column>
        <el-table-column prop="feeName" label="费用名称" min-width="150" />
        <el-table-column prop="amount" label="金额" min-width="100" />
        <el-table-column prop="unit" label="单位" min-width="150" />
        <el-table-column prop="frequency" label="收费频次" min-width="100">
          <template #default="{ row }">{{ frequencyText(row.frequency) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" v-if="isAdmin">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="费用类型" prop="feeType">
          <el-select v-model="form.feeType" style="width: 100%">
            <el-option label="物业费" value="PROPERTY" />
            <el-option label="车位费" value="PARKING" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用名称" prop="feeName">
          <el-input v-model="form.feeName" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" placeholder="如：元/平方米/月" />
        </el-form-item>
        <el-form-item label="收费频次" prop="frequency">
          <el-select v-model="form.frequency" style="width: 100%">
            <el-option label="按月" value="MONTHLY" />
            <el-option label="按季度" value="QUARTERLY" />
            <el-option label="按年" value="YEARLY" />
            <el-option label="一次性" value="ONETIME" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showMethodDialog" title="缴费方式管理" width="500px">
      <div class="method-list">
        <div v-for="m in paymentMethods" :key="m.id" class="method-item">
          <span>{{ m.methodName }}</span>
          <el-button type="danger" link @click="handleDeleteMethod(m)">删除</el-button>
        </div>
      </div>
      <el-form :inline="true" style="margin-top: 16px;">
        <el-form-item>
          <el-input v-model="newMethodName" placeholder="新增缴费方式" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleAddMethod">添加</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const paymentMethods = ref([])
const showDialog = ref(false)
const showMethodDialog = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const newMethodName = ref('')

const form = reactive({ id: null, feeType: '', feeName: '', amount: 0, unit: '', frequency: 'MONTHLY' })
const rules = {
  feeType: [{ required: true, message: '请选择', trigger: 'change' }],
  feeName: [{ required: true, message: '请输入', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入', trigger: 'blur' }],
  frequency: [{ required: true, message: '请选择', trigger: 'change' }]
}

const feeTypeText = (t) => ({ PROPERTY: '物业费', PARKING: '车位费', CUSTOM: '自定义' }[t] || t)
const frequencyText = (f) => ({ MONTHLY: '按月', QUARTERLY: '按季度', YEARLY: '按年', ONETIME: '一次性' }[f] || f)

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/fee-standards')
    tableData.value = res.data
  } finally { loading.value = false }
}

const loadPaymentMethods = async () => {
  const res = await request.get('/fee-standards/payment-methods')
  paymentMethods.value = res.data || []
}

const handleAdd = () => {
  dialogTitle.value = '新增'
  Object.assign(form, { id: null, feeType: '', feeName: '', amount: 0, unit: '', frequency: 'MONTHLY' })
  showDialog.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑'
  form.id = row.id
  form.feeType = row.feeType
  form.feeName = row.feeName
  form.amount = row.amount
  form.unit = row.unit
  form.frequency = row.frequency
  showDialog.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.id) {
      await request.put(`/fee-standards/${form.id}`, form)
    } else {
      await request.post('/fee-standards', form)
    }
    ElMessage.success('操作成功')
    showDialog.value = false
    loadData()
  } finally { submitLoading.value = false }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  await request.delete(`/fee-standards/${row.id}`)
  ElMessage.success('删除成功')
  loadData()
}

const handleAddMethod = async () => {
  if (!newMethodName.value.trim()) return
  await request.post('/fee-standards/payment-methods', { methodName: newMethodName.value })
  ElMessage.success('添加成功')
  newMethodName.value = ''
  loadPaymentMethods()
}

const handleDeleteMethod = async (m) => {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  await request.delete(`/fee-standards/payment-methods/${m.id}`)
  ElMessage.success('删除成功')
  loadPaymentMethods()
}

onMounted(() => { loadData(); loadPaymentMethods() })
</script>

<style lang="scss" scoped>
.table-toolbar { margin-bottom: 16px; display: flex; gap: 12px; }
.method-list { max-height: 300px; overflow-y: auto; }
.method-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--border-color); }
</style>
