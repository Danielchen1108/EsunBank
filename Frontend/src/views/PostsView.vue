<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createComment, createPost, deletePost, listPosts, updatePost } from '../api/client.js'

/**
 * 發文與留言畫面（題目 §3 發文、§4 留言）。
 *
 * 一頁涵蓋「列出所有發文／新增／編輯／刪除」與「針對發文新增留言」，
 * 對應後端 F004 與 F005 的全部端點。
 *
 * ── XSS 防護（題目 §6）─────────────────────────────────────────
 * 發文內容、留言內容、使用者名稱都是使用者輸入，且會回顯在這個畫面上。
 * 後端刻意原樣儲存與回傳（不在寫入時改寫使用者資料，避免資料失真），
 * **跳脫的責任在輸出端，也就是這裡。**
 *
 * 本檔一律以 Vue 的 {{ }} 插值輸出，插值預設會把 HTML 特殊字元跳脫，
 * 含 script 標籤的內容會原樣顯示為文字，不會被瀏覽器當成程式碼執行。
 *
 * **絕對不可改用 v-html** —— v-html 會把字串當 HTML 插入 DOM，
 * 等於把本案唯一的 XSS 防線拆掉。此約束記於 F004-API.md 與 F005-API.md § 安全考量。
 * ──────────────────────────────────────────────────────────────
 *
 * 沒有排序：後端 sp_post_list 刻意不寫 ORDER BY（題目未定義排序規則），
 * 前端照回傳順序顯示，不自行排序，避免實作出題目未定義的行為。
 *
 * 沒有留言列表：題目 §4 只要求「新增留言」，列出／編輯／刪除留言皆為 Out of Scope
 * （SCOPE-BOUNDARY.md R-3），後端也沒有對應端點，故留言送出後只回饋成功訊息。
 */
const router = useRouter()

const posts = ref([])
const loading = ref(false)
const listError = ref('')

// 新增發文
const newContent = ref('')
const createError = ref('')
const creating = ref(false)

// 編輯發文：同時只會有一篇進入編輯狀態，故用單一 postId 記錄
const editingId = ref(null)
const editingContent = ref('')
const editError = ref('')
const saving = ref(false)

// 留言：每篇發文各有自己的輸入框與狀態，以 postId 為鍵
const commentDrafts = reactive({})
const commentErrors = reactive({})
const commentSent = reactive({})
const commenting = reactive({})

/**
 * 把 API 錯誤轉成可讀訊息；401 一律導向登入頁。
 *
 * 未登入的處理只做導向：後端對 /api/posts 一律要求登入（deny-by-default），
 * 授權判定在後端。前端不設路由守衛，也不把「看不到畫面」當成安全機制——
 * 那只是體驗優化，擋不住直接呼叫 API 的人。
 */
function toMessage(error) {
  if (error.status === 401) {
    router.push('/login')
    return '請先登入後再操作。'
  }

  // 400 由後端 Bean Validation 產生，errors 為逐欄訊息（此處各表單都只有 content 一欄）
  if (error.status === 400 && error.body?.errors) {
    return error.body.errors.content ?? Object.values(error.body.errors).join('；')
  }

  return error.body?.message ?? error.message
}

/**
 * 載入發文列表。
 *
 * 每次異動後都重新載入：發文可被任何登入者編輯或刪除（BG-4 裁決），
 * 本機狀態隨時可能與伺服器不一致，重新取回是最不會出錯的做法。
 */
async function loadPosts() {
  loading.value = true
  listError.value = ''

  try {
    posts.value = await listPosts()
  } catch (e) {
    listError.value = `無法載入發文：${toMessage(e)}`
  } finally {
    loading.value = false
  }
}

onMounted(loadPosts)

async function onCreate() {
  createError.value = ''
  creating.value = true

  try {
    await createPost({ content: newContent.value })
    newContent.value = ''
    await loadPosts()
  } catch (e) {
    createError.value = toMessage(e)
  } finally {
    creating.value = false
  }
}

function startEdit(post) {
  editingId.value = post.postId
  editingContent.value = post.content
  editError.value = ''
}

function cancelEdit() {
  editingId.value = null
  editingContent.value = ''
  editError.value = ''
}

async function onUpdate(post) {
  editError.value = ''
  saving.value = true

  try {
    await updatePost(post.postId, { content: editingContent.value })
    cancelEdit()
    await loadPosts()
  } catch (e) {
    // 404：發文不存在或已被其他人刪除。列表重新載入後該筆就會消失。
    editError.value = toMessage(e)
    if (e.status === 404) {
      cancelEdit()
      await loadPosts()
    }
  } finally {
    saving.value = false
  }
}

async function onDelete(post) {
  // 確認對話框：後端刻意不檢查發文者身分（BG-4 裁決），任何登入者都能刪除他人發文，
  // 誤觸的代價高，故在送出前多問一次。這是體驗防呆，不是權限控制。
  if (!window.confirm('確定要刪除這篇發文嗎？該發文的留言會一併被刪除。')) {
    return
  }

  try {
    await deletePost(post.postId)
  } catch (e) {
    listError.value = toMessage(e)
  } finally {
    await loadPosts()
  }
}

