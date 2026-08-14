<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createComment, createPost, deletePost, listPosts, updatePost } from '../api/client.js'

/**
 * 發文與留言畫面（發文與留言）。
 *
 * 一頁涵蓋「列出所有發文／新增／編輯／刪除」與「針對發文新增留言」，
 * 對應後端發文與留言的全部端點。
 *
 * ── XSS 防護─────────────────────────────────────────
 * 發文內容、留言內容、使用者名稱都是使用者輸入，且會回顯在這個畫面上。
 * 後端刻意原樣儲存與回傳（不在寫入時改寫使用者資料，避免資料失真），
 * **跳脫的責任在輸出端，也就是這裡。**
 *
 * 本檔一律以 Vue 的 {{ }} 插值輸出，插值預設會把 HTML 特殊字元跳脫，
 * 含 script 標籤的內容會原樣顯示為文字，不會被瀏覽器當成程式碼執行。
 *
 * **絕對不可改用 v-html** —— v-html 會把字串當 HTML 插入 DOM，
 * 等於把本案唯一的 XSS 防線拆掉。此約束記於 與 安全考量。
 * ──────────────────────────────────────────────────────────────
 *
 * 排序一律由後端決定，前端照收不重排。發文由新到舊（sp_post_list），
 * 單則發文內的留言由舊到新（sp_comment_list_visible）——方向相反是刻意的：
 * 動態的通例是最新在最上面，但一段對話要從頭往下讀。
 * 排序規則只寫在 SQL 一個地方，前端再排一次的話規則會散在兩種語言中。
 *
 * 留言列表：由 GET /api/posts 隨發文一併帶回（一次請求拿齊，避免每篇再打一次 API 的 N+1）。
 * 預設只顯示最後 3 則，其餘由「顯示全部」展開。
 *
 * 留言的編輯與刪除仍不做：需求只寫「新增留言」，
 * 後端也沒有對應端點。
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
/**
 * 目前展開的「⋯」選單所屬的發文。
 *
 * 編輯與刪除收進選單，是現行社群平台的常見做法：
 * 瀏覽時看到的是內容，不是每則貼文都掛著兩顆操作鈕。
 * 破壞性動作藏一層，誤觸成本也低一些。
 */
const openMenuId = ref(null)

/**
 * 正在確認刪除的發文。
 *
 * 不用 window.confirm：原生對話框會凍結整個分頁、樣式不受控、
 * 在行動裝置上的呈現各家不一。改用畫面內確認，動作與後果留在同一個卡片裡。
 */
const confirmingDeleteId = ref(null)

const commentDrafts = reactive({})
const commentErrors = reactive({})
const commenting = reactive({})

/**
 * 每篇發文預設顯示的留言則數。
 *
 * 動態牆是一路往下滑的，每篇都攤開全部留言會讓發文本身被淹沒；
 * 留三則足以看出「這裡有對話」，想看全部的人再展開。
 */
const COMMENT_PREVIEW = 3

/**
 * 已展開留言的發文，以 postId 為鍵。
 *
 * 刻意獨立於 posts：loadPosts() 只換掉 posts.value，不會動到這個物件，
 * 所以送出留言後重新載入列表，使用者剛剛展開的留言不會被收回去。
 */
const expandedComments = reactive({})

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
 * 每次異動後都重新載入：發文可被任何登入者編輯或刪除，
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

/**
 * 相對時間的「現在」。
 *
 * 這是個會跳動的反應式來源：formatRelativeTime() 讀它，所以每次更新
 * Vue 就會重繪畫面上所有的時間。少了它，剛發的文會一直停在「剛剛」，
 * 直到使用者重新整理為止。
 *
 * 每分鐘更新一次即可——最小的顯示單位就是分鐘，再密只是白跑。
 */
const now = ref(Date.now())
let clockTimer = null

onMounted(() => {
  loadPosts()
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, 60_000)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
  clearInterval(clockTimer)
})

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

function toggleMenu(postId) {
  openMenuId.value = openMenuId.value === postId ? null : postId
  confirmingDeleteId.value = null
}

function closeOverlays() {
  openMenuId.value = null
  confirmingDeleteId.value = null
}

/** 點到卡片以外的地方就收起選單——使用者不必特地找關閉鈕。 */
function onDocumentClick() {
  openMenuId.value = null
}

/** Esc 收起選單與確認：鍵盤使用者不該被困在展開狀態裡。 */
function onKeydown(event) {
  if (event.key === 'Escape') {
    closeOverlays()
  }
}

