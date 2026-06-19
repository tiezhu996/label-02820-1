import request from './request'

export function getDashboard() {
  return request.get('/statistics/dashboard')
}

export function getPaymentDetail(params) {
  return request.get('/statistics/payment-detail', { params })
}

export function getArrearsDetail(params) {
  return request.get('/statistics/arrears-detail', { params })
}

export function getSummary(params) {
  return request.get('/statistics/summary', { params })
}

export function getBuildingSummary(params) {
  return request.get('/statistics/building-summary', { params })
}

export function exportPaymentDetail(params) {
  return request.get('/statistics/payment-detail/export', { params, responseType: 'blob' })
}

export function exportArrearsDetail(params) {
  return request.get('/statistics/arrears-detail/export', { params, responseType: 'blob' })
}
