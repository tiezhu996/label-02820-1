import request from './request'

export function getConfig() {
  return request.get('/config')
}

export function updateConfig(data) {
  return request.put('/config', data)
}

export function uploadLogo(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/config/logo', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
