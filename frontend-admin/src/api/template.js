import request from './request'

export function getTemplates(params) {
  return request.get('/templates', { params })
}

export function uploadTemplate(formData) {
  return request.post('/templates', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function deleteTemplate(id) {
  return request.delete(`/templates/${id}`)
}

export function previewTemplate(id, ownerId) {
  return request.get(`/templates/${id}/preview`, { 
    params: { ownerId }
  })
}

export function batchPreviewReminders(data) {
  return request.post('/templates/batch-preview', data)
}

export function getReminderTemplates() {
  return request.get('/templates', { params: { templateType: 'REMINDER' } })
}
