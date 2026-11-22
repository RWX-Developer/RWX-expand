<script setup>
import { ref, watch } from 'vue'

const props = defineProps({ entry: Object, decompiled: String, busy: Boolean })
const emit = defineEmits(['decompile', 'save-text'])
const draft = ref('')

watch(() => props.entry, (e) => {
  draft.value = e && typeof e.text === 'string' ? e.text : ''
}, { immediate: true })
</script>

<template>
  <div v-if="entry.kind === 'class'">
    <div class="toolbar" style="border:none;padding:0 0 8px">
      <span class="dim">{{ decompiled ? 'Java 源码（CFR 0.152）· ' + decompiled.length + ' 字符' : (busy ? '反编译中……' : '还没反编译') }}</span>
      <button class="btn" :class="{ off: busy }" @click="$emit('decompile')">重新反编译</button>
    </div>
    <pre v-if="decompiled">{{ decompiled }}</pre>
    <div v-else class="dim">选中 .class 会自动反编译，没出来就点上面的按钮。</div>
  </div>

  <div v-else-if="entry.kind === 'dex'">
    <div class="warn" style="margin-bottom:8px">classes.dex 是 Android 字节码，CFR 只认 .class。类清单在「模块」页。</div>
    <pre class="faint">{{ entry.hex }}</pre>
  </div>

  <div v-else-if="entry.kind === 'binary'">
    <div class="warn" style="margin-bottom:8px">二进制条目 · 不是文本，按文本解码只会得到乱码</div>
    <pre class="faint">{{ entry.hex }}</pre>
  </div>

  <div v-else>
    <textarea v-model="draft" spellcheck="false"></textarea>
    <div style="margin-top:6px">
      <button class="btn" @click="$emit('save-text', draft)">写入内存</button>
      <span class="faint" style="margin-left:8px">写的是这个条目的全文，落盘要点顶部「保存」</span>
    </div>
  </div>
</template>
