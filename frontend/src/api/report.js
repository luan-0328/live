import request from './request'

export function submitReport(data) {
  return request.post('/report', data)
}

export function getMyReports(params) {
  return request.get('/report/my', { params })
}
