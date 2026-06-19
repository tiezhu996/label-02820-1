import request from './request'

export function getBackups() {
  return request.get('/backup/list')
}

export function createBackup() {
  return request.post('/backup/create')
}

export function restoreBackup(filename) {
  return request.post('/backup/restore', { filename })
}

export function deleteBackup(filename) {
  return request.delete(`/backup/${filename}`)
}

export function initializeSystem() {
  return request.post('/backup/initialize')
}
