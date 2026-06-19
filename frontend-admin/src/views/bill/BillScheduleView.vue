<template>
  <div class="page-container">
    <div class="table-area">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd" v-if="isAdmin">新增调度配置</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="feeType" label="费用类型" min-width="120">
          <template #default="{ row }">{{ feeTypeText(row) }}</template>
        </el-table-column>
        <el-table-column prop="generateDay" label="生成日期" min-width="100">
          <template #default="{ row }">每月{{ row.generateDay }}日</template>
        </el-table-column>
        <el-table-column prop="periodType" label="账单周期" min-width="100">
          <template #default="{ row }">{{ periodTypeText(row.periodType) }}</template>
        </el-table-column>
        <el-table-column prop="dueDays" label="缴费期限" min-width="100">
          <template #default="{ row }">{{ row.dueDays }}天</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
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
          <el-select v-model="form.feeType" style="width: 100%" @change="onFeeTypeChange">
            <el-option label="物业费" value="PROPERTY" />
            <el-option label="车位费" value="PARKING" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.feeType === 'CUSTOM'" label="费用名称" prop="customFeeType">
          <el-input v-model="form.customFeeType" placeholder="请输入自定义费用类型名称" />
        </el-form-item>
        <el-form-item label="生成日期" prop="generateDay">
          <el-input-number v-model="form.generateDay" :min="1" :max="28" style="width: 100%" />
          <div class="form-tip">每月几号自动生成账单（1-28日）</div>
        </el-form-item>
        <el-form-item label="账单周期" prop="periodType">
          <el-select v-model="form.periodType" style="width: 100%">
            <el-option label="按月" value="MONTHLY" />
            <el-option label="按季度" value="QUARTERLY" />
            <el-option label="按年" value="YEARLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="缴费期限" prop="dueDays">
          <el-input-number v-model="form.dueDays" :min="1" :max="90" style="width: 100%" />
          <div class="form-tip">账单周期结束后多少天内需缴费</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
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
const showDialog = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)

const form = reactive({ id: null, feeType: '', customFeeType: '', generateDay: 1, periodType: 'MONTHLY', dueDays: 15, status: 1 })
const rules = {
  feeType: [{ required: true, message: '请选择', trigger: 'change' }],
  customFeeType: [{ required: true, message: '请输入自定义费用类型名称', trigger: 'blur' }],
  generateDay: [{ required: true, message: '请输入', trigger: 'blur' }],
  periodType: [{ required: true, message: '请选择', trigger: 'change' }],
  dueDays: [{ required: true, message: '请输入', trigger: 'blur' }]
}

const feeTypeText = (row) => {
  if (typeof row === 'string') return ({ PROPERTY: '物业费', PARKING: '车位费' }[row] || row)
  if (row.feeType === 'CUSTOM') return row.customFeeType || '自定义'
  return { PROPERTY: '物业费', PARKING: '车位费' }[row.feeType] || row.feeType
}
const periodTypeText = (t) => ({ MONTHLY: '按月', QUARTERLY: '按季度', YEARLY: '按年' }[t] || t)

const onFeeTypeChange = (val) => {
  if (val !== 'CUSTOM') form.customFeeType = ''
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/bill-schedules')
    tableData.value = res.data
  } finally { loading.value = false }
}

const handleAdd = () => {
  dialogTitle.value = '新增调度配置'
  Object.assign(form, { id: null, feeType: '', customFeeType: '', generateDay: 1, periodType: 'MONTHLY', dueDays: 15, status: 1 })
  showDialog.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑调度配置'
  form.id = row.id
  form.feeType = row.feeType
  form.customFeeType = row.customFeeType || ''
  form.generateDay = row.generateDay
  form.periodType = row.periodType
  form.dueDays = row.dueDays
  form.status = row.status
  showDialog.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.id) {
      await request.put(`/bill-schedules/${form.id}`, form)
    } else {
      await request.post('/bill-schedules', form)
    }
    ElMessage.success('操作成功')
    showDialog.value = false
    loadData()
  } finally { submitLoading.value = false }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  await request.delete(`/bill-schedules/${row.id}`)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => loadData())
</script>

<style lang="scss" scoped>
.table-toolbar { margin-bottom: 16px; }
.form-tip { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
</style>
