<script setup>
import { ref } from 'vue'

/**
 * 密碼輸入欄，含顯示／隱藏切換。
 *
 * 沒有顯示切換是登入表單最常見的缺陷之一：使用者打錯字卻看不到，
 * 只能整欄刪掉重打——在手機上尤其折磨。
 *
 * 預設隱藏，由使用者主動選擇顯示：肩後偷看（shoulder surfing）
 * 仍然是真實情境，不該替使用者決定要不要冒這個險。
 */
defineProps({
  modelValue: { type: String, required: true },
  label: { type: String, required: true },
  /** 新密碼用 new-password，登入用 current-password——影響密碼管理員的行為 */
  autocomplete: { type: String, default: 'current-password' },
  error: { type: String, default: '' },
})

defineEmits(['update:modelValue'])

const revealed = ref(false)
</script>

<template>
  <label>
    {{ label }}
    <span class="field">
      <input
        :type="revealed ? 'text' : 'password'"
        :value="modelValue"
        :autocomplete="autocomplete"
        @input="$emit('update:modelValue', $event.target.value)"
      />
      <button
        type="button"
        class="toggle"
        :aria-pressed="revealed"
        :aria-label="revealed ? '隱藏密碼' : '顯示密碼'"
        @click="revealed = !revealed"
      >
        {{ revealed ? '隱藏' : '顯示' }}
      </button>
    </span>
    <small v-if="error" class="error">{{ error }}</small>
  </label>
</template>

<style scoped>
.field {
  position: relative;
  display: block;
}

/* 右側留出切換鈕的空間，避免文字被蓋住 */
.field input {
  padding-right: 3.75rem;
}

.toggle {
  position: absolute;
  top: 50%;
  right: 0.4rem;
  transform: translateY(-50%);
  padding: 0.3rem 0.55rem;
  border: 0;
  background: none;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--stone-400);
  transition: color var(--dur) var(--ease);
}

.toggle:hover {
  background: none;
  color: var(--jade-700);
}

/* 按下時不套用全域按鈕的位移，否則會在欄位內晃動 */
.toggle:active:not(:disabled) {
  transform: translateY(-50%);
}
</style>
