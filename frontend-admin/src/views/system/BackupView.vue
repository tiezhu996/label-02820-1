<template>
  <div class="page-container">
    <div class="card">
      <div class="card-header">
        <span class="title">数据备份与恢复</span>
      </div>
      <div style="display: flex; gap: 20px;">
        <el-button type="primary" size="large" :loading="backupLoading" @click="handleBackup">
          <el-icon><Download /></el-icon>
          数据备份
        </el-button>
        <el-upload :show-file-list="false" :before-upload="handleRestore" accept=".sql">
          <el-button type="warning" size="large" :loading="restoreLoading">
            <el-icon><Upload /></el-icon>
            数据恢复
          </el-button>
        </el-upload>
        <el-button type="danger" size="large" @click="handleInit">
          <el-icon><RefreshRight /></el-icon>
          系统初始化
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const backupLoading = ref(false)
const restoreLoading = ref(false)

const handleBackup = async () => {
  backupLoading.value = true
  try {
    const res = await request.post('/backup/export', {}, { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.download = `backup_${new Date().toISOString().slice(0, 10)}.sql`
    link.click()
    ElMessage.success('备份成功')
  } finally { backupLoading.value = false }
}

const handleRestore = async (file) => {
  await ElMessageBox.confirm('恢复数据将覆盖现有数据，确定继续？', '警告', { type: 'warning' })
  restoreLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    await request.post('/backup/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    ElMessage.success('恢复成功')
  } finally { restoreLoading.value = false }
  return false
}

const handleInit = async () => {
  await ElMessageBox.confirm('初始化将清除所有数据，此操作不可恢复！确定继续？', '危险操作', { type: 'error' })
  await request.post('/backup/init')
  ElMessage.success('初始化成功')
}
</script>