function askDelete(post) {
  openMenuId.value = null
  confirmingDeleteId.value = post.postId
}

function cancelDelete() {
  confirmingDeleteId.value = null
}

function startEdit(post) {
  openMenuId.value = null
  confirmingDeleteId.value = null
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

/**
 * 執行刪除（已通過畫面內確認）。
 *
 * 確認這一步是體驗防呆，不是權限控制——後端刻意不檢查發文者身分，
 * 任何登入者都能刪除他人發文，誤觸的代價高，故在送出前多問一次。
 */
async function onDelete(post) {
  confirmingDeleteId.value = null

  try {
    await deletePost(post.postId)
  } catch (e) {
    listError.value = toMessage(e)
  } finally {
    await loadPosts()
  }
}

/**
 * 該篇發文目前要顯示的留言。
 *
 * 未展開時只取尾端 COMMENT_PREVIEW 則：後端由舊到新排序，尾端即最新，
 * 與展開後的順序一致，展開時不會有內容跳位。
 *
 * 仍容忍 comments 缺欄位（?? []）：這支畫面與後端各自部署，
 * 對著舊版後端時應該是「沒有留言」，而不是整頁壞掉。
 */
function visibleComments(post) {
  const comments = post.comments ?? []

  if (expandedComments[post.postId] || comments.length <= COMMENT_PREVIEW) {
    return comments
  }

  return comments.slice(-COMMENT_PREVIEW)
}

function commentCount(post) {
  return post.comments?.length ?? 0
}

/** 展開／收合留言。純前端切換——留言已隨發文一併載入，不需要再發請求。 */
function toggleComments(postId) {
  expandedComments[postId] = !expandedComments[postId]
}

async function onComment(post) {
  const postId = post.postId
  const content = commentDrafts[postId] ?? ''

  commentErrors[postId] = ''
  commenting[postId] = true

  try {
    await createComment(postId, { content })
    // 清空輸入框後重新載入：新留言直接出現在下方的列表裡，那就是最好的回饋，
    // 不需要另外一行「已送出」的訊息（也就不會有訊息清不掉的問題）。
    commentDrafts[postId] = ''
    await loadPosts()
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

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * 發佈時間顯示：近期用相對時間，久遠的用絕對日期。
 *
 *   < 1 分鐘   剛剛
 *   < 1 小時   N 分鐘前
 *   < 1 天     N 小時前
 *   < 7 天     N 天前
 *   >= 7 天    2026-08-05
 *
 * 超過 7 天就不再用相對時間：「93 天前」要心算才知道是什麼時候，
 * 到那個距離絕對日期反而好判讀。這是現行社群平台的共同做法。
 *
 * 時區前提：後端回的是不含時區位移的本地時間字串（例：2026-08-13T20:05:19）。
 * 要算相對時間就非得解析成 Date 不可，而 JS 規定「不帶位移的 date-time 字串」
 * 按瀏覽器本地時區解讀。因此顯示正確的前提是
 * 「MySQL 主機時鐘的時區 == 瀏覽器時區」——本專案前後端與 DB 都在同一台機器上
 * （見 README 的啟動步驟），前提成立。跨時區部署時這裡會偏移，
 * 屆時要改成由後端回帶位移的時間，而不是在前端補償。
 *
 * 絕對日期那一段刻意直接取字串前 10 碼，不走 toLocaleDateString()——
 * 已經解析過一次的東西不需要再讓瀏覽器換算一次時區，徒增出錯的機會。
 */
function formatRelativeTime(createdAt) {
  if (typeof createdAt !== 'string') return ''

  const timestamp = new Date(createdAt).getTime()
  if (Number.isNaN(timestamp)) return ''

  // 取 0 下限：DB 與瀏覽器的時鐘可能差幾秒，未來時間顯示為「剛剛」，
  // 而不是「-1 分鐘前」。
  const elapsed = Math.max(0, now.value - timestamp)

  if (elapsed < MINUTE) return '剛剛'
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)} 分鐘前`
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)} 小時前`
  if (elapsed < 7 * DAY) return `${Math.floor(elapsed / DAY)} 天前`

  return createdAt.slice(0, 10)
}

/** 完整時間，放進 title 供滑鼠停留時查看；畫面上只顯示相對時間。 */
function formatExactTime(createdAt) {
  return typeof createdAt === 'string' ? createdAt.replace('T', ' ') : ''
}
</script>

<template>
  <section class="page">
    <h1>發文</h1>

    <!-- 新增發文。送出前的長度檢查與後端同規則，僅為體驗，後端仍會獨立驗證。 -->
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
      照後端回傳順序顯示（由新到舊），前端不重排。
      內容一律用 {{ }} 插值輸出，插值會跳脫 HTML——這是本案的 XSS 防線，不得改用 v-html。
    -->
    <!--
      刻意不用 <TransitionGroup> 包這個列表。

      Vue 的 transition 機制以 requestAnimationFrame 推進（套用 -to class、
      判定結束都靠它）。分頁在背景時 rAF 被瀏覽器節流，離場動畫永遠不完成，
      **已刪除的發文就不會從 DOM 移除，一直留在畫面上**。
      實測確認：改 CSS 為 transition: none 也無效，問題在推進機制不在時長。

      這與先前移除整頁過場是同一個根因，判準相同：
      **內容的存在與否，不該取決於動畫跑不跑得完。**
    -->
    <div class="feed">
    <article v-for="post in posts" :key="post.postId" class="post">
      <header>
        <div class="meta">
          <span class="author">{{ post.userName }}</span>
          <time
            class="time"
            :datetime="post.createdAt"
            :title="formatExactTime(post.createdAt)"
          >{{ formatRelativeTime(post.createdAt) }}</time>
        </div>

        <!--
          編輯與刪除收進「⋯」選單：瀏覽時看到的應該是內容，
          不是每則貼文都掛著兩顆操作鈕。這是現行社群平台的常見做法。

          選單出現在每一則貼文上，包含他人的——後端刻意不檢查發文者身分，
          畫面誠實反映後端能做到的事，不假裝有權限限制。
        -->
        <div class="menu-wrap" @click.stop>
          <button
            type="button"
            class="menu-trigger"
            :aria-expanded="openMenuId === post.postId"
            aria-haspopup="menu"
            aria-label="更多操作"
            @click="toggleMenu(post.postId)"
          >
            ⋯
          </button>

          <div v-if="openMenuId === post.postId" class="menu" role="menu">
            <button type="button" role="menuitem" @click="startEdit(post)">編輯</button>
            <button type="button" role="menuitem" class="danger" @click="askDelete(post)">
              刪除
            </button>
          </div>
        </div>
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

        <!--
          刪除確認留在卡片內，而非跳原生 window.confirm。
          原生對話框會凍結整個分頁、樣式不受控、行動裝置上各家呈現不一；
          動作與後果應該留在同一個視覺脈絡裡。
        -->
        <div v-if="confirmingDeleteId === post.postId" class="confirm" role="alertdialog" @click.stop>
          <p class="confirm-text">刪除這篇發文？該發文的留言會一併被刪除。</p>
          <div class="confirm-actions">
            <button type="button" class="secondary" @click="cancelDelete">取消</button>
            <button type="button" class="solid-danger" @click="onDelete(post)">刪除</button>
          </div>
        </div>
      </template>

      <!--
        留言列表（隨 GET /api/posts 一併帶回）。

        放在輸入框「上方」：先看到既有對話，再決定要說什麼——
        這是社群平台的閱讀順序，不是先給筆再給紙。

        留言者名稱與留言內容同樣是使用者輸入且會回顯，一律用 {{ }} 插值輸出。
        本檔開頭的 XSS 說明對這一段完全適用：**不得改用 v-html**。
      -->
      <div v-if="commentCount(post) > 0" class="comments">
        <!-- 展開是純前端切換：留言早就在手上，不會因此多打一次 API -->
        <button
          v-if="commentCount(post) > COMMENT_PREVIEW"
          type="button"
          class="comment-toggle"
          :aria-expanded="expandedComments[post.postId] === true"
          @click="toggleComments(post.postId)"
        >
          {{
            expandedComments[post.postId]
              ? `只顯示最新 ${COMMENT_PREVIEW} 則`
              : `顯示全部 ${commentCount(post)} 則留言`
          }}
        </button>

        <ul class="comment-list">
          <li
            v-for="comment in visibleComments(post)"
            :key="comment.commentId"
            class="comment-item"
          >
            <div class="comment-meta">
              <span class="comment-author">{{ comment.userName }}</span>
              <time
                class="time"
                :datetime="comment.createdAt"
                :title="formatExactTime(comment.createdAt)"
              >{{ formatRelativeTime(comment.createdAt) }}</time>
            </div>
            <p class="comment-text">{{ comment.content }}</p>
          </li>
        </ul>
      </div>

      <!--
        針對發文新增留言。編輯與刪除留言仍不做。

        送出鈕只在輸入框有內容時出現：空著的時候，一則貼文下面掛一顆按不了的鈕
        是純粹的視覺噪音——社群動態是一路往下滑的，每則都多一顆灰鈕會很吵。
      -->
      <form class="comment" novalidate @submit.prevent="onComment(post)">
        <input
          v-model="commentDrafts[post.postId]"
          maxlength="500"
          placeholder="留個言…"
          aria-label="留言內容"
        />
        <button
          v-if="(commentDrafts[post.postId] ?? '').trim()"
          type="submit"
          :disabled="commenting[post.postId]"
        >
          {{ commenting[post.postId] ? '送出中…' : '留言' }}
        </button>
      </form>

      <small v-if="commentErrors[post.postId]" class="error">
        {{ commentErrors[post.postId] }}
      </small>
    </article>
    </div>
  </section>
</template>

<style scoped>
/*
 * 版面：單欄、窄行長。社群內容是逐則掃視的，過寬的行長會讓眼睛在換行時失去位置。
 * 動態一律由 CSS 與 Vue 內建 Transition 驅動；style.css 已統一處理 prefers-reduced-motion。
 */
.page {
  max-width: 46rem;
  margin: 0 auto;
  padding: 2.5rem 1.5rem 5rem;
}

h1 {
  margin-bottom: 1.5rem;
}

/* ── 發文輸入區 ─────────────────────────────────── */
.composer {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1.25rem;
  background: var(--mist-0);
  border: 1px solid var(--stone-200);
  border-radius: var(--r-lg);
  /* 輸入區是這一頁的動作起點，用一道細的主色邊界把它與下方的內容區分開 */
  border-top: 3px solid var(--jade-700);
}

.composer label {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.85rem;
  font-weight: 600;
}

/* 字數計數靠右對齊輸入框尾端，並用等寬數字避免跳動 */
.composer label > .hint {
  align-self: flex-end;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.75rem;
}

/* ── 發文列表 ───────────────────────────────────── */
.feed {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-top: 1.5rem;
}

.post {
  padding: 1.25rem;
  background: var(--mist-0);
  border: 1px solid var(--stone-200);
  border-radius: var(--r-lg);
  transition: border-color var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
}

.post:hover {
  border-color: var(--jade-100);
  box-shadow: 0 1px 3px rgba(5, 35, 29, 0.05);
}

.post > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.6rem;
}

