<template>
  <div class="page-container">
    <div class="card">
      <div class="card-header">
        <span class="title">系统配置</span>
      </div>
      <el-form :model="form" label-width="140px" style="max-width: 500px;">
        <el-form-item label="物业公司名称">
          <el-input v-model="form.companyName" />
        </el-form-item>
        <el-form-item label="公司Logo">
          <el-upload :show-file-list="false" :before-upload="handleLogoUpload">
            <img v-if="form.logoUrl" :src="form.logoUrl" style="width: 100px; height: 100px; object-fit: contain;" />
            <el-button v-else type="primary">上传Logo</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="账单到期天数">
          <el-input-number v-model="form.defaultDueDays" :min="1" :max="90" />
          <div class="form-tip">账单周期结束后多少天为缴费截止日</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSave">保存配置</el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <div class="card" style="margin-top: 20px;">
      <div class="card-header">
        <span class="title">系统信息</span>
      </div>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="系统版本">v1.0.0</el-descriptions-item>
        <el-descriptions-item label="发布日期">2026-02-01</el-descriptions-item>
        <el-descriptions-item label="更新日志">
          <ul class="changelog">
            <li>初始版本发布</li>
            <li>支持多账套管理</li>
            <li>支持物业费、车位费自动生成</li>
            <li>支持账单打印与通知单生成</li>
          </ul>
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfig, updateConfig, uploadLogo } from '@/api/config'
import { useConfigStore } from '@/store/config'

const configStore = useConfigStore()
const loading = ref(false)
const form = reactive({ companyName: '', logoUrl: '', defaultDueDays: 15 })

const loadData = async () => {
  const res = await getConfig()
  form.companyName = res.data.companyName || ''
  form.logoUrl = res.data.logoUrl || ''
  form.defaultDueDays = parseInt(res.data.defaultDueDays) || 15
}

const handleLogoUpload = async (file) => {
  try {
    const res = await uploadLogo(file)
    form.logoUrl = res.data
    configStore.setLogoUrl(res.data)
    ElMessage.success('上传成功')
  } catch (e) {}
  return false
}

const handleSave = async () => {
  loading.value = true
  try {
    await updateConfig({ companyName: form.companyName, defaultDueDays: form.defaultDueDays })
    configStore.setCompanyName(form.companyName)
    ElMessage.success('保存成功')
  } finally { loading.value = false }
}

onMounted(() => loadData())
</script>

<style lang="scss" scoped>
.form-tip { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.changelog { margin: 0; padding-left: 20px; li { margin: 4px 0; } }
</style>
