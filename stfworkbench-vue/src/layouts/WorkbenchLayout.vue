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
  SwitchButton,
  Wallet,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

/**
 * 工作台外壳：左侧固定导航 + 右侧内容区。
 *
 * 做成 layout 而不是写在 HomeView 里，是因为其余 7 个页面都要共用这套导航 ——
 * 否则每加一个模块就得复制一遍侧边栏。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/**
 * 已经做得出来的页面。顺序与首页模块总览一致，两边看着才是同一套。
 */
const navItems = [
  { name: 'home', label: '首页', icon: HomeFilled, to: '/' },
  { name: 'plan', label: '每日计划', icon: Calendar, to: '/plan' },
  { name: 'anniversary', label: '生日纪念日', icon: Present, to: '/anniversary' },
  { name: 'memo', label: '备忘录', icon: Memo, to: '/memo' },
  { name: 'expense', label: '每日消费', icon: Wallet, to: '/expense' },
  { name: 'course', label: '课程表', icon: Notebook, to: '/course' },
  { name: 'news', label: '每日新闻', icon: Reading, to: '/news' },
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
          :title="item.label"
          :aria-label="item.label"
        >
          <el-icon class="nav-icon">
            <component :is="item.icon" />
          </el-icon>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="account">
          <span class="avatar">{{ avatarText }}</span>
          <span class="account-name">{{ accountLabel }}</span>
        </div>
        <button
          type="button"
          class="logout"
          title="退出"
          aria-label="退出登录"
          @click="handleLogout"
        >
          <el-icon class="logout-icon">
            <SwitchButton />
          </el-icon>
          <span class="logout-text">退出</span>
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

/* 规划中的模块曾经在这里，样式是 .nav-item.is-disabled（不可点、不响应悬停）。
   六个业务模块全部落地后那段连同"即将上线"标题一起删掉了 —— 空标题下面没有条目
   比灰着更让人困惑。将来再加未实现的模块时，样式照上面那套写：
   文字用 --wb-text-muted、图标用 --wb-text-faint，整体比可用项降一档。 */

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

/* 退出图标的**默认状态是隐藏**，只在窄屏图标栏里出现。
   不这么写就等于顺手改了桌面端的样子 —— 上面那条"桌面端零变化"的约束是硬要求。 */
.logout-icon {
  display: none;
}

/* ---------- 内容区 ---------- */
.content {
  flex: 1;
  min-width: 0; /* 不加的话内部超宽内容会把 flex 容器撑破 */
  overflow-y: auto;
}

/* ============================================================
 * 窄屏：侧边栏收成 56px 图标栏（常驻，不做抽屉）
 *
 * 268px 的侧边栏在 390px 的手机上会让内容区只剩 122px，整站不可用。
 * 收成图标栏而不是抽屉，是为了**不引入状态** —— 抽屉要配 `isSidebarOpen`、
 * 遮罩、Esc、锁 body 滚动、路由变化时自动关这五样，其中"路由变化时忘了关"
 * 是最容易漏的一条；图标栏把这些全绕开了，导航还始终可见。
 *
 * 断点只取 768px 一个：769–1024px 内容区还有 500px 以上，配合各页自己的
 * 横向滚动已经够用，不再多设一档。
 * ============================================================ */
@media (max-width: 768px) {
  .sidebar {
    /* 桌面用的 --wb-sidebar-width 保持 268px 不动，只在这里覆盖 */
    width: 56px;
  }

  .brand {
    justify-content: center;
    padding: 22px 0 18px;
  }

  .brand-name {
    display: none;
  }

  .nav {
    padding: 0 8px;
  }

  /* 图标栏里图标是唯一的表意元素：居中、给足触摸目标（≥40px） */
  .nav-item {
    justify-content: center;
    min-height: 40px;
    padding: 10px 0;
  }

  /* 文字隐藏会**同时把它从无障碍树里摘掉**，所以模板里必须补 aria-label ——
     否则读屏软件读到的是 7 个没有名字的链接。见模板上的 :aria-label。 */
  .nav-label {
    display: none;
  }

  .nav-icon {
    font-size: 20px;
  }

  /* 底部改竖排：头像在上、退出在下 */
  .sidebar-footer {
    flex-direction: column;
    gap: 8px;
    padding: 12px 0;
  }

  .account {
    justify-content: center;
  }

  .account-name {
    display: none;
  }

  .logout {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 32px;
    padding: 0;
  }

  .logout-icon {
    display: inline-flex;
    font-size: 18px;
  }

  .logout-text {
    display: none;
  }
}
</style>
