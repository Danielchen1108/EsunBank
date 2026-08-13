<script setup>
/**
 * 應用程式外框。
 *
 * 畫面內容由路由決定，見 src/router/index.js。
 *
 * 頁首的等高線是本作品的識別元素：以同心弧暗示地形，呼應「玉山」之名。
 * **刻意不畫山形剪影**——玉山銀行的商標本身即為山形，重製他人註冊商標並不恰當；
 * 等高線屬地圖語彙，與該商標明確區隔。
 *
 * 頁面切換的淡入淡出使用 Vue 內建的 <Transition>，未引入任何動畫套件：
 * 每個新依賴都是一份要評估的第三方程式碼，而會直接操作 DOM 的動畫庫
 * 更可能牴觸本案唯一的 XSS 防線（一律以 {{ }} 插值輸出、不使用 v-html）。
 */
</script>

<template>
  <header>
    <!-- 等高線：純裝飾，對輔助技術隱藏 -->
    <svg
      class="contour"
      viewBox="0 0 1200 90"
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      <!--
        巢狀弧線共用起訖點、峰高遞增——這是等高線地圖描述一座山峰的方式。
        用地圖語彙而非山形剪影，與玉山銀行的商標圖形保持距離。
      -->
      <g fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round">
        <path d="M-40 92 Q 620 74 1240 92" opacity=".34" />
        <path d="M-40 92 Q 620 58 1240 92" opacity=".27" />
        <path d="M-40 92 Q 620 42 1240 92" opacity=".20" />
        <path d="M-40 92 Q 620 26 1240 92" opacity=".14" />
        <path d="M-40 92 Q 620 10 1240 92" opacity=".09" />
      </g>
    </svg>

    <div class="bar">
      <RouterLink to="/posts" class="brand">
        社群媒體系統
        <span class="brand-sub mono">E.SUN take-home</span>
      </RouterLink>

      <nav>
        <RouterLink to="/register">註冊</RouterLink>
        <RouterLink to="/login">登入</RouterLink>
        <!-- 未登入也顯示：能不能看由後端回 401 決定，前端不自行判斷權限 -->
        <RouterLink to="/posts">發文</RouterLink>
        <RouterLink to="/health">連線狀態</RouterLink>
      </nav>
    </div>
  </header>

  <RouterView v-slot="{ Component }">
    <Transition name="fade" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
</template>

<style scoped>
header {
  position: relative;
  background: var(--mist-0);
  border-bottom: 1px solid var(--stone-200);
  overflow: hidden;
}

.contour {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  color: var(--jade-700);
  pointer-events: none;
}

.bar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  max-width: 46rem;
  margin: 0 auto;
  padding: 1rem 1.5rem;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  font-weight: 700;
  font-size: 1.05rem;
  letter-spacing: -0.02em;
  color: var(--jade-950);
  text-decoration: none;
}

.brand-sub {
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--stone-400);
}

nav {
  display: flex;
  gap: 1.25rem;
}

nav a {
  position: relative;
  padding: 0.15rem 0;
  font-size: 0.9rem;
  color: var(--stone-400);
  text-decoration: none;
  transition: color var(--dur) var(--ease);
}

nav a:hover {
  color: var(--jade-700);
}

/* 目前頁面的底線由中央展開，而非直接出現——移動比閃現容易被眼睛追上 */
nav a::after {
  content: "";
  position: absolute;
  inset: auto 0 -2px 0;
  height: 2px;
  background: var(--jade-700);
  transform: scaleX(0);
  transition: transform var(--dur) var(--ease);
}

nav a.router-link-active {
  color: var(--jade-950);
  font-weight: 600;
}

nav a.router-link-active::after {
  transform: scaleX(1);
}

@media (max-width: 34rem) {
  .bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }

  nav {
    flex-wrap: wrap;
    gap: 1rem;
  }
}
</style>
