<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import * as anniversaryApi from '@/api/anniversaryApi'
import UpcomingAnniversaryList from '@/components/UpcomingAnniversaryList.vue'
import { useUserStore } from '@/store/user'
import type { UpcomingAnniversary } from '@/types/anniversary'

/**
 * 首页内容区。外层导航由 WorkbenchLayout 提供，这里只管内容。
 */
const userStore = useUserStore()

const nickname = computed(() => userStore.userInfo?.nickname ?? '')

/**
 * 临近日子的提前提示（需求 §3.2：进入工作台首页时检查是否有临近的生日并展示）。
 *
 * 走 `/api/anniversary/upcoming`。docs/接口清单.md §7 规划了一个
 * `/api/dashboard` 聚合端点，落地后这里会换成那一个请求 —— 届时今日任务、
 * 生日提醒、备忘条数一次拿全，不必再多发两个请求。
 */
const upcoming = ref<UpcomingAnniversary[]>([])

onMounted(async () => {
  try {
    upcoming.value = await anniversaryApi.upcoming()
  }
  catch {
    // request.ts 的拦截器已经弹过错误提示了，这里再弹一次是重复的。
    // 这块是锦上添花的内容，拿不到就空着，不该让整个首页用不了。
  }
})

/** 按当前时间问候。用本机时间即可 —— 后端整套按 Asia/Shanghai 存，见 application.yml。 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

interface HomeModule {
  label: string
  desc: string
  /** 已实现的模块给出路由地址，卡片变成可点的；没有这个字段的就是还没做 */
  to?: string
}

/**
 * 模块总览。与侧边栏那份是同一批，但这里多一句说明 ——
 * 侧边栏只需要名字，首页要讲清每个模块打算做什么。
 *
 * 类型显式写成 HomeModule[]，不然 TS 会推出「有的带 to、有的不带」的联合类型，
 * 访问 mod.to 时会对没写 to 的那几项报错。
 */
const modules: HomeModule[] = [
  { label: '每日计划', desc: '今天的待办与完成情况', to: '/plan' },
  { label: '生日纪念日', desc: '重要日子与倒数提醒', to: '/anniversary' },
  { label: '课程表', desc: '每周课程安排' },
  { label: '备忘录', desc: '随手记下的碎片' },
  { label: '每日新闻', desc: '每天值得一读的几条' },
  { label: '每日消费', desc: '当天花了多少、花在哪' },
]
</script>

<template>
  <div class="page">
    <header class="page-header">
      <h1 class="greeting">
        {{ greeting }}<template v-if="nickname">，{{ nickname }}</template>
      </h1>
      <p class="subtitle">
        下面是规划的模块，做好的可以直接点进去。
      </p>
    </header>

    <!-- 没有临近的日子时整块不出现 —— 一块"暂无提醒"的空白卡片除了占地方
         没有别的用处，还容易让人以为提醒功能坏了 -->
    <section
      v-if="upcoming.length"
      class="wb-card soon-panel"
    >
      <div class="soon-head">
        <h2 class="soon-title">
          即将到来
        </h2>
        <RouterLink
          to="/anniversary"
          class="soon-more"
        >
          管理 →
        </RouterLink>
      </div>
      <UpcomingAnniversaryList :items="upcoming" />
    </section>

    <section class="grid">
      <!-- 做好的模块渲染成 router-link（可点、可键盘聚焦、可右键新标签打开），
           没做好的还是 article。用 :is 切换而不是把两块内容各写一遍 -->
      <component
        :is="mod.to ? RouterLink : 'article'"
        v-for="mod in modules"
        :key="mod.label"
        :to="mod.to"
        class="wb-card module"
        :class="{ 'is-link': mod.to }"
      >
        <div class="module-head">
          <h2 class="module-title">
            {{ mod.label }}
          </h2>
          <span
            v-if="!mod.to"
            class="module-tag"
          >待开发</span>
          <span
            v-else
            class="module-go"
          >进入 →</span>
        </div>
        <p class="module-desc">
          {{ mod.desc }}
        </p>
      </component>
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: var(--wb-content-max);
  padding: 40px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 28px;
}

.greeting {
  font-size: var(--wb-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.subtitle {
  margin-top: 8px;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

/* ---------- 即将到来 ---------- */
.soon-panel {
  padding: 16px 20px 18px;
  margin-bottom: 16px;
}

.soon-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--wb-border);
}

.soon-title {
  font-size: var(--wb-text-base);
  font-weight: 600;
}

.soon-more {
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
}

.soon-more:hover {
  color: var(--wb-text-secondary);
}

/* ---------- 模块总览 ---------- */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(216px, 1fr));
  gap: 12px;
}

.module {
  padding: 16px;
  transition: border-color 0.12s ease, background-color 0.12s ease;
}

/* 已实现的模块：悬停时描边加深，给出"这个能点"的反馈。
   不做位移和投影 —— 那套在这个风格里太吵 */
.module.is-link:hover {
  background-color: var(--wb-surface-hover);
  border-color: var(--wb-border-strong);
}

.module-go {
  flex-shrink: 0;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
}

.module.is-link:hover .module-go {
  color: var(--wb-text-secondary);
}

.module-head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.module-title {
  font-size: var(--wb-text-base);
  font-weight: 500;
}

/* 标签用次级色而非弱化色：它坐在 --wb-primary-soft 的浅灰底上，
   同一个 --wb-text-muted 在白底上是 4.8:1，到这里只剩 4.4:1，掉出 AA。
   而且 12px 的小字本就比正文更需要对比度 —— 短标签比长段落深一档是对的层次。 */
.module-tag {
  flex-shrink: 0;
  padding: 2px 7px;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-secondary);
  background-color: var(--wb-primary-soft);
  border-radius: var(--wb-radius-sm);
}

.module-desc {
  margin-top: 8px;
  font-size: var(--wb-text-sm);
  line-height: 1.5;
  color: var(--wb-text-muted);
}
</style>
