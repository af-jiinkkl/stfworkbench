import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      // 工作台外壳。业务页面都挂在它下面，自动带上左侧导航，
      // 不必每个页面自己拼一遍侧边栏。
      path: '/',
      component: () => import('@/layouts/WorkbenchLayout.vue'),
      children: [
        {
          // 空 path 表示这就是父级的默认页，访问 / 时渲染
          path: '',
          name: 'home',
          // 懒加载：各页面拆成独立 chunk，首屏不必把它们全下下来
          component: () => import('@/views/HomeView.vue'),
        },
        {
          path: 'plan',
          name: 'plan',
          component: () => import('@/views/PlanView.vue'),
        },
        // 后续模块（生日 / 课程表 / 备忘 / 新闻 / 消费）加到这一层
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
  ],
})

/**
 * 路由守卫：默认全部需要登录，只有显式标了 meta.public 的才放行。
 *
 * 与后端 WebConfig 的白名单是同一个思路 —— 默认拒绝，
 * 新增页面时忘了加 meta 的后果是"要登录才能看"，而不是"谁都能看"。
 */
router.beforeEach((to) => {
  const userStore = useUserStore()
  const isPublic = to.meta.public === true

  if (!isPublic && !userStore.token) {
    // 记下原本要去的地址，登录后跳回去，避免用户重新点一遍
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (isPublic && userStore.token) {
    // 已登录还去登录页，直接送回首页
    return { name: 'home' }
  }

  return true
})

export default router
