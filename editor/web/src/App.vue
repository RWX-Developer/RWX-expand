<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from './api.js'
import PackageList from './components/PackageList.vue'
import FileList from './components/FileList.vue'
import ModuleView from './components/ModuleView.vue'
import TextView from './components/TextView.vue'

const state = ref({ workspace: '', packages: [], pkg: '', entries: [], modified: false })
const filter = ref('source')
const sel = ref('')
const detail = ref(null)
const tab = ref('modules')
const busy = ref(false)
const status = ref('就绪')
const decompiled = ref(null)

const files = computed(() => {
  const list = state.value.entries || []
  const pick = {
    source: (e) => ['ini', 'toml', 'kt', 'text'].includes(e.kind),
    text: (e) => !['binary', 'class', 'dex'].includes(e.kind),
    all: () => true,
    jvm: (e) => e.kind === 'class' || e.kind === 'dex',
  }[filter.value]
  return list.filter(pick)
})

async function open(pkg) {
  busy.value = true
  try {
    state.value = await api.open(pkg)
    const entries = state.value.entries || []
    // 支持 ?entry=<条目名> 深链：命令行 --entry 会把地址带过来
    const wanted = new URLSearchParams(location.search).get('entry')
    if (wanted && entries.some((e) => e.name === wanted)) {
      await select(wanted)
      status.value = '已打开 ' + wanted
      return
    }
    const first = entries.find((e) => e.kind === 'ini') || entries.find((e) => e.kind !== 'binary') || entries[0]
    await select(first ? first.name : '')
    status.value = '已打开 ' + pkg.split(/[\\/]/).pop()
  } catch (e) {
    status.value = '打开失败: ' + e.message
  } finally {
    busy.value = false
  }
}

async function select(name) {
  sel.value = name || ''
  decompiled.value = null
  if (!name) { detail.value = null; return }
  try {
    detail.value = await api.entry(name)
    // 选中的条目如果不在当前过滤里（比如 class 出现在「源码」视图外），切到能看见它的过滤器
    if (!files.value.some((e) => e.name === name)) filter.value = 'all'
    tab.value = ['ini', 'toml'].includes(detail.value.kind) ? 'modules' : 'text'
    if (detail.value.kind === 'class') decompile()
  } catch (e) {
    status.value = '读取失败: ' + e.message
  }
}

async function decompile() {
  if (!sel.value) return
  busy.value = true
  status.value = '反编译中……'
  try {
    const r = await api.decompile(sel.value)
    decompiled.value = r.text
    status.value = '反编译完成 · ' + r.text.length + ' 字符'
  } catch (e) {
    status.value = '反编译失败: ' + e.message
  } finally {
    busy.value = false
  }
}

async function setValue(si, li, value) {
  try {
    detail.value = await api.value(sel.value, si, li, value)
    status.value = '已改 [' + detail.value.name + '] 第 ' + li + ' 行'
  } catch (e) { status.value = e.message }
}

async function addSection(name) {
  try { detail.value = await api.addSection(sel.value, name); status.value = '已加 [' + name + ']' } catch (e) { status.value = e.message }
}

async function removeSection(si) {
  try { detail.value = await api.removeSection(sel.value, si); status.value = '已删一个节' } catch (e) { status.value = e.message }
}

async function saveText(text) {
  try { detail.value = await api.text(sel.value, text); status.value = '已写入内存（还没落盘）' } catch (e) { status.value = e.message }
}

async function save() {
  try {
    const r = await api.save()
    status.value = r.message
    if (r.state) state.value = r.state
  } catch (e) { status.value = '保存失败: ' + e.message }
}

async function pack() {
  try {
    const r = await api.pack()
    status.value = r.message
  } catch (e) { status.value = '打包失败: ' + e.message }
}

onMounted(async () => {
  try {
    state.value = await api.state()
    if (state.value.packages.length) await open(state.value.packages[0])
    else status.value = '工作区里没有模组'
  } catch (e) {
    status.value = '连不上后端: ' + e.message
  }
})
</script>

<template>
  <div class="toolbar">
    <span class="title">RWX 编辑器 · Web · {{ state.workspace }}</span>
    <button class="btn ok" @click="save">保存</button>
    <button class="btn" @click="pack">打包为 JAR</button>
    <span class="faint" v-if="state.modified">未落盘</span>
  </div>
  <div class="cols">
    <div class="col side">
      <PackageList :packages="state.packages" :pkg="state.pkg" @open="open" />
    </div>
    <div class="col mid">
      <FileList :entries="files" :sel="sel" :filter="filter" :total="state.entries.length"
                @pick="select" @set-filter="filter = $event" />
    </div>
    <div class="col main">
      <div class="toolbar" style="border-bottom:1px solid var(--line)">
        <span class="tab" :class="{ on: tab === 'modules' }" @click="tab = 'modules'">模块</span>
        <span class="tab" :class="{ on: tab === 'text' }" @click="tab = 'text'">文本</span>
        <span class="faint" style="margin-left:8px" v-if="detail">{{ detail.name }} · {{ detail.kind }} · {{ detail.size }} B</span>
      </div>
      <div class="body">
        <template v-if="detail">
          <ModuleView v-if="tab === 'modules'" :entry="detail"
                      @set-value="setValue" @add-section="addSection" @remove-section="removeSection" />
          <TextView v-else :entry="detail" :decompiled="decompiled" :busy="busy"
                    @decompile="decompile" @save-text="saveText" />
        </template>
        <div v-else class="dim">左边选一个条目</div>
      </div>
    </div>
  </div>
  <div class="status">
    <span>{{ status }}</span>
    <span v-if="busy">忙……</span>
    <span>文件 {{ files.length }} / {{ state.entries.length }}</span>
  </div>
</template>
