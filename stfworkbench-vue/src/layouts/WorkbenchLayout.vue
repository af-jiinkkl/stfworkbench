<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Calendar,
  HomeFilled,
  Memo,
  Notebook,
  Present,
  Reading,
  Wallet,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

/**
 * 工作台外壳：左侧固定导航 + 右侧内容区。
 *
 * 做成 layout 而不是写在 HomeView 里，是因为后面 6 个模块都要共用这套导航 ——
 * 否则每加一个模块就得复制一遍侧边栏。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 已经做得出来的页面。 */
const navItems = [
  { name: 'home', label: '首页', icon: HomeFilled, to: '/' },
]

/**
 * 规划中、尚未实现的模块。
 *
 * 这里刻意**不**做成可点的空路由：点进去只有一个空白页，比灰着更让人困惑。
 * 模块落地时，把它从下面这个数组移到 navItems 并加一条路由即可。
 */
const upcomingModules = [
  { label: '每日计划', icon: Calendar },
  { label: '生日纪念日', icon: Present },
  { label: '课程表', icon: Notebook },
  { label: '备忘录', icon: Memo },
  { label: '每日新闻', icon: Reading },
  { label: '每日消费', icon: Wallet },
]

const nickname = computed(() => userStore.userInfo?.nickname ?? '')
/** 没有头像图，用昵称首字做占位。中文取首字、英文取首字母，都成立。 */
const avatarText = computed(() => nickname.value.charAt(0).toUpperCase() || '·')

/**
 * 侧边栏显示的名字。
 *
 * 要区分「没登录」和「已登录但用户信息还在请求路上」：后者如果显示"未登录"，
 * 是当着用户的面说了一句假话。有 token 就说明已登录，此时留空即可。
 */
const accountLabel = computed(() => {
  if (nickname.value) {
    return nickname.value
  }
  return userStore.token ? '' : '未登录'
})

/**
 * 刷新页面后 Pinia 状态清空，但 localStorage 里的 token 还在。
 * 这里拉一次当前用户：既把昵称补上，也顺带向服务端验证 token 是否仍有效
 * （token 过期时 request.ts 的 401 分支会自动跳登录页）。
 *
 * 放在 layout 而不是各个页面里，保证任何页面进来都会补一次。
 */
onMounted(async () => {
  if (!userStore.userInfo && userStore.token) {
    await userStore.fetchCurrentUser()
  }
})

async function handleLogout(): Promise<void> {
  userStore.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">S</span>
        <span class="brand-name">stfworkbench</span>
      </div>

      <nav class="nav">
        <router-link
          v-for="item in navItems"
          :key="item.name"
          :to="item.to"
          class="nav-item"
          :class="{ 'is-active': route.name === item.name }"
        >
          <el-icon class="nav-icon">
            <component :is="item.icon" />
          </el-icon>
          <span>{{ item.label }}</span>
        </router-link>

        <p class="nav-caption">
          即将上线
        </p>

        <div
          v-for="mod in upcomingModules"
          :key="mod.label"
          class="nav-item is-disabled"
        >
          <el-icon class="nav-icon">
            <component :is="mod.icon" />
          </el-icon>
          <span>{{ mod.label }}</span>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="account">
          <span class="avatar">{{ avatarText }}</span>
          <span class="account-name">{{ accountLabel }}</span>
        </div>
        <button
          type="button"
          class="logout"
          @click="handleLogout"
        >
          退出
        </button>
      </div>
    </aside>

    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100%;
}

/* ---------- 侧边栏 ---------- */
.sidebar {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: var(--wb-sidebar-width);
  background-color: var(--wb-surface);
  border-right: 1px solid var(--wb-border);
}

.brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 24px 20px 22px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  font-size: var(--wb-text-sm);
  font-weight: 600;
  color: #fff;
  background-color: var(--wb-primary);
  border-radius: var(--wb-radius-sm);
}

.brand-name {
  font-size: var(--wb-text-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.nav {
  flex: 1;
  padding: 0 12px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  font-size: var(--wb-text-base);
  color: var(--wb-text-secondary);
  border-radius: var(--wb-radius);
  transition: background-color 0.12s ease, color 0.12s ease;
}

/* 图标**故意**不走字号阶：图标要比相邻文字大一两像素才显得平衡，
   跟正文同号会看着发虚。所以这里是个独立的数值，不是漏改。 */
.nav-icon {
  font-size: 18px;
  color: var(--wb-text-muted);
}

a.nav-item:hover {
  color: var(--wb-text);
  background-color: var(--wb-surface-hover);
}

/* 当前页：浅灰底 + 深色文字 + 图标同色，不用彩色高亮 */
.nav-item.is-active {
  font-weight: 500;
  color: var(--wb-text);
  background-color: var(--wb-primary-soft);
}

.nav-item.is-active .nav-icon {
  color: var(--wb-text);
}

/* 未实现的模块：只读地摆在那里，不可点也不响应悬停。
   文字和图标各比对应的可用项轻一档，整体构成一个"整块降一档"的观感 ——
   之前两处都用了最浅的色，淡到快读不出字了。 */
.nav-item.is-disabled {
  color: var(--wb-text-muted);
  cursor: default;
}

.nav-item.is-disabled .nav-icon {
  color: var(--wb-text-faint);
}

.nav-caption {
  padding: 0 12px;
  margin: 24px 0 8px;
  font-size: var(--wb-text-sm);
  font-weight: 500;
  color: var(--wb-text-muted);
  letter-spacing: 0.04em;
}

/* ---------- 侧边栏底部 ---------- */
.sidebar-footer {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-top: 1px solid var(--wb-border);
}

.account {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.avatar {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 32px;
  height: 32px;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-secondary);
  background-color: var(--wb-primary-soft);
  border-radius: 50%;
}

.account-name {
  overflow: hidden;
  font-size: var(--wb-text-base);
  color: var(--wb-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout {
  flex-shrink: 0;
  padding: 5px 10px;
  font-family: inherit;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  cursor: pointer;
  background: none;
  border: none;
  border-radius: var(--wb-radius-sm);
  transition: color 0.12s ease, background-color 0.12s ease;
}

.logout:hover {
  color: var(--wb-text);
  background-color: var(--wb-surface-hover);
}

/* ---------- 内容区 ---------- */
.content {
  flex: 1;
  min-width: 0; /* 不加的话内部超宽内容会把 flex 容器撑破 */
  overflow-y: auto;
}
</style>
