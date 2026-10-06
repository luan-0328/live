// 快速切换筛选条件时，仅最后发出的请求可以修改页面。
export function latestRequest() {
  let version = 0
  return {
    start: () => ++version,
    isCurrent: id => id === version,
    cancel: () => { version++ }
  }
}
