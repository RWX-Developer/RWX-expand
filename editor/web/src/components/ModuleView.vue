<script setup>
import { ref } from 'vue'

const props = defineProps({ entry: Object })
const emit = defineEmits(['set-value', 'add-section', 'remove-section'])
const newSection = ref('')

function commit(si, li, ev) {
  const v = ev.target.value
  ev.target.value = ''
  if (v !== '') emit('set-value', si, li, v)
}

function commitInput(ev) {
  // 回车提交；失焦也提交
}
</script>

<template>
  <div v-if="entry.doc">
    <div class="card" v-for="s in entry.doc.sections" :key="s.i">
      <header>
        <span class="sect">{{ s.headerless ? '（顶层键）' : '[' + s.name + ']' }}</span>
        <span class="count">{{ s.lines.length }} 项{{ s.isNew ? ' · 新加' : '' }}</span>
        <span class="grow"></span>
        <button class="btn" @click="$emit('remove-section', s.i)">删除这个节</button>
      </header>
      <div class="kv">
        <template v-for="l in s.lines" :key="l.li">
          <div class="k">{{ l.k }}</div>
          <div>
            <input :value="l.v" @change="(ev) => $emit('set-value', s.i, l.li, ev.target.value)" spellcheck="false" />
          </div>
        </template>
      </div>
    </div>
    <div class="card" style="padding:8px">
      <input class="add" v-model="newSection" placeholder="新节名，如 attack" />
      <button class="btn" style="margin-left:6px" @click="newSection && ($emit('add-section', newSection), newSection = '')">＋新增节</button>
      <span class="faint" style="margin-left:8px">改值后点上面「保存」才落盘</span>
    </div>
  </div>

  <div v-else-if="entry.class" class="card">
    <header><span class="sect">{{ entry.class.name }}</span><span class="count">父类 {{ entry.class.superName || '—' }} · {{ entry.class.javaVersion }} · 方法 {{ entry.class.methods.length }}</span></header>
    <div style="padding:10px">
      <div class="head">引用的平台 API（{{ entry.class.apiRefs.length }}）</div>
      <div v-for="r in entry.class.apiRefs" :key="r.name" class="mono faint">{{ r.name }} ×{{ r.count }}</div>
      <div class="head" style="margin-top:12px">方法表（{{ entry.class.methods.length }}）</div>
      <pre class="faint" v-for="m in entry.class.methods.slice(0, 80)" :key="m.name + m.desc">{{ m.isAbstract ? 'abstract ' : '' }}{{ m.name }}{{ m.desc }}</pre>
    </div>
  </div>

  <div v-else-if="entry.dex" class="card">
    <header><span class="sect">classes.dex {{ entry.dex.version }}</span><span class="count">类 {{ entry.dex.classCount }} · 类型 {{ entry.dex.typeCount }} · 方法 {{ entry.dex.methodCount }}</span></header>
    <div style="padding:10px">
      <div class="head">顶层包</div>
      <div v-for="p in entry.dex.packages" :key="p.name" class="mono faint">{{ p.name }} — {{ p.count }} 个类型</div>
      <div class="head" style="margin-top:12px">类清单（前 300）</div>
      <pre class="faint" v-for="c in entry.dex.classes" :key="c">{{ c }}</pre>
    </div>
  </div>

  <div v-else class="dim">这个条目解析不出结构，切到「文本」看内容。</div>
</template>
