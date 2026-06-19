import request from './request'

export function getBills(params) {
  return request.get('/bills', { params })
}

export function getBillById(id) {
  return request.get(`/bills/${id}`)
}

export function generateBills(data) {
  return request.post('/bills/generate', data)
}

export function deleteBill(id) {
  return request.delete(`/bills/${id}`)
}

export function exportBills(params) {
  return request.get('/bills/export', { params, responseType: 'blob' })
}

export function getBillPrintHtml(id) {
  return request.get(`/bills/${id}/print`)
}
