<template>
  <div class="page-container">
    <div class="search-area">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="操作人">
          <el-input v-model="searchForm.username" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-input v-model="searchForm.operation" placeholder="请输入" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <div class="table-area">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="username" label="操作人" min-width="100" />
        <el-table-column prop="operation" label="操作类型" min-width="120" />
        <el-table-column prop="method" label="请求方法" min-width="300" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP地址" min-width="120" />
        <el-table-column prop="createTime" label="操作时间" min-width="180">
          <template #default="{ row }">
            {{ formatLogTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size" :total="pagination.total" layout="total, sizes, prev, pager, next" @size-change="loadData" @current-change="loadData" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '@/api/request'
import { formatDateTime } from '@/utils'

const loading = ref(false)
const tableData = ref([])
const searchForm = reactive({ username: '', operation: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const formatLogTime = (value) => {
  if (!value) return ''

  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value
    const date = new Date(year, month - 1, day, hour, minute, second)
    return formatDateTime(date)
  }

  return formatDateTime(value)
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/logs', { params: { page: pagination.page, size: pagination.size, ...searchForm } })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { Object.assign(searchForm, { username: '', operation: '' }); handleSearch() }

onMounted(() => loadData())
</script>
