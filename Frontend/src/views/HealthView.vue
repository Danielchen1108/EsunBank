<script setup>
import { onMounted, ref } from 'vue'
import { fetchHealth } from '../api/client.js'

/**
 * R101 專案骨架驗證畫面。
 *
 * 用途：確認三層式架構（題目 §5）確實連通——
 * Vue 前端 → Spring Boot → MySQL。
 *
 * 功能畫面（註冊、登入、發文、留言）將於 F002–F005 加入，屆時本畫面移除。
 */
const status = ref(null)
const error = ref(null)

onMounted(async () => {
  try {
    status.value = await fetchHealth()
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <section>
    <h1>社群媒體系統</h1>
    <p class="subtitle">玉山銀行後端工程師實作題 — R101 專案骨架</p>

    <section class="panel">
      <h2>三層連通檢查</h2>

      <p v-if="error" class="down">
        無法連線至 Application Server：{{ error }}
        <br />
        <small>請確認 Spring Boot 已於 8080 埠啟動。</small>
      </p>

      <p v-else-if="!status">檢查中…</p>

      <dl v-else>
        <dt>Web Server（Vite）</dt>
        <dd class="up">UP</dd>

        <dt>Application Server（Spring Boot）</dt>
        <dd :class="status.application === 'UP' ? 'up' : 'down'">{{ status.application }}</dd>

        <dt>資料庫（MySQL）</dt>
        <dd :class="status.database === 'UP' ? 'up' : 'down'">{{ status.database }}</dd>
      </dl>
    </section>
  </section>
</template>

<style scoped>
section {
  max-width: 40rem;
  margin: 4rem auto;
  padding: 0 1.5rem;
}

h1 {
  margin-bottom: 0.25rem;
}

.subtitle {
  margin-top: 0;
  color: #666;
}

.panel {
  margin-top: 2rem;
  padding: 1.25rem 1.5rem;
  border: 1px solid #ddd;
  border-radius: 0.5rem;
}

h2 {
  margin-top: 0;
  font-size: 1rem;
}

dl {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.5rem 1rem;
  margin: 0;
}

dd {
  margin: 0;
  font-weight: 600;
}

.up {
  color: #0a7d3f;
}

.down {
  color: #b3261e;
}
</style>
