<template>
  <div class="page-container">
    <div class="card">
      <div class="card-header">
        <span class="title">楼栋布局图</span>
        <el-button type="primary" size="small" @click="showAddDialog = true" v-if="isAdmin">添加楼栋</el-button>
      </div>
      <div class="legend">
        <span class="legend-item"><span class="dot occupied"></span>已入住</span>
        <span class="legend-item"><span class="dot vacant"></span>空置</span>
      </div>
      <div class="layout-container" v-loading="loading">
        <div v-for="building in buildings" :key="building.id" class="building-block">
          <div class="building-header">
            <span>{{ building.buildingNo }}栋</span>
            <span class="building-stats">{{ building.occupiedRooms }}/{{ building.totalRooms }}</span>
          </div>
          <div v-for="unit in building.unitCount" :key="unit" class="unit-section">
            <div class="unit-header">{{ unit }}单元</div>
            <div class="floors-container">
              <div v-for="floor in building.floorCount" :key="floor" class="floor-row">
                <span class="floor-label">{{ building.floorCount - floor + 1 }}F</span>
                <div class="rooms-row">
                  <div 
                    v-for="room in building.roomsPerFloor" 
                    :key="room" 
                    class="room-cell"
                    :class="getRoomClass(building, unit, building.floorCount - floor + 1, room)"
                    @click="handleRoomClick(building, unit, building.floorCount - floor + 1, room)"
                  >
                    <div class="room-no">{{ formatRoomNo(unit, building.floorCount - floor + 1, room) }}</div>
                    <div class="room-owner">{{ getRoomOwner(building, unit, building.floorCount - floor + 1, room) }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="buildings.length === 0 && !loading" class="empty-tip">
          暂无楼栋数据，请先添加楼栋配置
        </div>
      </div>
    </div>
    
    <el-dialog v-model="showAddDialog" title="添加楼栋" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="楼栋号" prop="buildingNo">
          <el-input v-model="form.buildingNo" placeholder="如：1、A" />
        </el-form-item>
        <el-form-item label="单元数" prop="unitCount">
          <el-input-number v-model="form.unitCount" :min="1" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="楼层数" prop="floorCount">
          <el-input-number v-model="form.floorCount" :min="1" :max="50" style="width: 100%" />
        </el-form-item>
        <el-form-item label="每层房间数" prop="roomsPerFloor">
          <el-input-number v-model="form.roomsPerFloor" :min="1" :max="20" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)

const loading = ref(false)
const submitLoading = ref(false)
const buildings = ref([])
const showAddDialog = ref(false)
const formRef = ref(null)

const form = reactive({ buildingNo: '', unitCount: 1, floorCount: 6, roomsPerFloor: 2 })
const rules = {
  buildingNo: [{ required: true, message: '请输入楼栋号', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/layout/buildings')
    buildings.value = res.data || []
  } finally { loading.value = false }
}

const formatRoomNo = (unit, floor, room) => {
  return `${unit}-${String(floor).padStart(2, '0')}${String(room).padStart(2, '0')}`
}

const findOwner = (building, unit, floor, room) => {
  if (!building.owners) return null
  // 数据库中 unitNo 格式为 "1单元" 或 "1"，roomNo 格式为 "101" 或 "0101"
  const unitNoVariants = [String(unit), `${unit}单元`]
  // roomNo 可能是 "101" 或 "0101" 格式
  const roomNoVariants = [
    `${floor}${String(room).padStart(2, '0')}`,  // "101"
    `${String(floor).padStart(2, '0')}${String(room).padStart(2, '0')}`  // "0101"
  ]
  return building.owners.find(o => 
    unitNoVariants.includes(o.unitNo) && roomNoVariants.includes(o.roomNo)
  )
}

const getRoomClass = (building, unit, floor, room) => {
  const owner = findOwner(building, unit, floor, room)
  return owner && owner.status === 'OCCUPIED' ? 'occupied' : 'vacant'
}

const getRoomOwner = (building, unit, floor, room) => {
  const owner = findOwner(building, unit, floor, room)
  return owner ? owner.name : '空置'
}

const handleRoomClick = (building, unit, floor, room) => {
  const owner = findOwner(building, unit, floor, room)
  if (owner) {
    ElMessage.info(`业主：${owner.name}，电话：${owner.phone || '未登记'}`)
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    await request.post('/layout/buildings', form)
    ElMessage.success('添加成功')
    showAddDialog.value = false
    loadData()
  } finally { submitLoading.value = false }
}

onMounted(() => loadData())
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.legend {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
  font-size: 14px;
  
  .legend-item {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  
  .dot {
    width: 12px;
    height: 12px;
    border-radius: 2px;
    
    &.occupied { background: var(--success-color); }
    &.vacant { background: var(--border-color); }
  }
}

.layout-container {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.building-block {
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
  min-width: 280px;
}

.building-header {
  display: flex;
  justify-content: space-between;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
  color: var(--primary-color);
  
  .building-stats {
    font-size: 12px;
    color: var(--text-secondary);
    font-weight: normal;
  }
}

.unit-section {
  margin-bottom: 16px;
  
  &:last-child { margin-bottom: 0; }
}

.unit-header {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  padding-left: 30px;
}

.floors-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.floor-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.floor-label {
  width: 24px;
  font-size: 11px;
  color: var(--text-secondary);
  text-align: right;
}

.rooms-row {
  display: flex;
  gap: 4px;
}

.room-cell {
  width: 60px;
  padding: 4px;
  border-radius: 4px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  
  &.vacant {
    background: var(--bg-color);
    border: 1px dashed var(--border-color);
  }
  
  &.occupied {
    background: rgba(103, 194, 58, 0.15);
    border: 1px solid var(--success-color);
  }
  
  &:hover {
    transform: scale(1.05);
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  }
  
  .room-no {
    font-size: 10px;
    color: var(--text-secondary);
  }
  
  .room-owner {
    font-size: 11px;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.empty-tip {
  color: var(--text-secondary);
  text-align: center;
  padding: 40px;
  width: 100%;
}
</style>
