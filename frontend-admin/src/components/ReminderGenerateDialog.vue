<template>
  <el-dialog
    v-model="visible"
    title="生成催缴单"
    width="600px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <div v-loading="loading">
      <el-alert
        :title="`已选择 ${selectedOwners.length} 位欠费业主`"
        type="info"
        :closable="false"
        style="margin-bottom: 20px;"
      >
        <template #default>
          <div>已选择 <strong>{{ selectedOwners.length }}</strong> 位欠费业主</div>
          <div style="margin-top: 5px;">欠费总金额：<strong style="color: #f56c6c;">￥{{ totalArrearsAmount.toFixed(2) }}</strong></div>
        </template>
      </el-alert>

      <el-form label-width="100px">
        <el-form-item label="业主列表">
          <div style="max-height: 200px; overflow-y: auto; width: 100%; border: 1px solid #ebeef5; border-radius: 4px; padding: 8px;">
            <el-tag
              v-for="owner in selectedOwners"
              :key="owner.ownerId || owner.id"
              closable
              style="margin: 4px;"
              @close="removeOwner(owner)"
            >
              {{ owner.ownerName || owner.name }}
              ({{ owner.buildingNo }}-{{ owner.unitNo }}-{{ owner.roomNo }})
              - ￥{{ formatAmount(owner) }}
            </el-tag>
            <div v-if="selectedOwners.length === 0" style="color: #909399; text-align: center; padding: 20px;">
              暂无选中业主
            </div>
          </div>
        </el-form-item>

        <el-form-item label="催缴模板" required>
          <el-select v-model="selectedTemplateId" placeholder="请选择催缴模板" style="width: 100%;">
            <el-option
              v-for="tpl in templates"
              :key="tpl.id"
              :label="tpl.templateName"
              :value="tpl.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="generating" :disabled="!selectedTemplateId || selectedOwners.length === 0" @click="handleGenerate">
        生成并预览
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getReminderTemplates, batchPreviewReminders } from '@/api/template'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  owners: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const generating = ref(false)
const templates = ref([])
const selectedTemplateId = ref(null)
const selectedOwners = ref([])

const totalArrearsAmount = computed(() => {
  return selectedOwners.value.reduce((sum, owner) => {
    const amount = owner.cumulativeArrears || owner.arrearsAmount || 0
    return sum + Number(amount)
  }, 0)
})

const formatAmount = (owner) => {
  const amount = owner.cumulativeArrears || owner.arrearsAmount || 0
  return Number(amount).toFixed(2)
}

watch(() => props.modelValue, async (val) => {
  if (val) {
    selectedOwners.value = [...props.owners]
    selectedTemplateId.value = null
    loading.value = true
    try {
      const res = await getReminderTemplates()
      templates.value = res.data || []
      if (templates.value.length > 0) {
        selectedTemplateId.value = templates.value[0].id
      }
    } catch (e) {
      ElMessage.error('获取模板列表失败')
    } finally {
      loading.value = false
    }
  }
})

const removeOwner = (owner) => {
  const id = owner.ownerId || owner.id
  selectedOwners.value = selectedOwners.value.filter(o => (o.ownerId || o.id) !== id)
}

const handleClose = () => {
  selectedOwners.value = []
  selectedTemplateId.value = null
}

const handleGenerate = async () => {
  if (!selectedTemplateId.value || selectedOwners.value.length === 0) {
    ElMessage.warning('请选择模板和业主')
    return
  }

  generating.value = true
  try {
    const ownerIds = selectedOwners.value.map(o => o.ownerId || o.id)
    const res = await batchPreviewReminders({
      templateId: selectedTemplateId.value,
      ownerIds
    })

    const printWindow = window.open('', '_blank')
    printWindow.document.write(res.data)
    printWindow.document.close()
    emit('success')
    ElMessage.success('催缴单已生成')
  } catch (e) {
    ElMessage.error('生成失败')
  } finally {
    generating.value = false
  }
}
</script>
