<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { EChartsCoreOption } from 'echarts/core'
import EChart from '@/components/EChart.vue'
import * as expenseApi from '@/api/expenseApi'
import { EXPENSE_CATEGORIES } from '@/types/expense'
import type { CategorySummary, Expense, ExpenseParams, MonthSummary } from '@/types/expense'
import { addDays, addMonths, today } from '@/utils/date'
import { money } from '@/utils/money'

/**
 * 每日消费。对应 docs/接口清单.md §9 的 7 个接口。
 *
 * 页面分三块：上面的两张图（看趋势）、中间的筛选栏、下面的明细列表。
 * **两处统计口径都是后端算的**，前端只负责画 —— 汇总一旦在两边各算一遍，
 * 迟早会出现"图和列表对不上"，而差的那几分钱没人会当成 bug 报上来。
 *
 * 筛选口径：日期区间与分类**同时**作用于列表和分类饼图；
 * 月度趋势图按自然年走，用自己的年份选择器（它的接口只认 `year`）。
 * 两个控件各自贴着自己管的那块图，不放在一起，免得看不出谁管谁。
 */

const PAGE_SIZE = 10

// ---------- 区间 ----------

/**
 * 本月 1 号 ~ 本月最后一天。
 *
 * 用字符串拼而不是再造一个 Date：`today()` 本来就是本地时区的 `yyyy-MM-dd`
 * （见 utils/date.ts 开头那段关于 toISOString 的说明），截出年月再接 `-01`
 * 拿到的就是本月的第一天，不必再跟时区打交道。
 */
function currentMonthRange(): [string, string] {
  const first = `${today().slice(0, 7)}-01`
  // "下月 1 号减一天"永远是本月最后一天。addMonths 自己处理了月末溢出，
  // 所以这里不用去算 28/29/30/31 —— 那种算术正是 addMonths 存在的理由
  return [first, addDays(addMonths(first, 1), -1)]
}

const RANGE_PRESETS = [
  { label: '本月', value: 'month' },
  { label: '近三月', value: 'quarter' },
  { label: '本年', value: 'year' },
  { label: '不限', value: 'all' },
] as const

type RangePreset = (typeof RANGE_PRESETS)[number]['value']

function rangeOf(preset: RangePreset): [string, string] | null {
  const endOfThisMonth = currentMonthRange()[1]

  switch (preset) {
    case 'month':
      return currentMonthRange()
    case 'quarter':
      // 近三月 = 含本月在内的三个自然月。往前推两个月的**月首**，
      // 而不是"今天减 90 天" —— 后者会得到一个 5 月 13 号这种奇怪的起点
      return [`${addMonths(today(), -2).slice(0, 7)}-01`, endOfThisMonth]
    case 'year':
      return [`${today().slice(0, 4)}-01-01`, `${today().slice(0, 4)}-12-31`]
    case 'all':
      return null
  }
}

const range = ref<[string, string] | null>(rangeOf('month'))
const category = ref('')

/**
 * 当前区间命中的预设，**从 `range` 推导出来的，不是独立的一个 ref**。
 *
 * 独立存一份状态就有两个真相来源：用户手动改了日期，`preset` 还停在"本月"，
 * 于是按钮亮着"本月"而实际筛的是别的区间 —— 界面在说谎，且没有任何地方会报错。
 * 推导出来就不存在这个问题，代价是每次要全比一遍，三项而已。
 *
 * 手动选的区间匹配不上任何预设时返回空串，按钮组自然一个都不选中 ——
 * 这是实话，不该硬套一个最接近的。
 */
const activePreset = computed<RangePreset | ''>(() => {
  const current = range.value
  if (!current) {
    return 'all'
  }
  for (const preset of ['month', 'quarter', 'year'] as const) {
    const candidate = rangeOf(preset)
    if (candidate && candidate[0] === current[0] && candidate[1] === current[1]) {
      return preset
    }
  }
  return ''
})

function applyPreset(value: RangePreset): void {
  range.value = rangeOf(value)
  handleSearch()
}

// ---------- 列表 ----------

const loading = ref(false)
const list = ref<Expense[]>([])
const total = ref(0)
const pageNum = ref(1)

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const current = range.value
    const result = await expenseApi.page({
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
      startDate: current?.[0],
      endDate: current?.[1],
      category: category.value,
    })
    list.value = result.records
    total.value = result.total
  }
  finally {
    // 出错时 request.ts 的拦截器已经弹过提示了，这里只负责把 loading 收掉
    loading.value = false
  }
}

