import request from './request'

export function getReceivables(params) {
  return request.get('/receivables', { params })
}

export function lockReceivable(id) {
  return request.put(`/receivables/${id}/lock`)
}

export function unlockReceivable(id) {
  return request.put(`/receivables/${id}/unlock`)
}

export function generateNotice(id) {
  return request.get(`/receivables/${id}/notice`)
}
