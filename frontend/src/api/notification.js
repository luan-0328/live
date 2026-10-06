import request from './request'

export const getNotifications = (params) =>
  request.get('/notification/list', { params })

export const getUnreadCount = () =>
  request.get('/notification/unread-count')

export const markRead = (id) =>
  request.put(`/notification/read/${id}`)

export const markAllRead = () =>
  request.put('/notification/read-all')