/* 作者與時間同一行、基線對齊 */
.meta {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
  min-width: 0;
}

/* ── 「⋯」選單 ─────────────────────────────────── */
.menu-wrap {
  position: relative;
  flex-shrink: 0;
}

.menu-trigger {
  padding: 0 0.5rem;
  border: 0;
  border-radius: var(--r-sm);
  background: none;
  font-size: 1.15rem;
  line-height: 1.2;
  color: var(--stone-400);
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}

.menu-trigger:hover {
  background: var(--jade-100);
  color: var(--jade-950);
}

.menu-trigger[aria-expanded="true"] {
  background: var(--jade-100);
  color: var(--jade-950);
}

.menu {
  position: absolute;
  top: calc(100% + 0.35rem);
  right: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  min-width: 7rem;
  padding: 0.25rem;
  background: var(--mist-0);
  border: 1px solid var(--stone-200);
  border-radius: var(--r-md);
  box-shadow: 0 6px 20px rgba(5, 35, 29, 0.1);
}

.menu button {
  padding: 0.45rem 0.7rem;
  border: 0;
  border-radius: var(--r-sm);
  background: none;
  font-size: 0.88rem;
  font-weight: 500;
  text-align: left;
  color: var(--jade-950);
}

.menu button:hover {
  background: var(--jade-100);
}

