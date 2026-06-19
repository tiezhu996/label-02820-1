<template>
  <div class="page-container">
    <div class="table-area">
      <div class="table-toolbar">
        <el-button type="primary" @click="showUploadDialog = true">上传模板</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="templateType" label="模板类型" min-width="120">
          <template #default="{ row }">{{ typeText(row.templateType) }}</template>
        </el-table-column>
        <el-table-column prop="templateName" label="模板名称" min-width="200" />
        <el-table-column prop="createTime" label="上传时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePreview(row)">预览</el-button>
            <el-button type="success" link @click="handleDownload(row)">下载</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <el-dialog v-model="showUploadDialog" title="上传模板" width="500px">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="模板类型">
          <el-select v-model="uploadForm.templateType" style="width: 100%">
            <el-option label="催缴通知单" value="REMINDER" />
            <el-option label="违规通知单" value="VIOLATION" />
            <el-option label="其他通知" value="NOTICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板文件">
          <el-upload ref="uploadRef" :auto-upload="false" :limit="1" @change="handleFileChange">
            <el-button type="primary">选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploadLoading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showPreviewDialog" title="选择业主预览" width="500px">
      <el-form label-width="100px">
        <el-form-item label="选择业主">
          <el-select v-model="previewOwnerId" placeholder="请选择业主" filterable style="width: 100%">
            <el-option v-for="owner in ownerList" :key="owner.id" 
              :label="`${owner.name} (${owner.buildingNo}-${owner.unitNo}-${owner.roomNo})`" :value="owner.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPreviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="previewLoading" @click="doPreview">预览</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showPrintDialog" title="通知单预览" width="900px">
      <div v-html="previewHtml" class="print-preview"></div>
      <template #footer>
        <el-button @click="showPrintDialog = false">关闭</el-button>
        <el-button type="primary" @click="doPrint">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { getAllOwners } from '@/api/owner'
import { formatDateTime } from '@/utils'

const loading = ref(false)
const uploadLoading = ref(false)
const previewLoading = ref(false)
const tableData = ref([])
const ownerList = ref([])
const showUploadDialog = ref(false)
const showPreviewDialog = ref(false)
const showPrintDialog = ref(false)
const uploadFile = ref(null)
const uploadForm = reactive({ templateType: 'REMINDER' })
const currentTemplate = ref(null)
const previewOwnerId = ref(null)
const previewHtml = ref('')

const typeText = (t) => ({ REMINDER: '催缴通知单', VIOLATION: '违规通知单', NOTICE: '其他通知' }[t] || t)

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/templates')
    tableData.value = res.data || []
  } finally { loading.value = false }
}

const loadOwners = async () => {
  const res = await getAllOwners()
  ownerList.value = res.data || []
}

const handleFileChange = (file) => { uploadFile.value = file.raw }

const handleUpload = async () => {
  if (!uploadFile.value) { ElMessage.warning('请选择文件'); return }
  uploadLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    formData.append('templateType', uploadForm.templateType)
    await request.post('/templates', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    loadData()
  } finally { uploadLoading.value = false }
}

const handleDownload = async (row) => {
  try {
    const res = await request.get(`/templates/${row.id}/download`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.download = row.templateName
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  await request.delete(`/templates/${row.id}`)
  ElMessage.success('删除成功')
  loadData()
}

const handlePreview = (row) => {
  currentTemplate.value = row
  previewOwnerId.value = null
  showPreviewDialog.value = true
}

const doPreview = async () => {
  if (!previewOwnerId.value) { ElMessage.warning('请选择业主'); return }
  previewLoading.value = true
  try {
    const res = await request.get(`/templates/${currentTemplate.value.id}/preview`, { params: { ownerId: previewOwnerId.value } })
    previewHtml.value = res.data
    showPreviewDialog.value = false
    showPrintDialog.value = true
  } finally { previewLoading.value = false }
}

const doPrint = () => {
  const printWindow = window.open('', '_blank')
  printWindow.document.write(`<html><head><title>打印通知单</title></head><body>${previewHtml.value}</body></html>`)
  printWindow.document.close()
  printWindow.print()
}

onMounted(() => { loadData(); loadOwners() })
</script>

<style lang="scss" scoped>
.table-toolbar { margin-bottom: 16px; }
.print-preview { padding: 20px; border: 1px solid #eee; min-height: 400px; }
</style>
