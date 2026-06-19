import request from './request'

export function getFeeStandards(params) {
  return request.get('/fee-standards', { params })
}

export function getFeeStandardById(id) {
  return request.get(`/fee-standards/${id}`)
}

export function createFeeStandard(data) {
  return request.post('/fee-standards', data)
}

export function updateFeeStandard(id, data) {
  return request.put(`/fee-standards/${id}`, data)
}

export function deleteFeeStandard(id) {
  return request.delete(`/fee-standards/${id}`)
}
