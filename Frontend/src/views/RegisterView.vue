<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/client.js'

/**
 * 註冊畫面（題目 §1）。
 *
 * 前端驗證僅為體驗優化，後端仍會獨立驗證（F002 AC-8）；
 * 資料庫另有 CHECK 約束（F001 AC-10）。三層各自把關。
 */
const form = reactive({
  phone: '',
  userName: '',
  email: '',
  password: '',
  biography: '',
})

const router = useRouter()

const fieldErrors = ref({})
const generalError = ref('')
const successUserId = ref(null)
const submitting = ref(false)

// 僅檢查長度 10 碼——與後端同規則。不驗開頭數字，題目未要求。
const phoneLooksValid = computed(() => form.phone.length === 10)

async function onSubmit() {
  fieldErrors.value = {}
  generalError.value = ''
  successUserId.value = null
  submitting.value = true

  try {
    const result = await register({ ...form })
    successUserId.value = result.userId

    // 註冊不自動登入（題目未要求，F002-REQ.md Non-goals），
    // 但也不該把人留在原地自己找路。帶手機號碼過去，登入頁不必重打。
    setTimeout(() => {
      router.replace({ name: 'login', query: { phone: form.phone } })
    }, 1400)
  } catch (e) {
    if (e.status === 400 && e.body?.errors) {
      fieldErrors.value = e.body.errors
    } else if (e.status === 409) {
      generalError.value = e.body?.message ?? '此手機號碼已被註冊'
    } else {
      generalError.value = `註冊失敗：${e.message}`
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section>
    <h1>註冊</h1>
    <p class="lede">以手機號碼建立帳號，註冊後即可登入發文與留言。</p>

    <form novalidate @submit.prevent="onSubmit">
      <label>
        手機號碼
        <input v-model.trim="form.phone" maxlength="10" autocomplete="tel" />
        <small v-if="fieldErrors.phone" class="error">{{ fieldErrors.phone }}</small>
        <small v-else-if="form.phone && !phoneLooksValid" class="hint">手機號碼須為 10 碼</small>
      </label>

      <label>
        使用者名稱
        <input v-model.trim="form.userName" maxlength="50" />
        <small v-if="fieldErrors.userName" class="error">{{ fieldErrors.userName }}</small>
      </label>

      <label>
        電子郵件
        <input v-model.trim="form.email" maxlength="255" autocomplete="email" />
        <small v-if="fieldErrors.email" class="error">{{ fieldErrors.email }}</small>
      </label>

      <label>
        密碼
        <input v-model="form.password" type="password" autocomplete="new-password" />
        <small v-if="fieldErrors.password" class="error">{{ fieldErrors.password }}</small>
      </label>

      <label>
        自我介紹<span class="optional">（選填）</span>
        <textarea v-model="form.biography" maxlength="500" rows="3"></textarea>
        <small v-if="fieldErrors.biography" class="error">{{ fieldErrors.biography }}</small>
      </label>

      <button type="submit" :disabled="submitting">
        {{ submitting ? '註冊中…' : '註冊' }}
      </button>
    </form>

    <p v-if="generalError" class="error banner">{{ generalError }}</p>

    <p v-if="successUserId" class="success banner" role="status">
      註冊成功，使用者編號 {{ successUserId }}。正在帶你去登入…
    </p>

    <p v-else class="switch">
      已經有帳號了？<RouterLink to="/login">直接登入</RouterLink>
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

.switch {
  max-width: 26rem;
  margin-top: 1.25rem;
  font-size: 0.88rem;
  color: var(--stone-400);
}

.switch a {
  color: var(--jade-700);
  font-weight: 600;
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color var(--dur) var(--ease);
}

.switch a:hover {
  border-bottom-color: var(--jade-700);
}

@media (max-width: 34rem) {
  section {
    padding: 2rem 1rem 4rem;
  }
}
</style>