.menu button.danger {
  color: var(--clay-600);
  border: 0;
}

.menu button.danger:hover {
  background: var(--clay-50);
}

/* ── 刪除確認 ───────────────────────────────────── */
.confirm {
  margin-bottom: 0.5rem;
  padding: 0.9rem 1rem;
  background: var(--clay-50);
  border-radius: var(--r-md);
}

.confirm-text {
  margin: 0 0 0.75rem;
  font-size: 0.88rem;
  color: var(--clay-600);
}

.confirm-actions {
  display: flex;
  gap: 0.5rem;
}

.confirm-actions button {
  padding: 0.4rem 0.9rem;
  font-size: 0.85rem;
}

/* 確認裡的刪除鈕用實心：這是使用者最後一次確認，不該和「取消」看起來一樣輕 */
.solid-danger {
  background: var(--clay-600);
  color: var(--mist-0);
  border-color: var(--clay-600);
}

.solid-danger:hover:not(:disabled) {
  background: #8d2b22;
  border-color: #8d2b22;
}

.author {
  font-weight: 600;
  letter-spacing: -0.01em;
}

.time {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.78rem;
  color: var(--stone-400);
  white-space: nowrap;
}

/* 使用者輸入的內容：保留換行與空白，但不解讀 HTML（{{ }} 插值已跳脫） */
.content {
  margin: 0 0 0.9rem;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

.actions button {
  padding: 0.4rem 0.85rem;
  font-size: 0.85rem;
}

/* ── 編輯狀態 ───────────────────────────────────── */
.editor {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-bottom: 0.5rem;
}

/* ── 留言列表 ───────────────────────────────────── */
/*
 * 留言在視覺上必須從屬於發文：字級較小、名稱不與發文作者同級、
 * 靠左側一道極淺的主色線收攏成一個區塊，讓眼睛知道這些話是掛在上面那則發文底下的。
 */
.comments {
  margin-top: 1rem;
  padding-top: 0.9rem;
  border-top: 1px solid var(--stone-200);
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.comment-item {
  padding-left: 0.75rem;
  border-left: 2px solid var(--jade-100);
}

.comment-meta {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
}

.comment-author {
  font-size: 0.85rem;
  font-weight: 600;
  letter-spacing: -0.01em;
}

/* 留言的時間比發文的再小一級——它是註記，不是資訊主體 */
.comment-item .time {
  font-size: 0.72rem;
}

/* 與發文內容同樣保留換行、同樣只走 {{ }} 插值，不解讀 HTML */
.comment-text {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/*
 * 「顯示全部」是導覽性質的次要動作，不該長得像發文區的實心主鈕，
 * 故拆掉底色只留文字；hover 時才用主色表示可點。
 */
.comment-toggle {
  display: block;
  margin-bottom: 0.6rem;
  padding: 0;
  border: 0;
  background: none;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--stone-400);
  transition: color var(--dur) var(--ease);
}

.comment-toggle:hover {
  background: none;
  color: var(--jade-700);
}

/* ── 留言輸入 ───────────────────────────────────── */
.comment {
  display: flex;
  align-items: stretch;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--stone-200);
}

/*
 * 已經有留言列表時，分隔線由 .comments 承擔，輸入框只留間距。
 * 兩條線疊在一起會把留言區切成兩塊，但它們本來就是同一件事的兩面。
 */
.comments + .comment {
  margin-top: 0.85rem;
  padding-top: 0;
  border-top: 0;
}

.comment input {
  flex: 1;
  min-width: 0;
}

.comment button {
  flex-shrink: 0;
  padding: 0.55rem 1.1rem;
  font-size: 0.85rem;
}

/* 留言列在沒有內容時只有一個輸入框，讓它在視覺上退到背景 */
.comment input {
  background: var(--mist-50);
  border-color: transparent;
}

.comment input:focus-visible {
  background: var(--mist-0);
  border-color: var(--jade-500);
}

/* 留言送出失敗的訊息緊接在表單下方（成功不另外提示——新留言會直接出現在列表裡） */
.comment + .error {
  display: block;
  margin-top: 0.5rem;
  font-size: 0.8rem;
}

/* ── 狀態訊息 ───────────────────────────────────── */
.banner {
  margin: 1.25rem 0 0;
  padding: 0.85rem 1rem;
  border-radius: var(--r-md);
  font-size: 0.9rem;
}

.hint {
  color: var(--stone-400);
  background: var(--mist-0);
  border: 1px dashed var(--stone-200);
}

.error {
  color: var(--clay-600);
  background: var(--clay-50);
}

.field-error,
.form-error {
  font-size: 0.82rem;
  color: var(--clay-600);
}

.form-success {
  font-size: 0.82rem;
  color: var(--jade-700);
}

.counter {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.78rem;
  color: var(--stone-400);
}

@media (max-width: 34rem) {
  .page {
    padding: 1.75rem 1rem 4rem;
  }

  /* 留言列在窄螢幕改為上下堆疊：並排時輸入框會被壓到放不下一行字 */
  .comment {
    flex-direction: column;
  }

  /* 窄螢幕的行長本來就緊，留言的縮排再讓一點給內容 */
  .comment-item {
    padding-left: 0.6rem;
  }

  .comment button {
    align-self: flex-end;
  }

  /* 編輯／刪除在窄螢幕維持並排，但加大點擊區——手指不是滑鼠指標 */
  .actions button {
    padding: 0.5rem 1rem;
  }
}
</style>
