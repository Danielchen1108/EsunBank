<script setup>
/**
 * 登入／註冊的共用版型。
 *
 * ── 為什麼這兩頁沒有導覽列 ─────────────────────────────────
 * 導覽列是給「已經進到系統裡」的人用的。在登入頁頂一條寫著「登入／註冊」的列，
 * 等於把使用者當下唯一該做的事，降級成選單裡的一個選項。
 * 主流社群平台的登入頁都是獨立全頁——畫面上只留一件事可做。
 *
 * ── 版面 ───────────────────────────────────────────────
 * 寬螢幕左右分欄：左邊是識別（等高線與名稱），右邊是表單。
 * 窄螢幕收成單欄，識別縮成一行標題——手機上沒有多餘空間留給氣氛。
 *
 * 刻意不放背景大圖：登入頁是使用者遇到的第一個畫面，
 * 為了氣氛拖慢首次載入是划不來的交換（見 UX 慣例：避免以行動裝置效能換取視覺）。
 * 這裡的識別是幾條 SVG 路徑，成本接近零。
 */
defineProps({
  /** 表單標題，例如「登入」 */
  title: { type: String, required: true },
  /** 標題下方的一句說明 */
  lede: { type: String, default: '' },
})
</script>

<template>
  <div class="auth">
    <aside class="brand">
      <svg
        class="contour"
        viewBox="0 0 400 260"
        preserveAspectRatio="xMidYMax slice"
        aria-hidden="true"
        focusable="false"
      >
        <!-- 共用起訖點、峰高遞增的巢狀弧：等高線地圖描述山峰的方式 -->
        <g fill="none" stroke="currentColor" stroke-width="1.1" stroke-linecap="round">
          <path d="M-30 258 Q 200 214 430 258" opacity=".38" />
          <path d="M-30 258 Q 200 178 430 258" opacity=".31" />
          <path d="M-30 258 Q 200 142 430 258" opacity=".25" />
          <path d="M-30 258 Q 200 106 430 258" opacity=".19" />
          <path d="M-30 258 Q 200 70 430 258" opacity=".13" />
          <path d="M-30 258 Q 200 34 430 258" opacity=".08" />
        </g>
      </svg>

      <div class="brand-text">
        <p class="brand-name">社群媒體系統</p>
        <p class="brand-sub mono">E.SUN take-home</p>
        <p class="brand-note">以手機號碼註冊，登入後即可發文與留言。</p>
      </div>
    </aside>

    <main class="panel">
      <div class="panel-inner">
        <h1>{{ title }}</h1>
        <p v-if="lede" class="lede">{{ lede }}</p>
        <slot />
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  /* 左欄固定比例、右欄可伸縮——表單的行長比識別區重要 */
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  min-height: 100dvh;
}

/* ── 識別區 ───────────────────────────────────────── */
.brand {
  position: relative;
  display: flex;
  align-items: center;
  padding: 3rem;
  background: var(--mist-0);
  border-right: 1px solid var(--stone-200);
  overflow: hidden;
}

/* 等高線佔左欄下半部：識別文字在上、地形在下，兩者不互相干擾 */
.contour {
  position: absolute;
  inset: 45% 0 0 0;
  width: 100%;
  color: var(--jade-700);
  pointer-events: none;
}

.brand-text {
  position: relative;
  max-width: 20rem;
}

.brand-name {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--jade-950);
}

.brand-sub {
  margin: 0.15rem 0 0;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.brand-note {
  margin: 1.25rem 0 0;
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--stone-400);
}

/* ── 表單區 ───────────────────────────────────────── */
.panel {
  display: flex;
  align-items: center;
  padding: 3rem;
}

.panel-inner {
  width: 100%;
  max-width: 24rem;
}

h1 {
  margin-bottom: 0.35rem;
}

.lede {
  margin: 0 0 1.75rem;
  font-size: 0.9rem;
  color: var(--stone-400);
}

/*
 * ── 窄螢幕 ───────────────────────────────────────────
 * 分欄收成單欄，識別區壓成一行標題列。
 * 直向手機上不留大面積氣氛區——使用者要的是趕快填完表單。
 */
@media (max-width: 52rem) {
  .auth {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .brand {
    align-items: center;
    padding: 1.25rem 1.5rem;
    border-right: 0;
    border-bottom: 1px solid var(--stone-200);
  }

  .contour {
    inset: 0;
  }

  .brand-note {
    display: none;
  }

  .brand-text {
    max-width: none;
  }

  .brand-name {
    font-size: 1.05rem;
  }

  .panel {
    padding: 2.5rem 1.5rem 4rem;
  }

  .panel-inner {
    max-width: 26rem;
    margin: 0 auto;
  }
}

@media (max-width: 26rem) {
  .brand {
    padding: 1rem 1.15rem;
  }

  .panel {
    padding: 2rem 1.15rem 3.5rem;
  }
}
</style>
