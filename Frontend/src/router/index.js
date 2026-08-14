import { createRouter, createWebHistory } from 'vue-router'
import RegisterView from '../views/RegisterView.vue'
import LoginView from '../views/LoginView.vue'
import PostsView from '../views/PostsView.vue'
import { isLoggedIn } from '../api/client.js'

/**
 * 路由設定。
 *
 * 需求未提及路由，但註冊、登入、發文為各自獨立的畫面（需求 §1–§4），
 * 屬明文功能的必要前提（SCOPE-BOUNDARY.md 判定原則 R-2）。
 *
 * ── 關於路由守衛 ─────────────────────────────────────────────
 * 守衛**不是安全機制**，這點不會因為加了守衛而改變。真正的授權判定在後端
 * SecurityConfig 的過濾鏈：手動輸入網址、繞過前端直接打 API，一律回 401。
 *
 * 守衛只做一件事——**在使用者撞牆之前先把他帶到對的地方**。
 * 未登入者點「發文」，與其讓他看到空白畫面再被 401 踢回來，
 * 不如直接帶到登入頁，並記住他原本要去哪，登入後送他過去。
 *
 * 因此守衛的判斷依據是「本機有沒有憑證」，而不是「憑證有沒有效」——
 * 前端無從驗證簽章，也不該試。憑證過期或偽造由後端回 401，
 * 前端在 API 層處理（見 PostsView 的 401 分支）。
 */
/**
 * `meta.hideChrome`：登入與註冊不套用應用程式外框（導覽列）。
 * 導覽列是給已進到系統裡的人用的；在登入頁頂一條寫著「登入／註冊」的列，
 * 等於把使用者當下唯一該做的事降級成選單裡的一個選項。
 */
const routes = [
  {
    path: '/',
    // 進站導向依登入狀態決定：已登入直接看內容，未登入先登入
    redirect: () => (isLoggedIn() ? '/posts' : '/login'),
  },
  {
    path: '/register',
    name: 'register',
    component: RegisterView,
    meta: { guestOnly: true, hideChrome: true },
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { guestOnly: true, hideChrome: true },
  },
  {
    path: '/posts',
    name: 'posts',
    component: PostsView,
    meta: { requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const loggedIn = isLoggedIn()

  // 需登入卻未登入：帶去登入頁，並把原目的地記在查詢字串，登入後送回去
  if (to.meta.requiresAuth && !loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 已登入還想去註冊／登入頁：沒有意義，直接帶到內容頁
  if (to.meta.guestOnly && loggedIn) {
    return { name: 'posts' }
  }

  return true
})

export default router
