import request from './request'

export const getComments = (postId, params) =>
  request.get(`/post/${postId}/comments`, { params })

export const addComment = (postId, data) =>
  request.post(`/post/${postId}/comment`, data)

export const deleteComment = (commentId) =>
  request.delete(`/comment/${commentId}`)
