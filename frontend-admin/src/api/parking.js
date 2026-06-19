import request from './request'

export function getParkings(params) {
  return request.get('/parkings', { params })
}

export function getAllParkings() {
  return request.get('/parkings/all')
}

export function createParking(data) {
  return request.post('/parkings', data)
}

export function bindParking(id, data) {
  return request.put(`/parkings/${id}/bind`, data)
}

export function unbindParking(id) {
  return request.put(`/parkings/${id}/unbind`)
}

export function deleteParking(id) {
  return request.delete(`/parkings/${id}`)
}

export function importParkings(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/parkings/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function downloadTemplate() {
  return request.get('/parkings/template', { responseType: 'blob' })
}
