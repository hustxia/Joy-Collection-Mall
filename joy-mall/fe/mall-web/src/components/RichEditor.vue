<template>
  <div class="rich-editor">
    <div class="re-toolbar">
      <button type="button" class="re-btn" title="加粗" @mousedown.prevent="cmd('bold')"><b>B</b></button>
      <button type="button" class="re-btn" title="斜体" @mousedown.prevent="cmd('italic')"><i>I</i></button>
      <button type="button" class="re-btn" title="下划线" @mousedown.prevent="cmd('underline')"><u>U</u></button>
      <span class="re-sep"></span>
      <button type="button" class="re-btn" title="小标题" @mousedown.prevent="cmd('formatBlock', '<h3>')">H3</button>
      <button type="button" class="re-btn" title="段落" @mousedown.prevent="cmd('formatBlock', '<p>')">P</button>
      <button type="button" class="re-btn" title="无序列表" @mousedown.prevent="cmd('insertUnorderedList')">列表</button>
      <button type="button" class="re-btn" title="插入链接" @mousedown.prevent="createLink">链接</button>
      <button type="button" class="re-btn" title="清除格式" @mousedown.prevent="cmd('removeFormat')">清除</button>
      <span class="re-spacer"></span>
      <button type="button" class="re-btn re-btn--mode" @mousedown.prevent="toggleSource">{{ source ? '可视化' : 'HTML' }}</button>
    </div>

    <div v-show="!source" ref="editor" class="re-body" contenteditable="true" :data-placeholder="placeholder" @input="emit" @blur="emit"></div>
    <textarea v-show="source" class="re-source" :value="value" spellcheck="false" @input="onSource"></textarea>
  </div>
</template>

<script>
export default {
  name: 'RichEditor',
  props: {
    value: { type: String, default: '' },
    placeholder: { type: String, default: '请输入内容，支持加粗、标题、列表、链接等' }
  },
  data() {
    return { source: false }
  },
  mounted() {
    this.syncFromValue()
  },
  watch: {
    value(v) {
      // 编辑中不要回写 innerHTML，否则光标会跳到开头
      if (this.source || this.isFocused() || this.$refs.editor.innerHTML === (v || '')) return
      this.syncFromValue()
    }
  },
  methods: {
    syncFromValue() {
      const html = this.value || ''
      if (this.$refs.editor && this.$refs.editor.innerHTML !== html) {
        this.$refs.editor.innerHTML = html
      }
    },
    isFocused() {
      return document.activeElement === this.$refs.editor
    },
    emit() {
      if (!this.$refs.editor) return
      this.$emit('input', this.$refs.editor.innerHTML)
    },
    cmd(command, arg) {
      this.$refs.editor.focus()
      document.execCommand(command, false, arg || null)
      this.emit()
    },
    createLink() {
      const url = window.prompt('请输入链接地址', 'https://')
      if (!url) return
      this.cmd('createLink', url)
    },
    toggleSource() {
      this.source = !this.source
      this.$nextTick(() => {
        if (!this.source) this.syncFromValue()
      })
    },
    onSource(e) {
      this.$emit('input', e.target.value)
    }
  }
}
</script>

<style scoped>
.rich-editor {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
}
.re-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}
.re-btn {
  min-width: 28px;
  height: 26px;
  padding: 0 7px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #4b5563;
  font-size: 12px;
  line-height: 24px;
  cursor: pointer;
}
.re-btn:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.re-btn--mode { margin-left: auto; }
.re-sep { width: 1px; height: 20px; background: #e4e7ed; margin: 0 4px; }
.re-spacer { flex: 1; }
.re-body {
  min-height: 180px;
  max-height: 320px;
  overflow-y: auto;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  outline: none;
}
.re-body:empty::before {
  content: attr(data-placeholder);
  color: #c0c4cc;
  pointer-events: none;
}
.re-body img { max-width: 100%; }
.re-source {
  display: block;
  width: 100%;
  min-height: 180px;
  padding: 10px 12px;
  border: 0;
  outline: none;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  resize: vertical;
}
</style>
