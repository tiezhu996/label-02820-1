<template>
  <div class="page-container">
    <div class="table-area">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增账套</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="name" label="账套名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="createTime" label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-if="row.id !== 1">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="账套名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { useAccountSetStore } from '@/store/accountSet'
import { formatDateTime } from '@/utils'

const accountSetStore = useAccountSetStore()
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const showDialog = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const form = reactive({ id: null, name: '', description: '' })
const rules = { name: [{ required: true, message: '请输入', trigger: 'blur' }] }

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/account-sets')
    tableData.value = res.data
  } finally { loading.value = false }
}

const handleAdd = () => {
  dialogTitle.value = '新增账套'
  Object.assign(form, { id: null, name: '', description: '' })
  showDialog.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑账套'
  form.id = row.id
  form.name = row.name
  form.description = row.description
  showDialog.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.id) {
      await request.put(`/account-sets/${form.id}`, form)
    } else {
      await request.post('/account-sets', form)
    }
    ElMessage.success('操作成功')
    showDialog.value = false
    loadData()
    // 刷新顶部账套下拉列表
    accountSetStore.fetchAccountSetList()
  } finally { submitLoading.value = false }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  await request.delete(`/account-sets/${row.id}`)
  ElMessage.success('删除成功')
  loadData()
  // 刷新顶部账套下拉列表
  accountSetStore.fetchAccountSetList()
}

onMounted(() => loadData())
</script>

<style lang="scss" scoped>
.table-toolbar { margin-bottom: 16px; }
</style>
