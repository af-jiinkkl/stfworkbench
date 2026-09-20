<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Check } from '@element-plus/icons-vue'
import * as dashboardApi from '@/api/dashboardApi'
import UpcomingAnniversaryList from '@/components/UpcomingAnniversaryList.vue'
import { useUserStore } from '@/store/user'
import type { Dashboard } from '@/types/dashboard'

/**
 * 首页内容区。外层导航由 WorkbenchLayout 提供，这里只管内容。
 */

const userStore = useUserStore()

const nickname = computed(() => userStore.userInfo?.nickname ?? '')

/**
 * 首页要的三份数据来自**一次**请求（`GET /api/dashboard`，见 docs/接口清单.md §7）。
 *
 * 原先是单独调 `/api/anniversary/upcoming`，只够照亮"即将到来"那一块。
 * 现在今日任务、生日提醒、备忘条数一起回来，页面一次成型，
 * 不会先亮一块、再亮一块 —— 那三个请求各有各的 loading，看着像页面在抽搐。
 *
 * 拿不到就整体是 null：各块按"没有内容"渲染。首页本来就是入口页，
 * 某个模块的数据没取到不该让整个页面打不开。
 */
const dashboard = ref<Dashboard | null>(null)

/** 首页任务列表最多显示几条，多出来的走"全部 →"去每日计划页看 */
const TASK_PREVIEW_LIMIT = 6

const upcoming = computed(() => dashboard.value?.upcomingAnniversaries ?? [])

const todayPlan = computed(() => dashboard.value?.todayPlan ?? null)

const previewTasks = computed(
  () => todayPlan.value?.tasks.slice(0, TASK_PREVIEW_LIMIT) ?? [],
)

/** 超出预览条数的那部分。为 0 时不显示"还有 N 条" */
const hiddenTaskCount = computed(
  () => Math.max((todayPlan.value?.total ?? 0) - TASK_PREVIEW_LIMIT, 0),
)

/**
 * 完成百分比。
 *
 * 除零要挡住：新用户今天一条任务都没有，total 为 0。
 * 这里算出来是 0 而不是 NaN —— 进度条宽度写成 `NaN%` 会整个塌掉，
 * 而 CSS 不会为这种事报错。
 */
const percent = computed(() => {
  const plan = todayPlan.value
  if (!plan || !plan.total) {
    return 0
  }
  return Math.round((plan.completed / plan.total) * 100)
})

