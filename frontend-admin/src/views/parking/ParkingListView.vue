<template>
  <div class="page-container">
    <div class="search-area">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="车位号">
          <el-input v-model="searchForm.parkingNo" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" style="width: 120px;">
            <el-option label="已使用" value="USED" />
            <el-option label="空置" value="VACANT" />
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
        <el-button type="primary" @click="handleAdd" v-if="isAdmin">新增车位</el-button>
        <el-button type="success" @click="showImportDialog = true" v-if="isAdmin">Excel导入</el-button>
        <el-button type="info" @click="handleDownloadTemplate" v-if="isAdmin">下载模板</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="parkingNo" label="车位号" min-width="150" />
        <el-table-column prop="ownerName" label="绑定业主" min-width="120" />
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'USED' ? 'success' : 'info'">
              {{ row.status === 'USED' ? '已使用' : '空置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" v-if="isAdmin">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleBind(row)" v-if="!row.ownerId">绑定业主</el-button>
            <el-button type="warning" link @click="handleUnbind(row)" v-if="row.ownerId">解绑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
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
    
    <el-dialog v-model="showAddDialog" title="新增车位" width="400px" destroy-on-close>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item label="车位号" prop="parkingNo">
          <el-input v-model="addForm.parkingNo" placeholder="请输入车位号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleAddSubmit">确定</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showBindDialog" title="绑定业主" width="400px">
      <el-form label-width="80px">
        <el-form-item label="选择业主">
          <el-select v-model="bindOwnerId" placeholder="请选择业主" filterable style="width: 100%">
            <el-option
              v-for="owner in ownerList"
              :key="owner.id"
              :label="`${owner.name} (${owner.buildingNo}-${owner.unitNo}-${owner.roomNo})`"
              :value="owner.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBindDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleBindSubmit">确定</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showImportDialog" title="Excel导入" width="500px">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">只能上传 xlsx/xls 文件，第一列为车位号</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import request from '@/api/request'
import { getAllOwners } from '@/api/owner'
import { importParkings, downloadTemplate } from '@/api/parking'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const submitLoading = ref(false)
const importLoading = ref(false)
const tableData = ref([])
const ownerList = ref([])
const showAddDialog = ref(false)
const showBindDialog = ref(false)
const showImportDialog = ref(false)
const currentParking = ref(null)
const bindOwnerId = ref(null)
const addFormRef = ref(null)
const uploadRef = ref(null)
const importFile = ref(null)

const searchForm = reactive({ parkingNo: '', status: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const addForm = reactive({ parkingNo: '' })
const addRules = { parkingNo: [{ required: true, message: '请输入车位号', trigger: 'blur' }] }

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/parkings', { params: { page: pagination.page, size: pagination.size, ...searchForm } })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const loadOwners = async () => {
  const res = await getAllOwners()
  ownerList.value = res.data
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { parkingNo: '', status: '' }); handleSearch() }

const handleAdd = () => { addForm.parkingNo = ''; showAddDialog.value = true }

const handleAddSubmit = async () => {
  const valid = await addFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await request.post('/parkings', addForm)
    ElMessage.success('创建成功')
    showAddDialog.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

const handleBind = (row) => {
  currentParking.value = row
  bindOwnerId.value = row.ownerId
  showBindDialog.value = true
}

const handleBindSubmit = async () => {
  if (!bindOwnerId.value) { ElMessage.warning('请选择业主'); return }
  submitLoading.value = true
  try {
    await request.put(`/parkings/${currentParking.value.id}/bind`, { ownerId: bindOwnerId.value })
    ElMessage.success('绑定成功')
    showBindDialog.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

const handleUnbind = async (row) => {
  await ElMessageBox.confirm('确定要解绑该车位吗？', '提示', { type: 'warning' })
  await request.put(`/parkings/${row.id}/unbind`)
  ElMessage.success('解绑成功')
  loadData()
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定要删除该车位吗？', '提示', { type: 'warning' })
  await request.delete(`/parkings/${row.id}`)
  ElMessage.success('删除成功')
  loadData()
}

const handleFileChange = (file) => {
  importFile.value = file.raw
}

const handleImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  
  importLoading.value = true
  try {
    const res = await importParkings(importFile.value)
    ElMessage.success(`导入成功 ${res.data.successCount} 条`)
    if (res.data.errors?.length > 0) {
      ElMessage.warning(`有 ${res.data.errors.length} 条数据导入失败`)
    }
    showImportDialog.value = false
    importFile.value = null
    loadData()
  } finally {
    importLoading.value = false
  }
}

const handleDownloadTemplate = async () => {
  try {
    const res = await downloadTemplate()
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.download = '车位导入模板.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

onMounted(() => { loadData(); loadOwners() })
</script>

<style lang="scss" scoped>
.table-toolbar { margin-bottom: 16px; }
</style>
