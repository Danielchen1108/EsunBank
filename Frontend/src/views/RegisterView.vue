<script setup>
import { computed, reactive, ref } from 'vue'
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

    <p v-if="successUserId" class="success banner">
      註冊成功，使用者編號 {{ successUserId }}。
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

input,
textarea {
  padding: 0.5rem 0.6rem;
  border: 1px solid #ccc;
  border-radius: 0.3rem;
  font: inherit;
  font-size: 1rem;
}

textarea {
  resize: vertical;
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

.optional {
  color: #888;
  font-weight: 400;
}

.error {
  color: #b3261e;
}

.hint {
  color: #888;
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
