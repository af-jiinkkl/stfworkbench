<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef, watch } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Delete, EditPen, Plus } from '@element-plus/icons-vue'
import * as planApi from '@/api/planApi'
import type { PlanTask } from '@/types/plan'
import { addDays, addMonths, today } from '@/utils/date'

/**
 * 每日计划。对应 docs/接口清单.md §4 的 6 个接口。
 *
 * 分两个模式：
 * - **按天**：核心交互，增删改 + 勾选完成
 * - **回顾**：按区间查历史（需求要求可查看 6 个月内）
 */

const mode = ref<'day' | 'review'>('day')

const todayStr = today()

// ---------- 按天 ----------
const date = ref(todayStr)
const tasks = ref<PlanTask[]>([])
const loading = ref(false)
const newContent = ref('')
const adding = ref(false)

const completedCount = computed(
  () => tasks.value.filter((task) => task.completed === 1).length,
)

const percent = computed(() =>
  tasks.value.length
    ? Math.round((completedCount.value / tasks.value.length) * 100)
    : 0,
)

async function loadDay(): Promise<void> {
  loading.value = true
  try {
    tasks.value = await planApi.listByDate(date.value)
  }
  finally {
    loading.value = false
  }
}

function goToday(): void {
  date.value = todayStr
}

// 日期一改就重新拉。用 @change 而不是 watch(date)：用户连续翻日历时会触发
// 多次请求，@change 只在选定后触发一次。
function onDateChange(): void {
  loadDay()
}

async function add(): Promise<void> {
  const content = newContent.value.trim()
  if (!content) {
    return
  }

  adding.value = true
  try {
    const created = await planApi.createTask({ planDate: date.value, content })
    // 后端返回的 sortOrder 是 0，与既有任务同值，按 id 排正好落在末尾，
    // 所以直接 push 与再次拉取的顺序一致
    tasks.value.push(created)
    newContent.value = ''
  }
  finally {
    adding.value = false
  }
}

/**
 * 勾选完成。用乐观更新：勾选必须立刻有反馈，等一个网络来回会明显发顿。
 * 失败则回滚 —— 否则界面显示的是一个并没有存进库的状态，
 * 用户刷新后会看到勾又弹回去了，却不知道哪次是真的。
 */
async function toggle(task: PlanTask): Promise<void> {
  const next = task.completed === 1 ? 0 : 1
  const previous = task.completed
  task.completed = next

  try {
    const updated = await planApi.updateCompleted(task.id, next)
    Object.assign(task, updated)
  }
  catch {
    task.completed = previous
  }
}

