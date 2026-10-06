import request from './request'

export function getDashboard() {
  return request.get('/admin/dashboard')
}

export function listUsers(params) {
  return request.get('/admin/users', { params })
}

export function banUser(userId, status) {
  return request.put(`/admin/user/${userId}/ban`, { status })
}

export function listCategories() {
  return request.get('/category/list')
}

export function createCategory(data) {
  return request.post('/admin/category', data)
}

export function updateCategory(id, data) {
  return request.put(`/admin/category/${id}`, data)
}

export function deleteCategory(id) {
  return request.delete(`/admin/category/${id}`)
}

export function listReports(params) {
  return request.get('/admin/reports', { params })
}

export function handleReport(id, data) {
  return request.put(`/admin/report/${id}/handle`, data)
}

export function listAdminPosts(params) {
  return request.get('/admin/posts', { params })
}

export function forceDeletePost(id) {
  return request.delete(`/admin/post/${id}`)
}

export function forceDeleteComment(id) {
  return request.delete(`/admin/comment/${id}`)
}

export function listAdminLogs(params) {
  return request.get('/admin/logs', { params })
}
