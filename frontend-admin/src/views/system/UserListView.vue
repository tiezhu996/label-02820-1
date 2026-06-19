<template>
  <div class="page-container">
    <div class="table-area">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增用户</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="真实姓名" min-width="120" />
        <el-table-column prop="role" label="角色" min-width="100">
          <template #default="{ row }">{{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)" v-if="row.username !== 'admin'">编辑</el-button>
            <el-button type="success" link @click="handlePermission(row)" v-if="row.role === 'USER'">权限</el-button>
            <el-button type="warning" link @click="handleResetPwd(row)" v-if="row.username !== 'admin'">重置密码</el-button>
            <el-button type="danger" link @click="handleDelete(row)" v-if="row.username !== 'admin'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size" :total="pagination.total" layout="total, prev, pager, next" @current-change="loadData" />
      </div>
    </div>
    
    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!form.id">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
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
    
    <el-dialog v-model="showPermissionDialog" title="权限配置" width="500px">
      <el-form label-width="100px">
        <el-form-item label="用户">{{ permissionForm.realName || permissionForm.username }}</el-form-item>
        <el-form-item label="功能权限">
          <el-checkbox-group v-model="permissionForm.permissions">
            <el-checkbox label="owner:view">业主查看</el-checkbox>
            <el-checkbox label="owner:edit">业主编辑</el-checkbox>
            <el-checkbox label="parking:view">车位查看</el-checkbox>
            <el-checkbox label="parking:edit">车位编辑</el-checkbox>
            <el-checkbox label="bill:view">账单查看</el-checkbox>
            <el-checkbox label="bill:edit">账单编辑</el-checkbox>
            <el-checkbox label="payment:view">缴费查看</el-checkbox>
            <el-checkbox label="payment:edit">缴费操作</el-checkbox>
            <el-checkbox label="statistics:view">统计查看</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPermissionDialog = false">取消</el-button>
        <el-button type="primary" :loading="permissionLoading" @click="handleSavePermission">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { formatDateTime } from '@/utils'

const loading = ref(false)
const submitLoading = ref(false)
const permissionLoading = ref(false)
const tableData = ref([])
const showDialog = ref(false)
const showPermissionDialog = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const pagination = reactive({ page: 1, size: 10, total: 0 })
const form = reactive({ id: null, username: '', password: '', realName: '', role: 'USER', status: 1 })
const permissionForm = reactive({ id: null, username: '', realName: '', permissions: [] })
const rules = {
  username: [{ required: true, message: '请输入', trigger: 'blur' }],
  password: [{ required: true, message: '请输入', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/users', { params: { page: pagination.page, size: pagination.size } })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally { loading.value = false }
}

const handleAdd = () => {
  dialogTitle.value = '新增用户'
  Object.assign(form, { id: null, username: '', password: '', realName: '', role: 'USER', status: 1 })
  showDialog.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑用户'
  form.id = row.id
  form.username = row.username
  form.password = ''
  form.realName = row.realName
  form.role = row.role
  form.status = row.status
  showDialog.value = true
}

const handlePermission = (row) => {
  Object.assign(permissionForm, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    permissions: row.permissions ? row.permissions.split(',').filter(p => p) : []
  })
  showPermissionDialog.value = true
}

const handleSavePermission = async () => {
  permissionLoading.value = true
  try {
    await request.put(`/users/${permissionForm.id}/permissions`, permissionForm.permissions)
    ElMessage.success('权限保存成功')
    showPermissionDialog.value = false
    loadData()
  } finally { permissionLoading.value = false }
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.id) {
      const updateData = {
        username: form.username,
        realName: form.realName,
        role: form.role,
        status: form.status
      }
      await request.put(`/users/${form.id}`, updateData)
    } else {
      await request.post('/users', form)
    }
    ElMessage.success('操作成功')
    showDialog.value = false
    loadData()
  } finally { submitLoading.value = false }
}

const handleResetPwd = async (row) => {
  const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', { inputType: 'password' })
  if (value) {
    await request.put(`/users/${row.id}/password`, value, { headers: { 'Content-Type': 'text/plain' } })
    ElMessage.success('重置成功')
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  await request.delete(`/users/${row.id}`)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => loadData())
</script>

<style lang="scss" scoped>
.table-toolbar { margin-bottom: 16px; }
</style>
