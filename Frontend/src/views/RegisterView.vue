<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/client.js'
import AuthLayout from '../components/AuthLayout.vue'
import PasswordField from '../components/PasswordField.vue'

/**
 * 註冊畫面（需求 §1）。
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

// 僅檢查長度 10 碼——與後端同規則。不驗開頭數字，需求未要求。
const phoneLooksValid = computed(() => form.phone.length === 10)

async function onSubmit() {
  fieldErrors.value = {}
  generalError.value = ''
  successUserId.value = null
  submitting.value = true

  try {
    const result = await register({ ...form })
    successUserId.value = result.userId

    // 註冊不自動登入（需求未要求，F002-REQ.md Non-goals），
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
  <AuthLayout title="註冊" lede="以手機號碼建立帳號，註冊後即可登入。">
    <form novalidate @submit.prevent="onSubmit">
      <label>
        手機號碼
        <!--
          inputmode="numeric" 讓行動裝置直接跳出數字鍵盤。
          type 不用 number：會帶出上下微調鈕，且會吃掉開頭的 0。
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
        <small v-else-if="form.phone && !phoneLooksValid" class="hint">手機號碼須為 10 碼</small>
      </label>

      <label>
        使用者名稱
        <input v-model.trim="form.userName" maxlength="50" autocomplete="nickname" />
        <small v-if="fieldErrors.userName" class="error">{{ fieldErrors.userName }}</small>
      </label>

      <label>
        電子郵件
        <!-- type="email" 讓行動鍵盤帶出 @ 與 .com -->
        <input
          v-model.trim="form.email"
          type="email"
          maxlength="255"
          autocomplete="email"
        />
        <small v-if="fieldErrors.email" class="error">{{ fieldErrors.email }}</small>
      </label>

      <PasswordField
        v-model="form.password"
        label="密碼"
        autocomplete="new-password"
        :error="fieldErrors.password ?? ''"
      />

      <label>
        自我介紹<span class="optional">（選填）</span>
        <textarea v-model="form.biography" maxlength="500" rows="3"></textarea>
        <small v-if="fieldErrors.biography" class="error">{{ fieldErrors.biography }}</small>
      </label>

      <button type="submit" :disabled="submitting">
        {{ submitting ? '註冊中…' : '註冊' }}
      </button>
    </form>

    <p v-if="generalError" class="error banner" role="alert">{{ generalError }}</p>

    <p v-if="successUserId" class="success banner" role="status">
      註冊成功，使用者編號 {{ successUserId }}。正在帶你去登入…
    </p>

    <p v-else class="switch">
      已經有帳號了？<RouterLink to="/login">直接登入</RouterLink>
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
  color: var(--jade-700);
  background: var(--jade-100);
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
