import { createRouter, createWebHistory } from 'vue-router'
import RegisterView from '../views/RegisterView.vue'
import LoginView from '../views/LoginView.vue'
import HealthView from '../views/HealthView.vue'

/**
 * 路由設定。
 *
 * 題目未提及路由，但註冊、登入、發文為各自獨立的畫面（題目 §1–§4），
 * 屬明文功能的必要前提（SCOPE-BOUNDARY.md 判定原則 R-2）。
 *
 * 不設路由守衛：授權判定在後端（SecurityConfig 的過濾鏈），
 * 前端擋畫面只是體驗優化，且無法取代後端把關。待 /posts（F004）出現後，
 * 若要加守衛也僅為導向登入頁，不作為安全機制。
 */
const routes = [
  { path: '/', redirect: '/register' },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/health', name: 'health', component: HealthView },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