// ---------- 图表 ----------

const categorySummary = ref<CategorySummary[]>([])
const monthSummary = ref<MonthSummary[]>([])
const trendYear = ref(Number(today().slice(0, 4)))

/** 趋势图的年份选项：今年往前数五年。再往前对个人记账没有意义 */
const YEAR_OPTIONS = Array.from({ length: 5 }, (_, i) => trendYear.value - i)

async function loadPie(): Promise<void> {
  const current = range.value
  categorySummary.value = await expenseApi.summaryByCategory(current?.[0], current?.[1])
}

async function loadTrend(): Promise<void> {
  monthSummary.value = await expenseApi.summaryByMonth(trendYear.value)
}

/**
 * 三个请求一起发，但**各管各的 loading**。
 *
 * 合成一个 loading 的话，慢的那张图会把已经拿到的列表也一起遮住 ——
 * 用户看着一片转圈，其实列表早就好了。
 */
function refreshAll(): void {
  // 挨个 catch 掉：拦截器已经弹过提示，这里只需要让这轮刷新安静地结束。
  // 不接住的话会冒成 unhandled rejection，控制台多一条红字吓人
  void Promise.all([loadList(), loadPie(), loadTrend()]).catch(() => {})
}

const rangeTotal = computed(() =>
  categorySummary.value.reduce((sum, item) => sum + item.amount, 0),
)

/**
 * 饼图那句话里的区间说明。
 *
 * 饼图吃的是**列表上面那条筛选栏**里的日期区间（接口 §9 的分类汇总只收
 * startDate / endDate，收不了分类）。可那个筛选栏在饼图**下面**，
 * 光看卡片本身看不出"合计 ¥139.50"是哪个区间的钱 —— 换个区间数字会变，
 * 而用户以为自己看的是全部。所以把区间直接写在卡片上，让这个联动是看得见的。
 *
 * 分类筛选刻意**不影响**饼图（接口收不了这个参数）：饼图回答的是
 * "这段时间的钱花在哪些分类上"，筛成单一分类之后它就只剩一块，没有信息量了。
 */
const rangeLabel = computed(() => {
  const current = range.value
  return current ? `${current[0]} ~ ${current[1]}` : '全部时间'
})

const trendTotal = computed(() =>
  monthSummary.value.reduce((sum, item) => sum + item.amount, 0),
)

const pieOption = computed<EChartsCoreOption>(() => ({
  tooltip: {
    trigger: 'item',
    // 走 valueFormatter 而不是 formatter：后者是回调签名最复杂的一个，
    // 而这个只需要把数字变成 ¥x.xx，用不上 params 里的其他东西。
    // 参数标注成 unknown 是因为下面这个 option 的类型是 EChartsCoreOption，
    // 它对 tooltip 内部是宽松的，推断不出参数类型 —— 不标就是隐式 any，构建直接失败
    valueFormatter: (value: unknown) => `¥${money(Number(value))}`,
  },
  legend: {
    orient: 'vertical',
    right: 0,
    top: 'middle',
    itemWidth: 8,
    itemHeight: 8,
    // 图例上直接带金额。饼图本身不标数（六块挤在一起会互相压），
    // 要让金额可读就得有个地方写它，图例是现成的位置
    formatter: (name: string) => {
      const found = categorySummary.value.find(item => item.category === name)
      return found ? `${name}  ¥${money(found.amount)}` : name
    },
  },
  series: [
    {
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['32%', '50%'],
      label: { show: false },
      labelLine: { show: false },
      data: categorySummary.value.map(item => ({ name: item.category, value: item.amount })),
    },
  ],
}))

const lineOption = computed<EChartsCoreOption>(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (value: unknown) => `¥${money(Number(value))}`,
  },
  // containLabel 让坐标轴文字算进边距，否则 12 个月的标签会被裁掉两端
  grid: { left: 8, right: 16, top: 16, bottom: 4, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    // "2026-01" → "1月"。年份在选择器上，横轴再写一遍 12 次纯属噪音
    data: monthSummary.value.map(item => `${Number(item.month.slice(5))}月`),
  },
  yAxis: {
    type: 'value',
    axisLabel: { formatter: '¥{value}' },
  },
  series: [
    {
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.12 },
      data: monthSummary.value.map(item => item.amount),
    },
  ],
}))

