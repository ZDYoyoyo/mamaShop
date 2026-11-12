<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'

// F01 暫時的首頁：確認前端 → Vite proxy → 後端 → 資料庫整條路是通的。F02 會換成真正的首頁。
const categories = ref([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    categories.value = await http.get('/categories')
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section>
    <h1>mamaShop</h1>
    <p v-if="loading">連線中…</p>
    <p v-else-if="errorMessage" class="error">{{ errorMessage }}</p>
    <template v-else>
      <p>已連上後端，共 {{ categories.length }} 個分類：</p>
      <ul>
        <li v-for="category in categories" :key="category.id">{{ category.name }}</li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.error {
  color: var(--color-danger);
}
</style>
