import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createServer } from 'vite'
import { compileScript, parse } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import { createPinia, setActivePinia } from 'pinia'

const values = new Map()
globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: key => values.delete(key) }
globalThis.window = new EventTarget()
window.location = { pathname: '/', href: '/' }
let server, request, useUserStore
before(async () => {
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  request = (await server.ssrLoadModule('/src/api/request.js')).default
  useUserStore = (await server.ssrLoadModule('/src/stores/user.js')).useUserStore
})
after(async () => { await server?.close() })
function session() {
  values.clear()
  setActivePinia(createPinia())
  const store = useUserStore()
  store.setToken('old-token'); store.setUser({ id: 1, nickname: 'reader', role: 'ROLE_USER' })
  return store
}
const reply = (config, data, headers = {}) => ({ data, headers, config, status: 200, statusText: 'OK' })

test('续期同步Pinia，退出后的迟到响应不会恢复登录', async () => {
  const store = session()
  await request.get('/user/me', { silent: true, adapter: async config => reply(config, { code: 200 }, { 'x-auth-token': 'new-token' }) })
  assert.equal(store.token, 'new-token')
  let finish
  const pending = request.get('/user/me', { silent: true, adapter: config => new Promise(resolve => { finish = () => resolve(reply(config, { code: 200 }, { 'x-auth-token': 'late-token' })) }) })
  await new Promise(resolve => setImmediate(resolve))
  store.logout(); finish(); await pending
  assert.equal(store.isLogin, false)
  assert.equal(localStorage.getItem('token'), null)
})

test('旧账号的迟到401不会清除新账号会话', async () => {
  const store = session()
  let finish
  const pending = request.get('/user/me', { silent: true, skipAuthRedirect: true,
    adapter: config => new Promise(resolve => { finish = () => resolve(reply(config, { code: 401 })) }) })
  await new Promise(resolve => setImmediate(resolve))
  store.setToken('another-token'); store.setUser({ id: 2, nickname: 'new reader' })
  finish(); await assert.rejects(pending)
  assert.equal(store.token, 'another-token'); assert.equal(store.user.userId, 2)
})

test('业务401同步清除页面用户和未读数', async () => {
  const store = session(); store.unreadCount = 9
  await assert.rejects(request.get('/private', { silent: true, skipAuthRedirect: true,
    adapter: async config => reply(config, { code: 401 }) }))
  assert.equal(store.isLogin, false); assert.equal(store.user, null); assert.equal(store.unreadCount, 0)
})

// 执行实际SFC的setup代码，替换网络与路由依赖，验证页面动作产生的请求。
async function page(name, mocks) {
  const source = await readFile(new URL(`../src/views/${name}.vue`, import.meta.url), 'utf8')
  let code = compileScript(parse(source).descriptor, { id: name }).content
  const imports = []
  code = code.replace(/import\s+([\s\S]+?)\s+from\s+['"]([^'"]+)['"];?/g, (_, clause, path) => {
    const index = imports.length
    let module = mocks[path]
    if (path === 'vue') module = { ...Vue, onMounted() {}, onUnmounted() {} }
    imports.push(module || {})
    if (clause.trim().startsWith('{')) return `const ${clause.replace(/\bas\b/g, ':')} = imports[${index}];`
    return `const ${clause.trim()} = imports[${index}].default;`
  })
  code = code.replace('export default', 'return')
  const component = new Function('imports', code)(imports)
  const scope = Vue.effectScope()
  const state = scope.run(() => component.setup({}, { expose() {} }))
  return { state, stop: () => scope.stop() }
}
const tick = async () => { await Vue.nextTick(); await new Promise(resolve => setImmediate(resolve)) }

test('搜索分页保留page=2，顶栏改变关键词时重新查询', async () => {
  const route = Vue.reactive({ query: { keyword: '公园', page: '2' } })
  const calls = []
  const router = { push: async location => { route.query = location.query } }
  const screen = await page('Search', {
    'vue-router': { useRoute: () => route, useRouter: () => router },
    '../api/post': { searchPosts: async params => { calls.push(params); return { data: { records: [], total: 45 } } } },
    '../utils/latestRequest': await import('../src/utils/latestRequest.js')
  })
  await tick()
  assert.equal(calls.at(-1).page, 2)
  screen.state.changePage(3); await tick(); assert.equal(calls.at(-1).page, 3)
  route.query = { keyword: '美食' }; await tick()
  assert.equal(calls.at(-1).keyword, '美食'); assert.equal(calls.at(-1).page, 1)
  screen.stop()
})

test('搜索旧响应晚到也不能覆盖新查询', async () => {
  const route = Vue.reactive({ query: { keyword: '旧查询' } })
  const complete = []
  const screen = await page('Search', {
    'vue-router': { useRoute: () => route, useRouter: () => ({}) },
    '../api/post': { searchPosts: () => new Promise(resolve => complete.push(resolve)) },
    '../utils/latestRequest': await import('../src/utils/latestRequest.js')
  })
  await tick(); route.query = { keyword: '新查询' }; await tick()
  complete[1]({ data: { records: [{ postId: 2 }], total: 1 } }); await tick()
  complete[0]({ data: { records: [{ postId: 1 }], total: 1 } }); await tick()
  assert.equal(screen.state.posts.value[0].postId, 2)
  screen.stop()
})

