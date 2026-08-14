<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/client.js'
import AuthLayout from '../components/AuthLayout.vue'
import PasswordField from '../components/PasswordField.vue'

/**
 * 登入畫面。
 *
 * 登入後前端只保存憑證與顯示名稱，不保存密碼——密碼送出後不再留存於任何地方。
 *
 * 登入成功即導向使用者原本要去的頁面（由路由守衛寫在 ?redirect），
 * 沒有指定時回發文牆。**不讓使用者自己找路**：他點「發文」被帶來這裡，
 * 登入完就該回到發文，而不是停在一則「登入成功」的訊息上。
 *
 * 沒有「記住我」：需求未提及，依 R-3 不實作。
 */
const router = useRouter()
const route = useRoute()
// 從註冊頁帶過來的手機號碼——剛註冊完的人不必再打一次
const form = reactive({
  phone: typeof route.query.phone === 'string' ? route.query.phone : '',
  password: '',
})

const fieldErrors = ref({})
const generalError = ref('')
const submitting = ref(false)

// 已登入者不會走到這個畫面——路由守衛會先把他導向發文牆

async function onSubmit() {
  fieldErrors.value = {}
  generalError.value = ''
  submitting.value = true

  try {
    await login({ ...form })
    form.password = ''

    // 回到使用者原本要去的地方；沒有指定時回發文牆。
    // replace 而非 push：登入頁不該留在上一頁的歷史裡，
    // 否則使用者按上一頁會回到一個已經登入、進不去的畫面。
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/posts'
    await router.replace(target)
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
  <AuthLayout title="登入" lede="使用註冊時的手機號碼登入。">
    <form novalidate @submit.prevent="onSubmit">
      <label>
        手機號碼
        <!--
          inputmode="numeric" 讓行動裝置直接跳出數字鍵盤——
          手機號碼欄位卻要使用者自己切換鍵盤，是很容易被忽略的摩擦。
          type 仍為 text：type="number" 會帶出上下微調鈕，且會吃掉開頭的 0。
        -->
        <input
          v-model.trim="form.phone"
          type="text"
          inputmode="numeric"
          maxlength="10"
          autocomplete="tel"
          placeholder="09xxxxxxxx"
        />
        <small v-if="fieldErrors.phone" class="error">{{ fieldErrors.phone }}</small>
      </label>

      <PasswordField
        v-model="form.password"
        label="密碼"
        autocomplete="current-password"
        :error="fieldErrors.password ?? ''"
      />

      <button type="submit" :disabled="submitting">
        {{ submitting ? '登入中…' : '登入' }}
      </button>
    </form>

    <p v-if="generalError" class="error banner" role="alert">{{ generalError }}</p>

    <p class="switch">
      還沒有帳號？<RouterLink to="/register">註冊一個</RouterLink>
    </p>
  </AuthLayout>
</template>

<style scoped>
form {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.85rem;
  font-weight: 600;
}

small {
  font-weight: 400;
  font-size: 0.8rem;
}

.error {
  color: var(--clay-600);
}

.banner {
  margin-top: 1.25rem;
  padding: 0.85rem 1rem;
  border-radius: var(--r-md);
  background: var(--clay-50);
  font-size: 0.9rem;
}

.switch {
  margin-top: 1.75rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--stone-200);
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
</style>
