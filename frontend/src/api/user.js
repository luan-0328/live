import request from './request'

export const getUserInfo = () =>
  request.get('/user/me')

export const getUserProfile = (userId) =>
  request.get(`/user/${userId}/profile`)

export const updateProfile = (data) =>
  request.put('/user/profile', data)

export const updatePhone = (data) =>
  request.put('/user/phone', data)

export const updatePassword = (data) =>
  request.put('/user/password', data)

export const deleteAccount = () =>
  request.delete('/user/account')

export const followUser = (userId) =>
  request.post(`/user/follow/${userId}`)

export const unfollowUser = (userId) =>
  request.delete(`/user/follow/${userId}`)

export const getFollowers = (params) =>
  request.get('/user/followers', { params })

export const getFollowing = (params) =>
  request.get('/user/following', { params })

export const getFavorites = (params) =>
  request.get('/user/favorites', { params })
