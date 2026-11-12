<script setup>
/**
 * 分類選擇：一排可以左右滑動的按鈕（手機上比下拉選單好點）。
 * 用 v-model 綁定目前的分類 id；null 代表「全部」。
 */
defineProps({
  categories: { type: Array, default: () => [] },
})
const selected = defineModel({ type: Number, default: null })
</script>

<template>
  <div class="chips" role="tablist" aria-label="商品分類">
    <el-button
      round
      :type="selected === null ? 'primary' : 'default'"
      role="tab"
      :aria-selected="selected === null"
      @click="selected = null"
    >
      全部
    </el-button>
    <el-button
      v-for="category in categories"
      :key="category.id"
      round
      :type="selected === category.id ? 'primary' : 'default'"
      role="tab"
      :aria-selected="selected === category.id"
      @click="selected = category.id"
    >
      {{ category.name }}
    </el-button>
  </div>
</template>

<style scoped>
.chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
  /* 手機上可以用手指左右滑，不顯示捲軸 */
  scrollbar-width: none;
}

.chips::-webkit-scrollbar {
  display: none;
}

/* Element Plus 預設相鄰按鈕有左邊距，這裡已經用 gap 控制間距 */
.chips :deep(.el-button + .el-button) {
  margin-left: 0;
}

.chips :deep(.el-button) {
  flex-shrink: 0;
}
</style>
