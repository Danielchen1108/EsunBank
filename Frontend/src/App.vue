<script setup>
/**
 * 應用程式外框。
 *
 * 畫面內容由路由決定，見 src/router/index.js。
 */
</script>

<template>
  <header>
    <span class="brand">社群媒體系統</span>
    <nav>
      <RouterLink to="/register">註冊</RouterLink>
      <RouterLink to="/health">連線狀態</RouterLink>
    </nav>
  </header>

  <RouterView />
</template>

<style scoped>
header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1.5rem;
  border-bottom: 1px solid #e5e5e5;
}

.brand {
  font-weight: 600;
}

nav {
  display: flex;
  gap: 1rem;
}

nav a {
  color: #444;
  text-decoration: none;
  font-size: 0.9rem;
}

nav a.router-link-active {
  color: #0a7d3f;
  font-weight: 600;
}
</style>