// ---------- 筛选交互 ----------

function handleSearch(): void {
  // 换了筛选条件必须回到第一页。停在第 3 页去筛一个只有 2 条结果的条件，
  // 会得到一张空白列表，看上去像是筛选坏了
  pageNum.value = 1
  refreshAll()
}

function handlePageChange(): void {
  void loadList()
}

function handleReset(): void {
  range.value = null
  category.value = ''
  handleSearch()
}

/**
 * 这一笔会不会被当前筛选挡住。
 *
 * `yyyy-MM-dd` 是等宽的，高位在前，所以**字符串比较就是日期比较** ——
 * 不用转成 Date，也就绕开了时区那一整套容易出错的换算。
 */
function isHiddenByFilter(item: Expense): boolean {
  if (category.value && item.category !== category.value) {
    return true
  }
  const current = range.value
  if (!current) {
    return false
  }
  return item.expenseDate < current[0] || item.expenseDate > current[1]
}

// ---------- 表单 ----------

interface ExpenseForm {
  amount: number | undefined
  category: string
  expenseDate: string
  remark: string
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<ExpenseForm>({
  amount: undefined,
  category: '',
  expenseDate: today(),
  remark: '',
})

const rules: FormRules<ExpenseForm> = {
  amount: [
    { required: true, message: '填个金额', trigger: 'blur' },
  ],
  category: [
    { required: true, message: '选个分类', trigger: 'change' },
  ],
  expenseDate: [
    { required: true, message: '选个日期', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注不能超过 255 个字', trigger: 'blur' },
  ],
}

async function openCreate(): Promise<void> {
  editingId.value = null
  form.amount = undefined
  form.category = ''
  // 默认今天，但**可以改** —— 补录前几天忘了记的是这个页面的常见操作
  form.expenseDate = today()
  form.remark = ''
  dialogVisible.value = true

  // 弹窗用了 destroy-on-close，表单是刚建出来的，校验态本来就是空的；
  // 留着是为了防止将来去掉 destroy-on-close 后残留上一次的红字
  await nextTick()
  formRef.value?.clearValidate()
}

/**
 * 打开编辑。
 *
 * 这里**不像备忘录那样先拉一次详情**：那个列表接口刻意不返回正文，
 * 而消费的列表项字段已经和详情一模一样（同一个 VO），再请求一次纯属多余。
 */
async function openEdit(item: Expense): Promise<void> {
  editingId.value = item.id
  form.amount = item.amount
  form.category = item.category
  form.expenseDate = item.expenseDate
  form.remark = item.remark
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
    // 校验没过，字段下方已经有提示了，不需要再弹一个
    return
  }

  const amount = form.amount
  if (amount === undefined) {
    // 上面那条 required 规则已经拦住了，这里只是让 TS 把类型收窄到 number，
    // 别落到下面的 `as number` —— 硬掰类型是会骗人的
    return
  }

  const payload: ExpenseParams = {
    amount,
    category: form.category,
    expenseDate: form.expenseDate,
    // 不去空格：后端本来就会归一（ExpenseServiceImpl#apply）。
    // 前端再 trim 一遍就多出一个"到底谁说的算"的口径
    remark: form.remark,
  }

  const id = editingId.value
  const isCreate = id === null

  submitting.value = true
  try {
    const saved = isCreate
      ? await expenseApi.create(payload)
      : await expenseApi.update(id, payload)

    dialogVisible.value = false

    // 新记录的日期通常也是最近的，而列表按日期倒序，所以它多半在第一页
    if (isCreate) {
      pageNum.value = 1
    }

    // 存进去的日期可能落在当前筛选区间之外（默认区间是本月，而这是一笔补录的
    // 上个月消费），也可能分类对不上筛着的那个。那样保存成功、提示也弹了，
    // 列表里却找不到它 —— 不报错，只是安静地不对，正是本项目最防的那一类。
    // 所以主动把筛选清掉，并且明说一句，而不是让用户自己发现
    const hidden = isHiddenByFilter(saved)
    if (hidden) {
      range.value = null
      category.value = ''
    }

    ElMessage.success(
      hidden ? '已保存；这一笔不在当前筛选范围内，已重置筛选' : (isCreate ? '已保存' : '已更新'),
    )

    await Promise.all([loadList(), loadPie()])
  }
  finally {
    submitting.value = false
  }
}

async function remove(item: Expense): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.expenseDate} ${item.category} ¥${money(item.amount)}」吗？`,
      '删除消费记录',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  }
  catch {
    // 用户点了取消。ElMessageBox 取消时是 reject，不接住会冒成未处理的异常
    return
  }

  await expenseApi.remove(item.id)
  ElMessage.success('已删除')

  // 删掉的是当前页最后一条时往前退一页。不退的话会停在一张空列表上，
  // 而上面的总数又明明不为 0 —— 看上去像数据丢了
  if (list.value.length === 1 && pageNum.value > 1) {
    pageNum.value -= 1
  }
  refreshAll()
}

onMounted(refreshAll)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1 class="title">
          每日消费
        </h1>
        <p class="subtitle">
          花在哪儿了，一眼看得见。
        </p>
      </div>

      <el-button
        type="primary"
        :icon="Plus"
        @click="openCreate"
      >
        记一笔
      </el-button>
    </header>

    <!-- ========== 两张图 ========== -->
    <section class="charts">
      <div class="wb-card chart-card">
        <div class="chart-head">
          <h2 class="chart-title">
            分类占比
          </h2>
          <span class="chart-note">
            <span class="chart-range">{{ rangeLabel }}</span>
            <template v-if="rangeTotal > 0">合计 ¥{{ money(rangeTotal) }}</template>
          </span>
        </div>

        <div class="chart-box">
          <EChart
            v-if="categorySummary.length"
            :option="pieOption"
          />
          <p
            v-else
            class="chart-empty"
          >
            这段时间还没有记录
          </p>
        </div>
      </div>

      <div class="wb-card chart-card">
        <div class="chart-head">
          <h2 class="chart-title">
            月度趋势
          </h2>
          <!-- 年份选择器贴在这张图上：两张图的数据源不同（一个吃日期区间、
               一个只吃 year），控件跟着各自管的图走才看不出歧义 -->
          <el-select
            v-model="trendYear"
            class="year-select"
            size="small"
            @change="loadTrend"
          >
            <el-option
              v-for="year in YEAR_OPTIONS"
              :key="year"
              :label="`${year} 年`"
              :value="year"
            />
          </el-select>
        </div>

        <div class="chart-box">
          <EChart
            v-if="trendTotal > 0"
            :option="lineOption"
          />
          <p
            v-else
            class="chart-empty"
          >
            {{ trendYear }} 年还没有记录
          </p>
        </div>
      </div>
    </section>

    <!-- ========== 明细 ========== -->
    <section class="wb-card panel">
      <div class="filter-row">
        <el-radio-group
          :model-value="activePreset"
          @change="applyPreset($event as RangePreset)"
        >
          <el-radio-button
            v-for="preset in RANGE_PRESETS"
            :key="preset.value"
            :value="preset.value"
          >
            {{ preset.label }}
          </el-radio-button>
        </el-radio-group>

        <el-date-picker
          v-model="range"
          type="daterange"
          unlink-panels
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          class="range-picker"
        />

        <el-select
          v-model="category"
          placeholder="全部分类"
          clearable
          class="category-select"
        >
          <el-option
            v-for="item in EXPENSE_CATEGORIES"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>

        <el-button
          type="primary"
          :icon="Search"
          @click="handleSearch"
        >
          查询
        </el-button>
        <el-button
          :icon="Refresh"
          @click="handleReset"
        >
          重置
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        class="expense-table"
        @row-click="openEdit"
      >
        <el-table-column
          prop="expenseDate"
          label="日期"
          width="120"
        />

        <el-table-column
          label="分类"
          width="100"
        >
          <template #default="{ row }">
            <span class="category-tag">{{ row.category }}</span>
          </template>
        </el-table-column>

        <el-table-column
          label="金额"
          width="140"
          align="right"
        >
          <template #default="{ row }">
            <span class="amount">¥{{ money(row.amount) }}</span>
          </template>
        </el-table-column>

        <el-table-column
          label="备注"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="remark">{{ row.remark || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column
          width="70"
          align="right"
        >
          <template #default="{ row }">
            <span class="row-actions">
              <el-button
                link
                :icon="Delete"
                title="删除"
                @click.stop="remove(row)"
              />
            </span>
          </template>
        </el-table-column>

        <template #empty>
          <p class="empty">
            {{ category || range ? '没有符合条件的记录' : '还没有记录，点右上角「记一笔」开始吧' }}
          </p>
        </template>
      </el-table>

      <!-- 只有一页时不显示翻页器：一个永远禁用的"下一页"没有信息量 -->
      <div
        v-if="total > PAGE_SIZE"
        class="pager"
      >
        <el-pagination
          v-model:current-page="pageNum"
          :total="total"
          :page-size="PAGE_SIZE"
          layout="prev, pager, next, total"
          background
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <!-- ========== 记一笔 / 编辑 ========== -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '记一笔' : '编辑消费'"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item
          label="金额"
          prop="amount"
        >
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :max="99999999.99"
            :precision="2"
            :step="10"
            controls-position="right"
            class="full-width"
            placeholder="0.00"
          />
        </el-form-item>

        <el-form-item
          label="分类"
          prop="category"
        >
          <el-select
            v-model="form.category"
            placeholder="选一个"
            class="full-width"
          >
            <el-option
              v-for="item in EXPENSE_CATEGORIES"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>

        <el-form-item
          label="日期"
          prop="expenseDate"
        >
          <!-- 不 clearable：日期不该有"空"这个状态，清掉就该填回今天 -->
          <el-date-picker
            v-model="form.expenseDate"
            type="date"
            :clearable="false"
            value-format="YYYY-MM-DD"
            class="full-width"
          />
        </el-form-item>

        <el-form-item
          label="备注"
          prop="remark"
        >
          <el-input
            v-model="form.remark"
            maxlength="255"
            placeholder="选填，比如买了什么"
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

/* ---------- 图表 ---------- */
.charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

/* 窄屏下并排会把两张图都压得没法看，不如上下叠着 */
@media (max-width: 900px) {
  .charts {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  padding: 16px 20px 12px;
}

.chart-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.chart-title {
  font-size: var(--wb-text-base);
  font-weight: 600;
}

.chart-note {
  display: flex;
  gap: 10px;
  align-items: baseline;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

/* 区间比合计再轻一档：它是**说明**合计是从哪来的，主角还是那个数字 */
.chart-range {
  font-size: var(--wb-text-xs);
  color: var(--wb-text-faint);
}

/* EChart 的根元素是 height:100%，所以这里必须给出**具体高度**，
   给个 min-height 是不行的 —— 容器高度算成 0，图就不出来了 */
.chart-box {
  height: 240px;
}

/* 年份选择器要窄一些，不然它会占满整个卡片头部，把标题挤没 */
.year-select {
  width: 108px;
}

.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

/* ---------- 筛选 ---------- */
.panel {
  padding: 16px 20px 20px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

/* el-date-picker / el-select 默认宽度 100%，放进 flex 行里会把按钮挤出去。
   给它们各自定宽，让按钮保持原样 */
.range-picker {
  width: 260px;
}

.category-select {
  width: 140px;
}

/* ---------- 表格 ---------- */
.expense-table {
  width: 100%;
}

/* 整行可点进编辑（见 @row-click），给个手型提示 */
.expense-table :deep(.el-table__row) {
  cursor: pointer;
}

.category-tag {
  display: inline-block;
  padding: 1px 8px;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
  background-color: var(--wb-surface-hover);
  border-radius: var(--wb-radius-sm);
}

.amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.remark {
  color: var(--wb-text-muted);
}

/* 与生日纪念日、备忘录两页同一套：平时收起来，悬停或键盘聚焦时才出现 */
.row-actions {
  display: flex;
  justify-content: flex-end;
  opacity: 0;
  transition: opacity 0.12s ease;
}

/* 整条选择器都放进 :deep() 里。写成 `:deep(.el-table__row:hover) .row-actions`
   也能匹配上，但那后半段就落到了作用域之外，将来这个类名在别处重名会互相影响 */
.expense-table :deep(.el-table__row:hover .row-actions),
.expense-table :deep(.el-table__row:focus-within .row-actions) {
  opacity: 1;
}

/* ---------- 分页 / 空态 ---------- */
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  margin-top: 8px;
  border-top: 1px solid var(--wb-border);
}

.empty {
  padding: 32px 0;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-align: center;
}

/* ---------- 表单 ---------- */
.full-width {
  width: 100%;
}
</style>
