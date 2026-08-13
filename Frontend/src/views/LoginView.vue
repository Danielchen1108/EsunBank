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
section {
  max-width: 26rem;
  margin: 3rem auto;
  padding: 0 1.5rem;
}

form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

input {
  padding: 0.5rem 0.6rem;
  border: 1px solid #ccc;
  border-radius: 0.3rem;
  font: inherit;
  font-size: 1rem;
}

button {
  padding: 0.6rem;
  border: 0;
  border-radius: 0.3rem;
  background: #0a7d3f;
  color: #fff;
  font: inherit;
  font-size: 1rem;
  cursor: pointer;
}

button:disabled {
  background: #999;
  cursor: default;
}

.error {
  color: #b3261e;
}

.hint {
  color: #666;
}

.success {
  color: #0a7d3f;
}

.banner {
  margin-top: 1.25rem;
  padding: 0.75rem 1rem;
  border-radius: 0.3rem;
  background: #f4f4f4;
}
</style>
