<script setup>
/**
 * 資料載入失敗時顯示：原因＋「重新載入」按鈕。
 * 各頁面共用，讓顧客不用自己重新整理整個頁面（手機上不好找重新整理）。
 *
 * 用法：<LoadError :message="errorMessage" @retry="load" />
 */
defineProps({
  message: { type: String, required: true },
})

const emit = defineEmits(['retry'])
</script>

<template>
  <el-result icon="error" title="載入失敗" :sub-title="message" class="load-error">
    <template #extra>
      <el-button type="primary" @click="emit('retry')">重新載入</el-button>
    </template>
  </el-result>
</template>
