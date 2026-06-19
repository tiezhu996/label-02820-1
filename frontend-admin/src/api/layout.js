import request from './request'

export function getBuildingLayout() {
  return request.get('/layout/buildings')
}

export function createBuilding(data) {
  return request.post('/layout/buildings', data)
}

export function updateBuilding(id, data) {
  return request.put(`/layout/buildings/${id}`, data)
}

export function deleteBuilding(id) {
  return request.delete(`/layout/buildings/${id}`)
}

export function getParkingLayout() {
  return request.get('/layout/parkings')
}

export function getParkingSummary() {
  return request.get('/layout/parkings/summary')
}
