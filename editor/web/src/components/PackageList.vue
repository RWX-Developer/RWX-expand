<script setup>
defineProps({ packages: Array, pkg: String })
defineEmits(['open'])
const base = (p) => p.split(/[\\/]/).pop()
</script>

<template>
  <div class="head">模组（{{ packages.length }}）</div>
  <div v-for="p in packages" :key="p" class="row" :class="{ on: p === pkg }" :title="p" @click="$emit('open', p)">
    {{ base(p) }}
  </div>
</template>
