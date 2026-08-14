<script setup>
import { useRoute, useRouter } from 'vue-router'
import { authState, logout } from './api/client.js'

/**
 * 應用程式外框。
 *
 * 畫面內容由路由決定，見 src/router/index.js。
 *
 * 頁首的等高線是本作品的識別元素：以同心弧暗示地形，呼應主色調的高山意象。
 * **刻意不畫山形剪影**——山形是不少品牌的商標構圖，容易撞上他人的註冊標誌；
 * 等高線屬地圖語彙，辨識度足夠且不與任何商標混淆。
 *
 * 動態一律使用 Vue 內建的 <Transition> 與 CSS，未引入任何動畫套件：
 * 每個新依賴都是一份要評估的第三方程式碼，而會直接操作 DOM 的動畫庫
 * 更可能牴觸本案唯一的 XSS 防線（一律以 {{ }} 插值輸出、不使用 v-html）。
 *
 * ── 為什麼頁面切換沒有過場動畫 ─────────────────────────────
 * 曾在 <RouterView> 外包一層淡入淡出，實測後移除。
 *
 * Vue 的 Transition 靠 requestAnimationFrame 移除 enter-from class；
 * 分頁在背景時 rAF 被瀏覽器節流，class 不會被移除，
 * 整個頁面就停在 opacity: 0——**使用者以新分頁開啟連結，看到的是一片空白**。
 *
 * 根本問題在於：整頁淡入等於「頁面預設不可見，靠 JS 讓它可見」。
 * 一個 240ms 的過場不值得用「可能全白」當代價。
 * 內容的可見性不應該是任何動畫的副產物。
 *
 * 列表動畫（TransitionGroup）保留：它動的是已經可見的頁面裡的單一項目，
 * 即使動畫沒跑完，最壞情況也只是該項目沒有動畫，而不是整頁消失。
 *
 * 導覽列依登入狀態切換，並在登入／註冊頁完全不出現（route.meta.hideChrome）。
 * **選單不是權限控制**——真正的把關在後端，這裡只是不讓使用者看到對他無效的選項。
 */
const router = useRouter()
const route = useRoute()

function onLogout() {
  logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <header v-if="!route.meta.hideChrome">
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
        用地圖語彙而非山形剪影，與各家品牌常見的山形商標保持距離。
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
      <RouterLink to="/posts" class="brand">社群媒體系統</RouterLink>

      <nav>
        <template v-if="authState.loggedIn">
          <RouterLink to="/posts">發文</RouterLink>
          <span class="who" :title="authState.userName">{{ authState.userName }}</span>
          <button type="button" class="link" @click="onLogout">登出</button>
        </template>

        <template v-else>
          <RouterLink to="/login">登入</RouterLink>
          <RouterLink to="/register">註冊</RouterLink>
        </template>
      </nav>
    </div>
  </header>

  <RouterView />
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
  font-weight: 700;
  font-size: 1.05rem;
  letter-spacing: -0.02em;
  color: var(--jade-950);
  text-decoration: none;
}


/*
 * baseline 而非預設的 stretch：導覽列是一排文字，該對齊的是文字基線。
 * stretch 會讓每個項目撐成同高的盒子，內距不同的項目（連結有 padding、
 * 使用者名稱沒有）文字就會落在不同高度——差 2px 肉眼就看得出來。
 */
nav {
  display: flex;
  align-items: baseline;
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

/* 目前登入者：與導覽連結以一道細分隔線區隔，這不是可以點的選項 */
.who {
  max-width: 9rem;
  padding-left: 1.25rem;
  border-left: 1px solid var(--stone-200);
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--jade-950);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 登出是動作不是導覽，但視覺上屬於這一列，故做成連結的樣子 */
button.link {
  padding: 0.15rem 0;
  border: 0;
  background: none;
  font-size: 0.9rem;
  font-weight: 400;
  color: var(--stone-400);
  transition: color var(--dur) var(--ease);
}

button.link:hover {
  background: none;
  color: var(--clay-600);
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
