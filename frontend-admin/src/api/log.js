import request from './request'

export function getLogs(params) {
  return request.get('/logs', { params })
}