async function onComment(post) {
  const postId = post.postId
  const content = commentDrafts[postId] ?? ''

  commentErrors[postId] = ''
  commentSent[postId] = false
  commenting[postId] = true

  try {
    await createComment(postId, { content })
    // 清空輸入框並回饋成功。不重新載入留言列表——列出留言不在題目範圍內，
    // 後端沒有可查詢留言的端點。
    commentDrafts[postId] = ''
    commentSent[postId] = true
  } catch (e) {
    commentErrors[postId] = toMessage(e)
    // 404：目標發文不存在或已被刪除，重新載入列表讓畫面與伺服器一致
    if (e.status === 404) {
      await loadPosts()
    }
  } finally {
    commenting[postId] = false
  }
}

/**
 * 發佈時間顯示。
 *
 * 後端回的是不含時區的本地時間字串（例：2026-08-13T20:05:19），
 * 只把 T 換成空白呈現，不交給 Date 解析——沒有時區資訊時，
 * 讓瀏覽器猜時區反而可能把時間位移。
 */
function formatTime(createdAt) {
  return typeof createdAt === 'string' ? createdAt.replace('T', ' ') : ''
}
</script>

<template>
  <section class="page">
    <h1>發文</h1>

    <!-- 新增發文（題目 §3）。送出前的長度檢查與後端同規則，僅為體驗，後端仍會獨立驗證。 -->
    <form class="composer" novalidate @submit.prevent="onCreate">
      <label>
        發文內容
        <textarea
          v-model="newContent"
          maxlength="2000"
          rows="3"
          placeholder="想說點什麼？"
        ></textarea>
        <small class="hint">{{ newContent.length }} / 2000</small>
        <small v-if="createError" class="error">{{ createError }}</small>
      </label>

      <button type="submit" :disabled="creating || newContent.trim() === ''">
        {{ creating ? '發佈中…' : '發佈' }}
      </button>
    </form>

    <p v-if="listError" class="error banner">{{ listError }}</p>
    <p v-else-if="loading" class="hint banner">載入中…</p>
    <p v-else-if="posts.length === 0" class="hint banner">目前沒有發文。</p>

    <!--
      照後端回傳順序顯示，前端不排序（題目未定義排序規則）。
      內容一律用 {{ }} 插值輸出，插值會跳脫 HTML——這是本案的 XSS 防線，不得改用 v-html。
    -->
    <article v-for="post in posts" :key="post.postId" class="post">
      <header>
        <span class="author">{{ post.userName }}</span>
        <span class="time">{{ formatTime(post.createdAt) }}</span>
      </header>

      <form
        v-if="editingId === post.postId"
        class="editor"
        novalidate
        @submit.prevent="onUpdate(post)"
      >
        <textarea v-model="editingContent" maxlength="2000" rows="3"></textarea>
        <small v-if="editError" class="error">{{ editError }}</small>
        <div class="actions">
          <button type="submit" :disabled="saving || editingContent.trim() === ''">
            {{ saving ? '儲存中…' : '儲存' }}
          </button>
          <button type="button" class="secondary" @click="cancelEdit">取消</button>
        </div>
      </form>

      <template v-else>
        <p class="content">{{ post.content }}</p>
        <div class="actions">
          <button type="button" class="secondary" @click="startEdit(post)">編輯</button>
          <button type="button" class="danger" @click="onDelete(post)">刪除</button>
        </div>
      </template>

      <!-- 針對發文新增留言（題目 §4）。只有新增，沒有列出／編輯／刪除。 -->
      <form class="comment" novalidate @submit.prevent="onComment(post)">
        <input
          v-model="commentDrafts[post.postId]"
          maxlength="500"
          placeholder="新增留言…"
          aria-label="留言內容"
        />
        <button
          type="submit"
          :disabled="commenting[post.postId] || !(commentDrafts[post.postId] ?? '').trim()"
        >
          {{ commenting[post.postId] ? '送出中…' : '留言' }}
        </button>
      </form>

      <small v-if="commentErrors[post.postId]" class="error">
        {{ commentErrors[post.postId] }}
      </small>
      <small v-else-if="commentSent[post.postId]" class="success">
        留言已送出。（題目未要求列出留言，故此處不顯示留言內容）
      </small>
    </article>
  </section>
</template>

<style scoped>
.page {
  max-width: 40rem;
  margin: 3rem auto;
  padding: 0 1.5rem;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 2rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

textarea,
input {
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
  padding: 0.5rem 0.9rem;
  border: 0;
  border-radius: 0.3rem;
  background: #0a7d3f;
  color: #fff;
  font: inherit;
  font-size: 0.95rem;
  cursor: pointer;
}

button:disabled {
  background: #999;
  cursor: default;
}

button.secondary {
  background: #eee;
  color: #333;
}

button.danger {
  background: #b3261e;
}

.post {
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
  border: 1px solid #ddd;
  border-radius: 0.5rem;
}

.post header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

.author {
  font-weight: 600;
}

.time {
  color: #888;
  font-size: 0.8rem;
}

.content {
  margin: 0.6rem 0 1rem;
  /* 使用者輸入的換行照原樣呈現；插值仍會跳脫 HTML */
  white-space: pre-wrap;
  word-break: break-word;
}

.editor {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 0.6rem 0 1rem;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

.comment {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 0.9rem;
  border-top: 1px solid #eee;
}

.comment input {
  flex: 1;
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
  padding: 0.75rem 1rem;
  border-radius: 0.3rem;
  background: #f4f4f4;
}
</style>
