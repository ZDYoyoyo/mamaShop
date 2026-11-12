<script setup>
/** 商品搜尋框：按 Enter 或放大鏡送出，觸發 search 事件（帶去除空白後的關鍵字） */
import { Search } from '@element-plus/icons-vue'

const keyword = defineModel({ type: String, default: '' })
const emit = defineEmits(['search'])

function submit() {
  emit('search', keyword.value.trim())
}
</script>

<template>
  <form class="search" role="search" @submit.prevent="submit">
    <el-input
      v-model="keyword"
      placeholder="搜尋商品名稱"
      maxlength="50"
      clearable
      enterkeyhint="search"
      aria-label="搜尋商品名稱"
    >
      <template #append>
        <el-button :icon="Search" native-type="submit" aria-label="搜尋" />
      </template>
    </el-input>
  </form>
</template>