test('图片上传失败停止发布，保留已上传URL供重试', async () => {
  let uploads = 0, saves = 0
  const screen = await page('CreatePost', {
    'vue-router': { useRoute: () => ({ params: {} }), useRouter: () => ({ push() {} }) },
    '../stores/user': { useUserStore: () => ({ user: { userId: 1 } }) },
    '../utils/images': await import('../src/utils/images.js'),
    '../api/upload': { uploadImage: async () => { uploads++; if (uploads === 2) throw new Error('OSS down'); return { data: { url: 'https://example.com/image.png' } } } },
    '../api/post': { createPost: async () => { saves++ } },
    'element-plus': { ElMessage: { success() {}, warning() {} } }
  })
  screen.state.initializing.value = false
  screen.state.formRef.value = { validate: async () => true }
  screen.state.fileList.value = [{ name: 'one', raw: {} }, { name: 'two', raw: {} }]
  await screen.state.handleSubmit()
  assert.equal(saves, 0); assert.equal(screen.state.fileList.value[0].raw, null)
  await screen.state.handleSubmit()
  assert.equal(saves, 1); assert.equal(uploads, 3)
  screen.stop()
})

test('管理员删除他人评论使用管理接口，作者删除使用普通接口', async () => {
  const calls = []
  const store = { isAdmin: true, isLogin: true, user: { userId: 1 } }
  const screen = await page('PostDetail', {
    'vue-router': { useRoute: () => ({ params: { id: '10' } }), useRouter: () => ({}) },
    '../stores/user': { useUserStore: () => store },
    '../api/admin': { forceDeleteComment: async id => calls.push(['admin', id]) },
    '../api/comment': { deleteComment: async id => calls.push(['owner', id]), getComments: async () => ({ data: { records: [], total: 0 } }) },
    'element-plus': { ElMessageBox: { confirm: async () => {} }, ElMessage: { success() {} } }
  })
  screen.state.comments.value = [{ id: 20, authorId: 2 }]
  await screen.state.handleDeleteComment(20)
  screen.state.comments.value = [{ id: 21, authorId: 1 }]
  await screen.state.handleDeleteComment(21)
  assert.deepEqual(calls, [['admin', 20], ['owner', 21]])
  screen.stop()
})

test('退出登录先携带会话完成后端撤销，再清除本地状态', async () => {
  let finish, cleared = false
  const navigations = []
  const screen = await page('../components/AppHeader', {
    'vue-router': { useRouter: () => ({ push: path => navigations.push(path) }) },
    '../stores/user': { useUserStore: () => ({ isLogin: true, logout: () => { cleared = true }, user: {} }) },
    '../api/auth': { logout: () => new Promise(resolve => { finish = resolve }) }
  })
  const action = screen.state.handleCommand('logout')
  await tick(); assert.equal(cleared, false)
  finish(); await action
  assert.equal(cleared, true); assert.deepEqual(navigations, ['/'])
  screen.stop()
})

test('附近列表翻页仍使用附近接口和相同位置', async () => {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: {
    geolocation: { getCurrentPosition: callback => callback({ coords: { longitude: 120, latitude: 30 } }) }
  } })
  const route = Vue.reactive({ query: {}, fullPath: '/' })
  const calls = []
  const router = { resolve: () => ({ fullPath: '/changed' }), push: async target => { route.query = target.query } }
  const screen = await page('Home', {
    'vue-router': { useRoute: () => route, useRouter: () => router },
    '../stores/user': { useUserStore: () => ({ isLogin: false }) },
    '../utils/latestRequest': await import('../src/utils/latestRequest.js'),
    '../api/post': {
      getPostList: async () => ({ data: { records: [], total: 0 } }),
      getNearbyPosts: async params => { calls.push(params); return { data: { records: [], total: 40 } } }
    },
    'element-plus': { ElMessage: { warning() {} } }
  })
  await screen.state.showNearby(); await tick()
  screen.state.changePage(2); await tick()
  assert.equal(calls.at(-1).page, 2)
  assert.equal(calls.at(-1).longitude, 120); assert.equal(calls.at(-1).latitude, 30)
  screen.stop()
})

test('个人中心缺少用户ID时不会请求全部帖子，关注菜单使用分页接口', async () => {
  let allPosts = 0
  const pages = []
  const screen = await page('UserCenter', {
    'vue-router': { useRouter: () => ({}) },
    '../stores/user': { useUserStore: () => ({ isLogin: true, user: null }) },
    '../api/post': { getPostList: async () => { allPosts++; return { data: {} } } },
    '../api/user': { getFollowing: async params => { pages.push(params); return { data: { records: [{ id: 2 }], total: 1 } } } },
    '../utils/latestRequest': await import('../src/utils/latestRequest.js')
  })
  await screen.state.loadMyPosts(); assert.equal(allPosts, 0)
  screen.state.tab.value = 'following'; await tick()
  assert.deepEqual(pages, [{ page: 1, size: 20 }]); assert.equal(screen.state.records.value[0].id, 2)
  screen.stop()
})
