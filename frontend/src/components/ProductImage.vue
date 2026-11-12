<script setup>
/**
 * 商品圖片：沒有圖片（或圖片載入失敗）時顯示灰色的「暫無圖片」，固定 1:1 比例，版面不會跳動。
 */
import { ref, watch } from 'vue'

const props = defineProps({
  src: { type: String, default: null },
  alt: { type: String, default: '' },
})

const failed = ref(false)
watch(
  () => props.src,
  () => (failed.value = false),
)
</script>

<template>
  <div class="product-image">
    <img v-if="src && !failed" :src="src" :alt="alt" loading="lazy" @error="failed = true" />
    <div v-else class="placeholder" aria-label="暫無圖片">暫無圖片</div>
  </div>
</template>

<style scoped>
.product-image {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 8px;
  background: var(--color-surface);
}

img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-muted);
  font-size: 0.85rem;
}
</style>
