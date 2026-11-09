<template>
  <img :src="current" :style="imgStyle" :alt="alt" @error="onError" />
</template>

<script>
// 商品图兜底组件：种子数据的 /img/goods/*.jpg 在前台工程里不存在，
// 404 时回落到本地 placeholder.svg，避免整页破图。
const PLACEHOLDER = '/placeholder.svg'

export default {
  name: 'PcImage',
  props: {
    src: { type: String, default: '' },
    alt: { type: String, default: '商品图片' },
    imgStyle: { type: Object, default: () => ({}) }
  },
  data() {
    return { failed: false }
  },
  computed: {
    current() {
      if (this.failed || !this.src) return PLACEHOLDER
      return this.src
    }
  },
  watch: {
    src() { this.failed = false }
  },
  methods: {
    onError() {
      this.failed = true
    }
  }
}
</script>
