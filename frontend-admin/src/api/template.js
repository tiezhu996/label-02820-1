import request from './request'

export function getTemplates(params) {
  return request.get('/templates', { params })
}

export function uploadTemplate(formData) {
  return request.post('/templates/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function deleteTemplate(id) {
  return request.delete(`/templates/${id}`)
}

export function previewTemplate(id, ownerId) {
  return request.get(`/templates/${id}/preview`, { 
    params: { ownerId },
    responseType: 'blob' 
  })
}

export function batchGenerateReminders(data) {
  return request.post('/templates/reminders/batch', data)
}
