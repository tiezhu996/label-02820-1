import request from './request'

export function getPayments(params) {
  return request.get('/payments', { params })
}

export function createPayment(data) {
  return request.post('/payments', data)
}

export function getPaymentMethods() {
  return request.get('/fee-standards/payment-methods')
}

export function createPaymentMethod(data) {
  return request.post('/fee-standards/payment-methods', data)
}

export function deletePaymentMethod(id) {
  return request.delete(`/fee-standards/payment-methods/${id}`)
}
