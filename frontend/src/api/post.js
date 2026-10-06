import request from './request'

export const getPostList = (params) =>
  request.get('/post/list', { params })

export const getPostDetail = (id) =>
  request.get(`/post/${id}`)

export const getNearbyPosts = (params) =>
  request.get('/post/nearby', { params })

export const searchPosts = (params) =>
  request.get('/post/search', { params })

export const createPost = (data) =>
  request.post('/post', data)

export const updatePost = (id, data) =>
  request.put(`/post/${id}`, data)

export const deletePost = (id) =>
  request.delete(`/post/${id}`)

export const likePost = (id) =>
  request.post(`/post/${id}/like`)

export const unlikePost = (id) =>
  request.delete(`/post/${id}/like`)

export const favoritePost = (id) =>
  request.post(`/post/${id}/favorite`)

export const unfavoritePost = (id) =>
  request.delete(`/post/${id}/favorite`)
