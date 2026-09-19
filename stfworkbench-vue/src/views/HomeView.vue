<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/store/user'

/**
 * 首页内容区。外层导航由 WorkbenchLayout 提供，这里只管内容。
 */
const userStore = useUserStore()

const nickname = computed(() => userStore.userInfo?.nickname ?? '')

/** 按当前时间问候。用本机时间即可 —— 后端整套按 Asia/Shanghai 存，见 application.yml。 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

/**
 * 模块总览。与侧边栏那份是同一批，但这里多一句说明 ——
 * 侧边栏只需要名字，首页要讲清每个模块打算做什么。
 */
const modules = [
  { label: '每日计划', desc: '今天的待办与完成情况' },
  { label: '生日纪念日', desc: '重要日子与倒数提醒' },
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
        登录链路已打通。下面是规划的模块，会陆续接进来。
      </p>
    </header>

    <section class="grid">
      <article
        v-for="mod in modules"
        :key="mod.label"
        class="wb-card module"
      >
        <div class="module-head">
          <h2 class="module-title">
            {{ mod.label }}
          </h2>
          <span class="module-tag">待开发</span>
        </div>
        <p class="module-desc">
          {{ mod.desc }}
        </p>
      </article>
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

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(216px, 1fr));
  gap: 12px;
}

.module {
  padding: 16px;
  transition: border-color 0.12s ease, background-color 0.12s ease;
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
