import request from './request'

export function getUsers(params) {
  return request.get('/users', { params })
}

export function getUserById(id) {
  return request.get(`/users/${id}`)
}

export function createUser(data) {
  return request.post('/users', data)
}

export function updateUser(id, data) {
  return request.put(`/users/${id}`, data)
}

export function deleteUser(id) {
  return request.delete(`/users/${id}`)
}

export function updateUserPermissions(id, permissions) {
  return request.put(`/users/${id}/permissions`, { permissions })
}

export function resetPassword(id) {
  return request.put(`/users/${id}/reset-password`)
}
