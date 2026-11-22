async function call(path, params) {
  const usp = new URLSearchParams()
  for (const [k, v] of Object.entries(params || {})) usp.set(k, v == null ? '' : String(v))
  const res = await fetch('/api/' + path + '?' + usp.toString(), { method: 'POST' })
  const text = await res.text()
  let data
  try { data = JSON.parse(text) } catch (e) { throw new Error('后端返回的不是 JSON: ' + text.slice(0, 200)) }
  if (data.error) throw new Error(data.error)
  return data
}

export const api = {
  state: () => call('state'),
  open: (pkg) => call('open', { pkg }),
  entry: (name) => call('entry', { name }),
  value: (name, si, li, value) => call('value', { name, si, li, value }),
  addSection: (name, section) => call('addSection', { name, section }),
  removeSection: (name, si) => call('removeSection', { name, si }),
  text: (name, text) => call('text', { name, text }),
  decompile: (name) => call('decompile', { name }),
  save: () => call('save'),
  pack: () => call('pack'),
}
