<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus } from '@element-plus/icons-vue'
import * as anniversaryApi from '@/api/anniversaryApi'
import UpcomingAnniversaryList from '@/components/UpcomingAnniversaryList.vue'
import {
  RELATION_OPTIONS,
  TYPE_BIRTHDAY,
  TYPE_MEMORIAL,
  type Anniversary,
  type AnniversaryParams,
  type UpcomingAnniversary,
} from '@/types/anniversary'
import { today } from '@/utils/date'

/**
 * 生日与纪念日。对应 docs/接口清单.md §5 的 5 个接口。
 *
 * 两块内容：
 * - **即将到来**：`/upcoming` 返回的、进了提醒窗口的记录。这块和首页上那条提示
 *   同源，所以在这里能看到的东西，首页也会看到。
 * - **全部记录**：按月份分组，用来"找哪个月有谁的生日"。
 */

const loading = ref(false)
const list = ref<Anniversary[]>([])
const upcomingList = ref<UpcomingAnniversary[]>([])

/** 后端已按 月、日 排好序，这里只做分组，不再重排 —— 保持两处口径一致 */
const grouped = computed(() => {
  const groups = new Map<number, Anniversary[]>()
  for (const item of list.value) {
    const bucket = groups.get(item.month)
    if (bucket) {
      bucket.push(item)
    }
    else {
      groups.set(item.month, [item])
    }
  }
  return [...groups.entries()].map(([month, items]) => ({ month, items }))
})

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    // 两个请求互不依赖，并发发出。串行的话这个页面要多等一个来回
    const [all, soon] = await Promise.all([
      anniversaryApi.list(),
      anniversaryApi.upcoming(),
    ])
    list.value = all
    upcomingList.value = soon
  }
  finally {
    loading.value = false
  }
}

// ---------- 显示辅助 ----------

function typeLabel(type: number): string {
  return type === TYPE_MEMORIAL ? '纪念日' : '生日'
}

// ---------- 表单 ----------

const DEFAULT_REMIND_DAYS = 7

/** 打开新增时把日期默认到今天：生日多是自己或身边人的，落在当月的概率不低 */
const todayParts = today().split('-').map(Number)
const TODAY_MONTH = todayParts[1] ?? 1
const TODAY_DAY = todayParts[2] ?? 1

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<AnniversaryParams>({
  name: '',
  type: TYPE_BIRTHDAY,
  relation: '',
  month: TODAY_MONTH,
  day: TODAY_DAY,
  remindDays: DEFAULT_REMIND_DAYS,
  remark: '',
})

/**
 * 某月最多能选到几号。
 *
 * 2 月给到 29：闰年出生的人要录得进来。后端也是按 29 校验的，
 * 平年那几年由 YearlyRecurrence 退到 2 月 28 日提醒。
 */
function maxDayOf(month: number): number {
  if (month === 2) {
    return 29
  }
  return [4, 6, 9, 11].includes(month) ? 30 : 31
}

const dayOptions = computed(() =>
  Array.from({ length: maxDayOf(form.month) }, (_, index) => index + 1),
)

/** 2 月 29 日这条要提前说清楚，否则用户会以为某年提醒丢了 */
const leapDayHint = computed(() => form.month === 2 && form.day === 29)

// 换了月份后原来选的"日"可能就不存在了（1 月 31 日 → 2 月）。
// 不夹一下的话会提交一个后端必然拒绝的组合，白等一次失败。
watch(() => form.month, (month) => {
  const max = maxDayOf(month)
  if (form.day > max) {
    form.day = max
  }
})

