import dayjs from 'dayjs'

function normalizeDateInput(value) {
  if (!value) return null

  if (Array.isArray(value)) {
    const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0] = value
    return new Date(year, month - 1, day, hour, minute, second)
  }

  return value
}

export function formatDate(date, format = 'YYYY-MM-DD') {
  const normalized = normalizeDateInput(date)
  if (!normalized) return ''

  const d = dayjs(normalized)
  return d.isValid() ? d.format(format) : ''
}

export function formatDateTime(date) {
  return formatDate(date, 'YYYY-MM-DD HH:mm:ss')
}

export function formatMoney(value, decimals = 2) {
  if (value === null || value === undefined) return '0.00'
  return Number(value).toFixed(decimals)
}

export function formatPercent(value, decimals = 2) {
  if (value === null || value === undefined) return '0.00%'
  return Number(value).toFixed(decimals) + '%'
}

export function downloadFile(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
    }, delay)
  }
}

export function throttle(fn, delay = 300) {
  let lastTime = 0
  return function (...args) {
    const now = Date.now()
    if (now - lastTime >= delay) {
      lastTime = now
      fn.apply(this, args)
    }
  }
}
