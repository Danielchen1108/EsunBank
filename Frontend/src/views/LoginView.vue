<script setup>
import { reactive, ref } from 'vue'
import { isLoggedIn, login } from '../api/client.js'

/**
 * 登入畫面（題目 §2）。
 *
 * 登入後前端只保存憑證，不保存密碼——密碼在送出後即不再留存於任何地方。
 *
 * 沒有登出按鈕、沒有「記住我」：題目未提及，依 SCOPE-BOUNDARY.md R-3 不實作。
 */
const form = reactive({
  phone: '',
  password: '',
})

const fieldErrors = ref({})
const generalError = ref('')
const loggedInUserId = ref(null)
const submitting = ref(false)

// 進入畫面時若已有憑證，直接反映狀態，避免使用者重複登入
const alreadyLoggedIn = ref(isLoggedIn())

async function onSubmit() {
  fieldErrors.value = {}
  generalError.value = ''
  loggedInUserId.value = null
  submitting.value = true

  try {
    const result = await login({ ...form })
    loggedInUserId.value = result.userId
    alreadyLoggedIn.value = true
    form.password = ''
  } catch (e) {
    if (e.status === 400 && e.body?.errors) {
      fieldErrors.value = e.body.errors
    } else if (e.status === 401) {
      // 後端刻意不區分「查無此手機號碼」與「密碼錯誤」，前端照原訊息呈現
      generalError.value = e.body?.message ?? '手機號碼或密碼錯誤'
    } else {
      generalError.value = `登入失敗：${e.message}`
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section>
    <h1>登入</h1>

    <form novalidate @submit.prevent="onSubmit">
      <label>
        手機號碼
        <input v-model.trim="form.phone" maxlength="10" autocomplete="tel" />
        <small v-if="fieldErrors.phone" class="error">{{ fieldErrors.phone }}</small>
      </label>

      <label>
        密碼
        <input v-model="form.password" type="password" autocomplete="current-password" />
        <small v-if="fieldErrors.password" class="error">{{ fieldErrors.password }}</small>
      </label>

      <button type="submit" :disabled="submitting">
        {{ submitting ? '登入中…' : '登入' }}
      </button>
    </form>

    <p v-if="generalError" class="error banner">{{ generalError }}</p>

    <p v-if="loggedInUserId" class="success banner">
      登入成功，使用者編號 {{ loggedInUserId }}。
    </p>

    <p v-else-if="alreadyLoggedIn" class="hint banner">
      目前為已登入狀態，可直接發文或留言。
    </p>
  </section>
</template>

<style scoped>
/*
 * 表單頁（註冊／登入）共用版型：窄欄、置中偏上。
 * 不置中於垂直中線——表單長度不一，靠上對齊時各頁的視覺起點一致。
 */
/*
 * 與其他頁面共用 46rem 的內容欄，表單本身再收窄至 26rem 並靠左。
 * 表單置中於自己的窄欄看似合理，但左緣會與頁首的品牌區錯開——
 * 同一條垂直基準線貫穿所有頁面，比每頁各自置中更安定。
 */
section {
  max-width: 46rem;
  margin: 0 auto;
  padding: 3rem 1.5rem 5rem;
}

form,
h1,
.lede,
.banner {
  max-width: 26rem;
}

h1 {
  margin-bottom: 0.35rem;
}

.lede {
  margin: 0 0 2rem;
  font-size: 0.9rem;
  color: var(--stone-400);
}

form {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  padding: 1.5rem;
  background: var(--mist-0);
  border: 1px solid var(--stone-200);
  border-top: 3px solid var(--jade-700);
  border-radius: var(--r-lg);
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.85rem;
  font-weight: 600;
}

.optional {
  font-weight: 400;
  color: var(--stone-400);
}

small {
  font-weight: 400;
  font-size: 0.8rem;
}

.error {
  color: var(--clay-600);
}

.hint {
  color: var(--stone-400);
}

.success {
  color: var(--jade-700);
}

.banner {
  margin-top: 1.25rem;
  padding: 0.85rem 1rem;
  border-radius: var(--r-md);
  font-size: 0.9rem;
}

.banner.error {
  background: var(--clay-50);
}

.banner.success {
  background: var(--jade-100);
}

@media (max-width: 34rem) {
  section {
    padding: 2rem 1rem 4rem;
  }
}
</style>