const rules: FormRules<AnniversaryParams> = {
  name: [
    { required: true, message: '请填写姓名或名称', trigger: 'blur' },
    { max: 50, message: '不能超过 50 个字', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  relation: [{ max: 20, message: '不能超过 20 个字', trigger: 'blur' }],
  month: [{ required: true, message: '请选择月份', trigger: 'change' }],
  day: [{ required: true, message: '请选择日期', trigger: 'change' }],
  remindDays: [{ required: true, message: '请选择提醒天数', trigger: 'change' }],
  remark: [{ max: 255, message: '不能超过 255 个字', trigger: 'blur' }],
}

async function openCreate(): Promise<void> {
  editingId.value = null
  Object.assign(form, {
    name: '',
    type: TYPE_BIRTHDAY,
    relation: '',
    month: TODAY_MONTH,
    day: TODAY_DAY,
    remindDays: DEFAULT_REMIND_DAYS,
    remark: '',
  })
  dialogVisible.value = true
  // 弹窗关闭时用 destroy-on-close 把表单整个销毁，校验态随之消失，
  // 所以这里不用 clearValidate
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(item: Anniversary): Promise<void> {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    type: item.type,
    relation: item.relation,
    month: item.month,
    day: item.day,
    remindDays: item.remindDays,
    remark: item.remark,
  })
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function save(): Promise<void> {
  if (!formRef.value) {
    return
  }

  try {
    await formRef.value.validate()
  }
  catch {
    // 校验没过，字段下方已经有提示了，这里不需要再弹一个
    return
  }

  // 先取出来再判空：直接读 editingId.value 的话 TS 收窄不到那个范围，
  // 到 update 那行就得靠 as number 硬掰，而硬掰是会骗人的
  const id = editingId.value
  const isCreate = id === null

  submitting.value = true
  try {
    // 展开传一份普通对象：直接把 reactive 交给 axios 也能跑，
    // 但序列化时拿到的是 Proxy，出问题时不好看
    if (id === null) {
      await anniversaryApi.create({ ...form })
    }
    else {
      await anniversaryApi.update(id, { ...form })
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '已添加' : '已保存')
    // 重新拉一遍而不是就地改 list：新增或改了月/日都会影响分组和排序，
    // 本地推演一遍等于把后端的排序规则抄到前端，迟早对不上
    await loadAll()
  }
  finally {
    submitting.value = false
  }
}

async function remove(item: Anniversary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.name}」的${typeLabel(item.type)}吗？`,
      '删除记录',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  }
  catch {
    // 用户点了取消。ElMessageBox 取消时是 reject，不接住会冒成未处理的异常
    return
  }

  await anniversaryApi.remove(item.id)
  ElMessage.success('已删除')
  await loadAll()
}

onMounted(loadAll)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1 class="title">
          生日与纪念日
        </h1>
        <p class="subtitle">
          提前一点想起来，来得及准备。
        </p>
      </div>

      <el-button
        type="primary"
        :icon="Plus"
        @click="openCreate"
      >
        添加
      </el-button>
    </header>

    <!-- ========== 即将到来 ========== -->
    <!-- 没有临近的记录时整块不出现：一块"暂无"的空卡片除了占地方没有别的用处 -->
    <section
      v-if="upcomingList.length"
      class="wb-card panel soon-panel"
    >
      <div class="panel-head">
        <span class="panel-title">即将到来</span>
        <span class="panel-meta">共 {{ upcomingList.length }} 条</span>
      </div>

      <!-- 首页的提前提示用的是同一个组件，两边永远显示同一套说法 -->
      <UpcomingAnniversaryList :items="upcomingList" />
    </section>

    <!-- ========== 全部记录 ========== -->
    <div
      v-loading="loading"
      class="wb-card panel"
    >
      <div class="panel-head">
        <span class="panel-title">全部记录</span>
        <span
          v-if="list.length"
          class="panel-meta"
        >共 {{ list.length }} 条</span>
      </div>

      <template v-if="grouped.length">
        <div
          v-for="group in grouped"
          :key="group.month"
          class="month-group"
        >
          <div class="month-head">
            {{ group.month }} 月
          </div>

          <ul class="item-list">
            <li
              v-for="item in group.items"
              :key="item.id"
              class="item"
            >
              <span class="item-day">{{ item.day }} 日</span>
              <span class="item-name">{{ item.name }}</span>
              <span class="item-tag">{{ typeLabel(item.type) }}</span>
              <span
                v-if="item.relation"
                class="item-relation"
              >{{ item.relation }}</span>
              <span
                v-if="item.remark"
                class="item-remark"
                :title="item.remark"
              >{{ item.remark }}</span>

              <span class="item-actions">
                <el-button
                  link
                  :icon="EditPen"
                  title="编辑"
                  @click="openEdit(item)"
                />
                <el-button
                  link
                  :icon="Delete"
                  title="删除"
                  @click="remove(item)"
                />
              </span>
            </li>
          </ul>
        </div>
      </template>

      <p
        v-else-if="!loading"
        class="empty"
      >
        还没有记录，点右上角「添加」记一个吧
      </p>
    </div>

    <!-- ========== 新增 / 编辑 ========== -->
    <!-- destroy-on-close：关闭即销毁表单，下次打开不会残留上一次的校验红字 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '添加记录' : '编辑记录'"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="72px"
      >
        <el-form-item
          label="名称"
          prop="name"
        >
          <el-input
            v-model="form.name"
            maxlength="50"
            placeholder="姓名或纪念日名称"
          />
        </el-form-item>

        <el-form-item
          label="类型"
          prop="type"
        >
          <el-radio-group v-model="form.type">
            <el-radio-button :value="TYPE_BIRTHDAY">
              生日
            </el-radio-button>
            <el-radio-button :value="TYPE_MEMORIAL">
              纪念日
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          label="关系"
          prop="relation"
        >
          <!-- allow-create：常用关系直接点，特殊情况可以自己输入。
               后端这个字段只限长度，不限取值 -->
          <el-select
            v-model="form.relation"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="自己 / 家人 / 朋友"
          >
            <el-option
              v-for="option in RELATION_OPTIONS"
              :key="option"
              :label="option"
              :value="option"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="日期">
          <div class="date-row">
            <el-form-item prop="month">
              <el-select
                v-model="form.month"
                placeholder="月"
              >
                <el-option
                  v-for="month in 12"
                  :key="month"
                  :label="`${month} 月`"
                  :value="month"
                />
              </el-select>
            </el-form-item>
            <el-form-item prop="day">
              <el-select
                v-model="form.day"
                placeholder="日"
              >
                <el-option
                  v-for="day in dayOptions"
                  :key="day"
                  :label="`${day} 日`"
                  :value="day"
                />
              </el-select>
            </el-form-item>
          </div>
          <p
            v-if="leapDayHint"
            class="field-hint"
          >
            平年没有 2 月 29 日，那几年会在 2 月 28 日提醒
          </p>
        </el-form-item>

        <el-form-item
          label="提醒"
          prop="remindDays"
        >
          <el-select v-model="form.remindDays">
            <el-option
              v-for="days in 7"
              :key="days"
              :label="days === 1 ? '提前 1 天' : `提前 ${days} 天`"
              :value="days"
            />
          </el-select>
        </el-form-item>

        <el-form-item
          label="备注"
          prop="remark"
        >
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="save"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
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

/* ---------- 卡片 ---------- */
.panel {
  padding: 8px 20px 20px;
}

/* 即将到来是"要说的事"，和下面的台账分开一点 */
.soon-panel {
  margin-bottom: 16px;
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

/* ---------- 全部记录 ---------- */
.month-group + .month-group {
  margin-top: 8px;
}

.month-head {
  padding: 12px 0 6px;
  font-size: var(--wb-text-sm);
  font-weight: 500;
  color: var(--wb-text-muted);
}

.item-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.item {
  display: flex;
  gap: 10px;
  align-items: baseline;
  min-height: 40px;
  padding: 6px 0;
  border-bottom: 1px solid var(--wb-border);
}

.item:last-child {
  border-bottom: none;
}

.item-day {
  flex-shrink: 0;
  width: 44px;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-align: right;
}

.item-name {
  flex-shrink: 0;
  font-size: var(--wb-text-base);
}

.item-tag {
  flex-shrink: 0;
  padding: 1px 6px;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-secondary);
  background-color: var(--wb-primary-soft);
  border-radius: var(--wb-radius-sm);
}

.item-relation {
  flex-shrink: 0;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

.item-remark {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 备注为空时靠它把操作按钮顶到最右边 */
.item-actions {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.12s ease;
}

.item:hover .item-actions,
.item:focus-within .item-actions {
  opacity: 1;
}

/* ---------- 表单 ---------- */
.date-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

/* 嵌在"日期"这一项里的两个子项，各自不该再占一整行 */
.date-row :deep(.el-form-item) {
  /* flex: 1 不能少。el-select 的宽度是 100%，而它在 flex 行里作为
     "内容尺寸"的项会被压到只剩一个箭头宽 —— 选中的"10 月"随即被裁掉，
     看上去像是没选上。这个坑只在 flex 容器里出现，直接放在 el-form-item
     下（如上面的"关系"）不会有。 */
  flex: 1;
  min-width: 0;
  margin-bottom: 0;
}

.field-hint {
  margin-top: 4px;
  font-size: var(--wb-text-xs);
  line-height: 1.5;
  color: var(--wb-text-muted);
}

.empty {
  padding: 28px 0;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-align: center;
}
</style>