async function remove(task: PlanTask): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除「${task.content}」吗？`, '删除任务', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  }
  catch {
    // 用户点了取消。ElMessageBox 取消时是 reject，不是返回 false，
    // 不接住的话会冒成未处理的 Promise 异常
    return
  }

  await planApi.removeTask(task.id)
  tasks.value = tasks.value.filter((item) => item.id !== task.id)
  ElMessage.success('已删除')
}

// ---------- 行内编辑 ----------
const editingId = ref<number | null>(null)
const editingContent = ref('')
const editInput = shallowRef<{ focus: () => void } | null>(null)

/**
 * v-for 里的模板 ref 会被收集成数组，拿不准下标对应哪一项。
 * 用函数式 ref 只留住当前正在编辑的那一个 —— 反正同时只会渲染一个输入框。
 *
 * 这里要绕一次 unknown：el-input 实例上确实有 focus()，但 Vue 给函数式 ref 的
 * 形参类型是 ComponentPublicInstance，里面没有这个方法。仅此一处，不做类型体操。
 * 传 null 表示组件已卸载，必须跟着清掉，否则会攥着一个失效的实例。
 */
function registerEditRef(el: Element | ComponentPublicInstance | null): void {
  editInput.value = el ? (el as unknown as { focus: () => void }) : null
}

async function startEdit(task: PlanTask): Promise<void> {
  editingId.value = task.id
  editingContent.value = task.content
  await nextTick()
  editInput.value?.focus()
}

function cancelEdit(): void {
  editingId.value = null
}

async function saveEdit(task: PlanTask): Promise<void> {
  // Esc 取消后输入框会失焦，从而又触发一次 blur。editingId 已经清掉，
  // 靠这个判断把那次多余的保存挡下来，否则"取消"反而会把改动存进去。
  if (editingId.value !== task.id) {
    return
  }

  const content = editingContent.value.trim()
  if (!content) {
    ElMessage.warning('任务内容不能为空')
    return
  }
  if (content === task.content) {
    editingId.value = null
    return
  }

  const updated = await planApi.updateTask(task.id, { content })
  const index = tasks.value.findIndex((item) => item.id === task.id)
  if (index >= 0) {
    // 就地替换而不是整表重拉：重拉会让列表滚动位置和正在编辑的状态一起丢掉
    tasks.value[index] = updated
  }
  editingId.value = null
}

// ---------- 回顾 ----------
const range = ref<[string, string]>([addDays(todayStr, -29), todayStr])
const reviewTasks = ref<PlanTask[]>([])
const reviewLoading = ref(false)

/** 按日期分组。接口返回已按日期倒序，这里保持原顺序即可 */
const groupedReview = computed(() => {
  const groups = new Map<string, PlanTask[]>()
  for (const task of reviewTasks.value) {
    const list = groups.get(task.planDate)
    if (list) {
      list.push(task)
    }
    else {
      groups.set(task.planDate, [task])
    }
  }
  return [...groups.entries()].map(([dateKey, items]) => ({ date: dateKey, items }))
})

async function loadReview(): Promise<void> {
  reviewLoading.value = true
  try {
    reviewTasks.value = await planApi.listByRange(range.value[0], range.value[1])
  }
  finally {
    reviewLoading.value = false
  }
}

function onRangeChange(value: [string, string] | null): void {
  if (!value) {
    return
  }

  // 后端限制区间不超过 6 个月（docs/接口清单.md §4）。这里先拦一道是为了
  // 不让用户白等一次必然失败的请求；它**不能**当作保证 —— 后端那一层才是。
  const limit = addMonths(value[0], 6)
  if (value[1] > limit) {
    ElMessage.warning('查询区间不能超过 6 个月，已截断到上限')
    range.value = [value[0], limit]
  }

  loadReview()
}

function doneIn(items: PlanTask[]): number {
  return items.filter((task) => task.completed === 1).length
}

/** 把 yyyy-MM-dd 说成人话。跨年时补上年份，否则"1 月 5 日"会有歧义 */
function friendlyDate(dateStr: string): string {
  if (dateStr === todayStr) {
    return '今天'
  }
  if (dateStr === addDays(todayStr, -1)) {
    return '昨天'
  }
  if (dateStr === addDays(todayStr, 1)) {
    return '明天'
  }

  const [year, month, day] = dateStr.split('-').map(Number)
  const label = `${month} 月 ${day} 日`
  return year === Number(todayStr.slice(0, 4)) ? label : `${year} 年 ${label}`
}

watch(mode, (current) => {
  if (current === 'review' && !reviewTasks.value.length) {
    loadReview()
  }
})

onMounted(loadDay)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1 class="title">
          每日计划
        </h1>
        <p class="subtitle">
          今天要做的事，做完打个勾。
        </p>
      </div>

      <el-radio-group v-model="mode">
        <el-radio-button value="day">
          按天
        </el-radio-button>
        <el-radio-button value="review">
          回顾
        </el-radio-button>
      </el-radio-group>
    </header>

    <!-- ========== 按天 ========== -->
    <template v-if="mode === 'day'">
      <div class="toolbar">
        <!-- value-format 用的是 dayjs 的记号（YYYY-MM-DD），
             和后端 Java 那套 yyyy-MM-dd 长得像但不是一回事，别照抄 -->
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          placeholder="选择日期"
          @change="onDateChange"
        />
        <el-button
          v-if="date !== todayStr"
          link
          @click="goToday"
        >
          回到今天
        </el-button>
      </div>

      <div
        v-loading="loading"
        class="wb-card panel"
      >
        <div class="panel-head">
          <span class="panel-title">{{ friendlyDate(date) }}的任务</span>
          <span
            v-if="tasks.length"
            class="panel-meta"
          >
            已完成 {{ completedCount }} / {{ tasks.length }}
          </span>
        </div>

        <div
          v-if="tasks.length"
          class="progress"
        >
          <div
            class="progress-fill"
            :style="{ width: `${percent}%` }"
          />
        </div>

        <ul
          v-if="tasks.length"
          class="task-list"
        >
          <li
            v-for="task in tasks"
            :key="task.id"
            class="task"
            :class="{ 'is-done': task.completed === 1 }"
          >
            <el-checkbox
              :model-value="task.completed === 1"
              @change="toggle(task)"
            />

            <el-input
              v-if="editingId === task.id"
              :ref="registerEditRef"
              v-model="editingContent"
              class="task-edit"
              @keyup.enter="saveEdit(task)"
              @keyup.esc="cancelEdit"
              @blur="saveEdit(task)"
            />

            <template v-else>
              <!-- 双击也能进入编辑，省得非要去够那个小图标 -->
              <span
                class="task-content"
                @dblclick="startEdit(task)"
              >{{ task.content }}</span>
              <span class="task-actions">
                <el-button
                  link
                  :icon="EditPen"
                  title="编辑"
                  @click="startEdit(task)"
                />
                <el-button
                  link
                  :icon="Delete"
                  title="删除"
                  @click="remove(task)"
                />
              </span>
            </template>
          </li>
        </ul>

        <p
          v-else-if="!loading"
          class="empty"
        >
          这一天还没有任务
        </p>

        <div class="add-row">
          <el-input
            v-model="newContent"
            placeholder="添加一个任务，回车即可"
            :prefix-icon="Plus"
            @keyup.enter="add"
          />
          <el-button
            type="primary"
            :loading="adding"
            @click="add"
          >
            添加
          </el-button>
        </div>
      </div>
    </template>

    <!-- ========== 回顾 ========== -->
    <template v-else>
      <div class="toolbar">
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="onRangeChange"
        />
        <span class="hint">最多可查 6 个月</span>
      </div>

      <div v-loading="reviewLoading">
        <p
          v-if="!reviewLoading && !groupedReview.length"
          class="empty"
        >
          这段时间还没有记录
        </p>

        <div
          v-for="group in groupedReview"
          :key="group.date"
          class="wb-card day-group"
        >
          <div class="day-head">
            <span class="day-title">{{ friendlyDate(group.date) }}</span>
            <span class="day-meta">{{ group.date }} · 完成 {{ doneIn(group.items) }} / {{ group.items.length }}</span>
          </div>

          <ul class="task-list">
            <li
              v-for="task in group.items"
              :key="task.id"
              class="task is-readonly"
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
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: var(--wb-content-max);
  padding: 40px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 28px;
}

.title {
  font-size: var(--wb-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.subtitle {
  margin-top: 8px;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.hint {
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

/* ---------- 卡片 ---------- */
.panel {
  padding: 8px 20px 20px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 12px 0;
}

.panel-title {
  font-size: var(--wb-text-lg);
  font-weight: 600;
}

.panel-meta {
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
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

/* ---------- 任务列表 ---------- */
.task-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.task {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 44px;
  padding: 6px 0;
  border-bottom: 1px solid var(--wb-border);
}

.task:last-child {
  border-bottom: none;
}

.task-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--wb-text-base);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 已完成：文字变淡并划掉。仍然保持可读（用的是达到 4.5:1 的那一档），
   因为用户经常需要回头确认某件事到底做没做 */
.task.is-done .task-content {
  color: var(--wb-text-muted);
  text-decoration: line-through;
}

.task-edit {
  flex: 1;
}

/* 操作按钮平时隐身，悬停才出现 —— 平时让内容自己说话 */
.task-actions {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.12s ease;
}

.task:hover .task-actions,
.task:focus-within .task-actions {
  opacity: 1;
}

/* ---------- 回顾 ---------- */
.day-group {
  padding: 16px 20px;
  margin-bottom: 12px;
}

.day-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 10px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--wb-border);
}

.day-title {
  font-size: var(--wb-text-base);
  font-weight: 600;
}

.day-meta {
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

/* 回顾是只读的：不提供勾选框和操作按钮，避免误以为能在这里改历史 */
.task.is-readonly {
  min-height: 32px;
  padding: 4px 0;
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

.is-readonly.is-done .task-mark {
  background-color: var(--wb-primary-soft);
}

/* ---------- 添加 ---------- */
.add-row {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.empty {
  padding: 28px 0;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-align: center;
}
</style>
