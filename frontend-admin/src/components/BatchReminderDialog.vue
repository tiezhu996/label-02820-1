<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="(val) => emit('update:modelValue', val)"
    title="批量生成催缴单"
    width="900px"
    destroy-on-close
    @open="handleOpen"
  >
    <el-form :inline="true" label-width="100px">
      <el-form-item label="催缴模板" required>
        <el-select v-model="selectedTemplateId" style="width: 280px;" placeholder="请选择催缴模板">
          <el-option
            v-for="t in reminderTemplates"
            :key="t.id"
            :label="t.templateName"
            :value="t.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="账期月份">
        <el-date-picker
          v-model="periodMonth"
          type="month"
          value-format="YYYY-MM"
          placeholder="可选，默认按外部传入日期范围"
          style="width: 220px;"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="generating" @click="handleGenerate">生成预览</el-button>
        <el-button v-if="reminders.length" type="success" @click="printAll">打印全部</el-button>
      </el-form-item>
    </el-form>

    <div class="reminder-summary" v-if="reminders.length">
      共生成 {{ reminders.length }} 份催缴单，合计欠费 ￥{{ totalArrears.toFixed(2) }}
    </div>

    <el-table v-if="reminders.length" :data="reminders" max-height="380">
      <el-table-column label="业主" prop="ownerName" min-width="120" />
      <el-table-column label="房间" prop="roomInfo" min-width="160" />
      <el-table-column label="欠费金额" prop="totalArrears" min-width="120" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="primary" link @click="previewOne(row)">预览</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTemplates, batchGenerateReminders } from '@/api/template'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ownerIds: { type: Array, default: () => [] },
  periodStart: { type: String, default: null },
  periodEnd: { type: String, default: null }
})

const emit = defineEmits(['update:modelValue', 'success'])

const reminderTemplates = ref([])
const selectedTemplateId = ref(null)
const periodMonth = ref('')
const reminders = ref([])
const generating = ref(false)

const totalArrears = computed(() =>
  reminders.value.reduce((sum, r) => sum + Number(r.totalArrears || 0), 0)
)

watch(() => props.modelValue, (val) => {
  if (!val) {
    reminders.value = []
    selectedTemplateId.value = null
    periodMonth.value = ''
  }
})

const handleOpen = async () => {
  try {
    const res = await getTemplates()
    reminderTemplates.value = (res.data || []).filter(t => t.templateType === 'REMINDER')
    if (reminderTemplates.value.length === 1) {
      selectedTemplateId.value = reminderTemplates.value[0].id
    }
  } catch (e) {
    ElMessage.error('获取催缴模板失败')
  }
}

const handleGenerate = async () => {
  if (!selectedTemplateId.value) {
    ElMessage.warning('请选择催缴模板')
    return
  }
  if (!props.ownerIds.length) {
    ElMessage.warning('未选择催缴业主')
    return
  }
  generating.value = true
  try {
    const payload = {
      templateId: selectedTemplateId.value,
      ownerIds: [...props.ownerIds]
    }
    if (periodMonth.value) {
      payload.periodMonth = periodMonth.value
    } else {
      if (props.periodStart) payload.periodStart = props.periodStart
      if (props.periodEnd) payload.periodEnd = props.periodEnd
    }
    const res = await batchGenerateReminders(payload)
    reminders.value = res.data || []
    if (!reminders.value.length) {
      ElMessage.warning('选中业主当前账期没有欠费账单')
    } else {
      emit('success', reminders.value)
    }
  } finally {
    generating.value = false
  }
}

const previewOne = (row) => {
  const w = window.open('', '_blank')
  w.document.write(`<!DOCTYPE html><html><head><meta charset="UTF-8"><title>催缴单-${row.ownerName}</title></head><body>${row.html}</body></html>`)
  w.document.close()
}

const printAll = () => {
  if (!reminders.value.length) return
  const body = reminders.value.map(r => `<div style="page-break-after: always;">${r.html}</div>`).join('')
  const w = window.open('', '_blank')
  w.document.write(`<!DOCTYPE html><html><head><meta charset="UTF-8"><title>催缴单</title></head><body>${body}</body></html>`)
  w.document.close()
  w.print()
}
</script>

<style lang="scss" scoped>
.reminder-summary {
  margin-bottom: 12px;
  color: var(--text-secondary);
}
</style>
