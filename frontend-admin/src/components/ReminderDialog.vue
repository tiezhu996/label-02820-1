<template>
  <el-dialog v-model="visible" title="批量生成催缴单" width="800px" destroy-on-close @close="handleClose">
    <div v-loading="loading">
      <el-form label-width="100px">
        <el-form-item label="选择模板">
          <el-select v-model="selectedTemplateId" placeholder="请选择催缴模板" style="width: 100%;">
            <el-option 
              v-for="tpl in templates" 
              :key="tpl.id" 
              :label="tpl.templateName" 
              :value="tpl.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="已选业主">
          <span>共 {{ ownerIds.length }} 户欠费业主</span>
        </el-form-item>
      </el-form>
      
      <div v-if="previewData" style="margin-top: 20px;">
        <el-divider>预览 ({{ previewData.count }} 份)</el-divider>
        <el-table :data="previewData.items" max-height="400" border size="small">
          <el-table-column prop="roomInfo" label="房间" width="120" />
          <el-table-column prop="ownerName" label="业主" width="100" />
          <el-table-column prop="totalArrears" label="欠费金额" width="120">
            <template #default="{ row }">￥{{ row.totalArrears || 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="primary" link @click="viewItem(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="info" :disabled="!selectedTemplateId || ownerIds.length === 0" @click="handlePreview">预览</el-button>
      <el-button type="primary" :disabled="!selectedTemplateId || ownerIds.length === 0" @click="handleGenerate">生成并下载</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTemplates, batchPreviewReminders, batchGenerateReminders } from '@/api/template'

const props = defineProps({
  modelValue: Boolean,
  ownerIds: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(props.modelValue)
const loading = ref(false)
const templates = ref([])
const selectedTemplateId = ref(null)
const previewData = ref(null)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    loadTemplates()
    previewData.value = null
    selectedTemplateId.value = null
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const loadTemplates = async () => {
  try {
    const res = await getTemplates({ templateType: 'REMINDER' })
    templates.value = (res.data || []).filter(t => t.templateType === 'REMINDER')
  } catch (e) {
    ElMessage.error('加载模板失败')
  }
}

const handlePreview = async () => {
  if (!selectedTemplateId.value || props.ownerIds.length === 0) return
  loading.value = true
  try {
    const res = await batchPreviewReminders({
      ownerIds: props.ownerIds,
      templateId: selectedTemplateId.value
    })
    previewData.value = res.data
  } catch (e) {
    ElMessage.error('预览失败')
  } finally {
    loading.value = false
  }
}

const viewItem = (item) => {
  const printWindow = window.open('', '_blank')
  printWindow.document.write(item.html)
  printWindow.document.close()
}

const handleGenerate = async () => {
  if (!selectedTemplateId.value || props.ownerIds.length === 0) return
  loading.value = true
  try {
    const res = await batchGenerateReminders({
      ownerIds: props.ownerIds,
      templateId: selectedTemplateId.value
    })
    const url = window.URL.createObjectURL(new Blob([res], { type: 'text/html' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `批量催缴单_${new Date().toISOString().slice(0, 10)}.html`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('生成成功')
    visible.value = false
  } catch (e) {
    ElMessage.error('生成失败')
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  previewData.value = null
  selectedTemplateId.value = null
}
</script>