onMounted(async () => {
  try {
    dashboard.value = await dashboardApi.overview()
  }
  catch {
    // request.ts 的拦截器已经弹过错误提示了，这里再弹一次是重复的。
    // 聚合失败就当作"这次没数据"：模块入口不受影响，照样能点进去用单个模块
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
  /** 卡片上的一句实时数字，没实现或还没取到数据时为 undefined */
  meta?: string
}

/**
 * 模块总览。与侧边栏那份是同一批，但这里多一句说明 ——
 * 侧边栏只需要名字，首页要讲清每个模块打算做什么。
 *
 * 已实现的三个模块带上一个数字，数字全部来自上面那一次聚合请求。
 * 写成 computed 是因为它们随 dashboard 变化；静态数组的话要等到
 * 请求回来再手动改数组里的字符串，容易忘。
 *
 * 类型显式写成 HomeModule[]，不然 TS 会推出「有的带 to、有的不带」的联合类型，
 * 访问 mod.to 时会对没写 to 的那几项报错。
 */
const modules = computed<HomeModule[]>(() => {
  const plan = todayPlan.value

  return [
    {
      label: '每日计划',
      desc: '今天的待办与完成情况',
      to: '/plan',
      // 一条都没有时说"还没有安排"而不是"已完成 0 / 0" —— 后者像是出了错
      meta: plan && plan.total ? `今日 ${plan.completed} / ${plan.total}` : '今天还没有安排',
    },
    {
      label: '生日纪念日',
      desc: '重要日子与倒数提醒',
      to: '/anniversary',
      // 没有临近日子时不给数字，空着比"0 条临近"自然
      meta: upcoming.value.length ? `${upcoming.value.length} 条临近` : undefined,
    },
    {
      label: '备忘录',
      desc: '随手记下的碎片',
      to: '/memo',
      meta: dashboard.value ? `共 ${dashboard.value.memoCount} 条` : undefined,
    },
    { label: '课程表', desc: '每周课程安排' },
    { label: '每日新闻', desc: '每天值得一读的几条' },
    { label: '每日消费', desc: '当天花了多少、花在哪' },
  ]
})
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

    <!-- ========== 今日计划 ========== -->
    <!-- 数据还没回来时整块不出现，免得先闪一下"今天还没有安排"再变成任务列表 -->
    <section
      v-if="todayPlan"
      class="wb-card panel"
    >
      <div class="panel-head">
        <h2 class="panel-title">
          今日计划
        </h2>
        <span
          v-if="todayPlan.total"
          class="panel-meta"
        >已完成 {{ todayPlan.completed }} / {{ todayPlan.total }}</span>
        <RouterLink
          v-else
          to="/plan"
          class="panel-more"
        >
          去安排 →
        </RouterLink>
      </div>

      <div
        v-if="todayPlan.total"
        class="progress"
      >
        <div
          class="progress-fill"
          :style="{ width: `${percent}%` }"
        />
      </div>

      <ul
        v-if="previewTasks.length"
        class="task-list"
      >
        <li
          v-for="task in previewTasks"
          :key="task.id"
          class="task"
          :class="{ 'is-done': task.completed === 1 }"
        >
          <span class="task-mark">
            <el-icon v-if="task.completed === 1">
              <Check />
            </el-icon>
          </span>
          <span class="task-content">{{ task.content }}</span>
        </li>
      </ul>

      <p
        v-else
        class="empty"
      >
        今天还没有安排，<RouterLink
          to="/plan"
          class="link"
        >
          去加一个
        </RouterLink>
      </p>

      <!-- 超出预览条数时给个出口。不显示"还有 0 条" -->
      <RouterLink
        v-if="hiddenTaskCount"
        to="/plan"
        class="more"
      >
        还有 {{ hiddenTaskCount }} 条，全部 →
      </RouterLink>
    </section>

    <!-- ========== 即将到来 ========== -->
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
        <p
          v-if="mod.meta"
          class="module-meta"
        >
          {{ mod.meta }}
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

/* ---------- 今日计划 ---------- */
.panel {
  padding: 8px 20px 18px;
  margin-bottom: 16px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 12px 0;
}

.panel-title {
  font-size: var(--wb-text-base);
  font-weight: 600;
}

.panel-meta {
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

.panel-more {
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
}

.panel-more:hover {
  color: var(--wb-text-secondary);
}

.progress {
  height: 2px;
  overflow: hidden;
  background-color: var(--wb-border);
  border-radius: 1px;
}

.progress-fill {
  height: 100%;
  background-color: var(--wb-primary);
  transition: width 0.2s ease;
}

.task-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

/* 首页的任务是只读摘要：不做勾选框也不进入编辑，要改去每日计划页。
   两处都能改的话，同一件事就有两个入口，出问题时不知道是哪边写的 */
.task {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 32px;
  padding: 4px 0;
  border-bottom: 1px solid var(--wb-border);
}

.task:last-child {
  border-bottom: none;
}

.task-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--wb-text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task.is-done .task-content {
  color: var(--wb-text-muted);
  text-decoration: line-through;
}

.task-mark {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  font-size: 13px;
  color: var(--wb-text-secondary);
  border: 1px solid var(--wb-border-strong);
  border-radius: var(--wb-radius-sm);
}

.task.is-done .task-mark {
  background-color: var(--wb-primary-soft);
}

.empty {
  padding: 20px 0;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

.link {
  color: var(--wb-text-secondary);
  text-decoration: underline;
}

.more {
  display: inline-block;
  margin-top: 10px;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
}

.more:hover {
  color: var(--wb-text-secondary);
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

/* 实时数字单独一行，比说明深一档 —— 它是这张卡片上真正在说话的东西 */
.module-meta {
  margin-top: 6px;
  font-size: var(--wb-text-sm);
  font-weight: 500;
  color: var(--wb-text-secondary);
}
</style>
