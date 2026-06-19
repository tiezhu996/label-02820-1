import request from './request'

export function getOwnerList(params) {
  return request.get('/owners', { params })
}

export function getAllOwners() {
  return request.get('/owners/all')
}

export function getOwnerById(id) {
  return request.get(`/owners/${id}`)
}

export function createOwner(data) {
  return request.post('/owners', data)
}

export function updateOwner(id, data) {
  return request.put(`/owners/${id}`, data)
}

export function deleteOwner(id) {
  return request.delete(`/owners/${id}`)
}

export function importOwners(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/owners/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function downloadTemplate() {
  return request.get('/owners/template', { responseType: 'blob' })
}

export function getOwnersWithArrears(params) {
  return request.get('/owners/with-arrears', { params })
}
