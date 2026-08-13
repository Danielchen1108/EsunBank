import { createRouter, createWebHistory } from 'vue-router'
import RegisterView from '../views/RegisterView.vue'
import HealthView from '../views/HealthView.vue'

/**
 * 路由設定。
 *
 * 題目未提及路由，但註冊、登入、發文為各自獨立的畫面（題目 §1–§4），
 * 屬明文功能的必要前提（SCOPE-BOUNDARY.md 判定原則 R-2）。
 *
 * 待加入：/login（F003）、/posts（F004）。
 */
const routes = [
  { path: '/', redirect: '/register' },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/health', name: 'health', component: HealthView },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
