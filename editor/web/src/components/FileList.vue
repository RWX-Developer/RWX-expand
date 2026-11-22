<script setup>
defineProps({ entries: Array, sel: String, filter: String, total: Number })
defineEmits(['pick', 'set-filter'])
const filters = [
  ['source', '源码'],
  ['text', '文本'],
  ['all', '全部'],
  ['jvm', 'JVM'],
]
</script>

<template>
  <div class="head">文件（{{ entries.length }} / {{ total }}）</div>
  <div class="chips">
    <span v-for="f in filters" :key="f[0]" class="chip" :class="{ on: filter === f[0] }" @click="$emit('set-filter', f[0])">
      {{ f[1] }}
    </span>
  </div>
  <div v-for="e in entries" :key="e.name" class="row" :class="{ on: e.name === sel }" :title="e.name" @click="$emit('pick', e.name)">
    {{ e.name }}
    <div class="k">{{ e.kind }} · {{ e.size }} B</div>
  </div>
</template>
