import request from './request'

export function getAccountSetList() {
  return request.get('/account-sets')
}

export function getAccountSetById(id) {
  return request.get(`/account-sets/${id}`)
}

export function createAccountSet(data) {
  return request.post('/account-sets', data)
}

export function updateAccountSet(id, data) {
  return request.put(`/account-sets/${id}`, data)
}

export function deleteAccountSet(id) {
  return request.delete(`/account-sets/${id}`)
}

export function switchAccountSet(id) {
  return request.post(`/account-sets/${id}/switch`)
}
