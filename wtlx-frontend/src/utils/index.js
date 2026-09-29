// 原 js/common.js 中 util 对象的迁移（getUrlParam / formatPrice 逻辑原样保留）

export function getUrlParam(name) {
  // hash 路由下 query 在 window.location.hash 中，先尝试解析 hash 内 query，再回退 location.search
  const hash = window.location.hash.split('?')[1]
  const search = window.location.search.length ? window.location.search.substr(1) : (hash || '')
  const reg = new RegExp('(^|&)' + name + '=([^&]*)(&|$)', 'i')
  const r = search.match(reg)
  if (r != null) return decodeURI(r[2])
  return ''
}

export function formatPrice(val) {
  if (typeof val === 'string') {
    if (isNaN(val)) {
      return null
    }
    const index = val.lastIndexOf('.')
    let p = ''
    if (index < 0) {
      p = val + '00'
    } else if (index === p.length - 2) {
      p = val.replace('\.', '') + '0'
    } else {
      p = val.replace('\.', '')
    }
    return parseInt(p)
  } else if (typeof val === 'number') {
    if (!val) {
      return null
    }
    const s = val + ''
    if (s.length === 0) {
      return '0.00'
    }
    if (s.length === 1) {
      return '0.0' + val
    }
    if (s.length === 2) {
      return '0.' + val
    }
    const i = s.indexOf('.')
    if (i < 0) {
      return s.substring(0, s.length - 2) + '.' + s.substring(s.length - 2)
    }
    const num = s.substring(0, i) + s.substring(i + 1)
    if (i === 1) {
      return '0.0' + num
    }
    if (i === 2) {
      return '0.' + num
    }
    if (i > 2) {
      return num.substring(0, i - 2) + '.' + num.substring(i - 2)
    }
  }
}
