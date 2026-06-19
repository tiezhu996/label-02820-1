<template>
  <div class="page-container">
    <div class="card">
      <div class="card-header">
        <span class="title">车位布局图</span>
      </div>
      <div class="parking-grid">
        <div v-for="parking in parkings" :key="parking.id" class="parking-cell" :class="{ used: parking.status === 'USED' }">
          <div class="parking-no">{{ parking.parkingNo }}</div>
          <div class="parking-owner">{{ parking.ownerName || '空置' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/api/request'

const parkings = ref([])

const loadData = async () => {
  const res = await request.get('/parkings/all')
  parkings.value = res.data || []
}

onMounted(() => loadData())
</script>

<style lang="scss" scoped>
.parking-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 12px;
}

.parking-cell {
  padding: 12px;
  border-radius: 4px;
  text-align: center;
  background: var(--bg-color);
  border: 2px solid var(--border-color);
  
  &.used {
    background: rgba(103, 194, 58, 0.15);
    border-color: var(--success-color);
  }
  
  .parking-no {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
  }
  
  .parking-owner {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 4px;
  }
}
</style>
