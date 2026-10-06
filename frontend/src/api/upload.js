import request from './request'

export const uploadImage = (file, dir = 'post') => {
  const form = new FormData()
  form.append('file', file)
  form.append('dir', dir)
  return request.post('/upload/image', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
