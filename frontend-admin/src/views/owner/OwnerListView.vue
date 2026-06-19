<template>
  <div class="page-container">
    <div class="search-area">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="业主姓名">
          <el-input v-model="searchForm.name" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="楼栋号">
          <el-input v-model="searchForm.buildingNo" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="单元号">
          <el-input v-model="searchForm.unitNo" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="房间号">
          <el-input v-model="searchForm.roomNo" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" style="width: 120px;">
            <el-option label="已入住" value="OCCUPIED" />
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
        <el-button type="primary" @click="handleAdd" v-if="isAdmin">新增业主</el-button>
        <el-button type="success" @click="showImportDialog = true" v-if="isAdmin">Excel导入</el-button>
        <el-button type="info" @click="handleDownloadTemplate" v-if="isAdmin">下载模板</el-button>
      </div>
      
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="name" label="业主姓名" min-width="100" />
        <el-table-column prop="buildingNo" label="楼栋号" min-width="80" />
        <el-table-column prop="unitNo" label="单元号" min-width="80" />
        <el-table-column prop="roomNo" label="房间号" min-width="80" />
        <el-table-column prop="phone" label="电话" min-width="120" />
        <el-table-column prop="area" label="面积(㎡)" min-width="100" />
        <el-table-column prop="moveInDate" label="入住时间" min-width="120">
          <template #default="{ row }">{{ formatDateValue(row.moveInDate) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OCCUPIED' ? 'success' : 'info'">
              {{ row.status === 'OCCUPIED' ? '已入住' : '空置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" v-if="isAdmin">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>
    
    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="业主姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入业主姓名" />
        </el-form-item>
        <el-form-item label="楼栋号" prop="buildingNo">
          <el-input v-model="form.buildingNo" placeholder="请输入楼栋号" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="单元号" prop="unitNo">
          <el-input v-model="form.unitNo" placeholder="请输入单元号" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="房间号" prop="roomNo">
          <el-input v-model="form.roomNo" placeholder="请输入房间号" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="房屋面积" prop="area">
          <el-input-number v-model="form.area" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="入住时间" prop="moveInDate">
          <el-date-picker v-model="form.moveInDate" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
            <el-option label="已入住" value="OCCUPIED" />
            <el-option label="空置" value="VACANT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
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
          <div class="el-upload__tip">只能上传 xlsx/xls 文件</div>
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
import { getOwnerList, createOwner, updateOwner, deleteOwner, importOwners, downloadTemplate } from '@/api/owner'
import { useAuthStore } from '@/store/auth'
import { formatDate as formatDateValue } from '@/utils'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const submitLoading = ref(false)
const importLoading = ref(false)
const tableData = ref([])
const showDialog = ref(false)
const showImportDialog = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const uploadRef = ref(null)
const importFile = ref(null)

const searchForm = reactive({
  name: '',
  buildingNo: '',
  unitNo: '',
  roomNo: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const form = reactive({
  id: null,
  name: '',
  buildingNo: '',
  unitNo: '',
  roomNo: '',
  phone: '',
  area: 0,
  moveInDate: null,
  status: 'VACANT'
})

const rules = {
  name: [{ required: true, message: '请输入业主姓名', trigger: 'blur' }],
  buildingNo: [{ required: true, message: '请输入楼栋号', trigger: 'blur' }],
  unitNo: [{ required: true, message: '请输入单元号', trigger: 'blur' }],
  roomNo: [{ required: true, message: '请输入房间号', trigger: 'blur' }],
  area: [{ required: true, message: '请输入房屋面积', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getOwnerList({
      page: pagination.page,
      size: pagination.size,
      ...searchForm
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { name: '', buildingNo: '', unitNo: '', roomNo: '', status: '' })
  handleSearch()
}

const resetForm = () => {
  form.id = null
  form.name = ''
  form.buildingNo = ''
  form.unitNo = ''
  form.roomNo = ''
  form.phone = ''
  form.area = 0
  form.moveInDate = null
  form.status = 'VACANT'
}

const handleAdd = () => {
  dialogTitle.value = '新增业主'
  resetForm()
  showDialog.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑业主'
  resetForm()
  form.id = row.id
  form.name = row.name
  form.buildingNo = row.buildingNo
  form.unitNo = row.unitNo
  form.roomNo = row.roomNo
  form.phone = row.phone
  form.area = row.area
  form.moveInDate = row.moveInDate
  form.status = row.status
  showDialog.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  
  submitLoading.value = true
  try {
    const data = { ...form, moveInDate: formatDateValue(form.moveInDate) || null }
    if (form.id) {
      await updateOwner(form.id, data)
      ElMessage.success('更新成功')
    } else {
      const { id, ...createData } = data
      await createOwner(createData)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定要删除该业主吗？', '提示', { type: 'warning' })
  await deleteOwner(row.id)
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
    const res = await importOwners(importFile.value)
    ElMessage.success(`导入成功 ${res.data.successCount} 条`)
    if (res.data.errors?.length > 0) {
      ElMessage.warning(`有 ${res.data.errors.length} 条数据导入失败`)
    }
    showImportDialog.value = false
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
    link.download = '业主导入模板.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style lang="scss" scoped>
.table-toolbar {
  margin-bottom: 16px;
}
</style>
