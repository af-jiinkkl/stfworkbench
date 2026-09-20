<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Delete, EditPen, Plus } from '@element-plus/icons-vue'
import * as courseApi from '@/api/courseApi'
import * as semesterApi from '@/api/semesterApi'
import {
  DAYS_OF_WEEK,
  MAX_SECTION,
  MIN_SECTION,
  SECTIONS,
  SECTION_BANDS,
  WEEK_TYPE_EVERY,
  WEEK_TYPE_OPTIONS,
  occursOnWeek,
  sectionText,
  weekRangeText,
  weekTypeLabel,
  type Course,
  type CourseParams,
  type Semester,
  type SemesterParams,
} from '@/types/course'
import { addDays, diffInDays, today, weekdayOf } from '@/utils/date'

/**
 * 课程表。对应 docs/接口清单.md §10（学期）与 §11（课程）。
 *
 * 一屏分三层：
 * - **学期**：下拉切换，配合"新建 / 编辑 / 删除学期"
 * - **周次**：上一周 / 下一周。第几周由本页算，**不请求后端** ——
 *   后端只有一个 `listOnDate` 管"今天"，管不到"第 7 周的周三"
 * - **网格**：7 天 × 6 节，时段（早晨/上午/下午/晚上）由节次推导
 *
 * 日期换算只有一条链路：`学期.startDate + (第 N 周 - 1) * 7 + (星期几 - 1)`。
 * 这条链路的起点必须是周一 —— 后端为此拒绝了所有非周一的开始日期
 * （见 SemesterServiceImpl#requireMonday）。
 */

const loading = ref(false)
const semesters = ref<Semester[]>([])
const semesterId = ref<number | null>(null)
const courses = ref<Course[]>([])

/** 当前显示的周次，从 1 开始 */
const weekIndex = ref(1)

const semester = computed<Semester | null>(
  () => semesters.value.find(item => item.id === semesterId.value) ?? null,
)

const totalWeeks = computed(() => semester.value?.totalWeeks ?? 0)

/**
 * 今天是第几周。
 *
 * 今天落在学期之前（还没开学）时得到 ≤ 0，之后（放假了）会超过总周数 ——
 * 两种都夹回 [1, totalWeeks]，好让"回到本周"总是落在一个存在的周上。
 * 夹取只影响跳转目标，不影响任何判断：`occursOnWeek` 收到的仍是夹过的周次，
 * 而那种周次下本来也不会有课显示错。
 */
const todayWeek = computed(() => {
  const current = semester.value
  if (!current) {
    return 1
  }
  const raw = Math.floor(diffInDays(current.startDate, today()) / 7) + 1
  return Math.min(Math.max(raw, 1), current.totalWeeks)
})

/** 今天是否就落在当前显示的这一周里 */
const isTodayInView = computed(() => semester.value !== null && weekIndex.value === todayWeek.value)

/** 学期第一天与最后一天，显示在工具栏右侧 */
const rangeText = computed(() => {
  const current = semester.value
  if (!current) {
    return ''
  }
  return `${current.startDate} ~ ${addDays(current.startDate, current.totalWeeks * 7 - 1)}`
})

interface DayColumn {
  dayOfWeek: number
  label: string
  /** 这一列的日期，`yyyy-MM-dd` */
  date: string
  isToday: boolean
}

/**
 * 当前这一周的 7 个日期。
 *
 * 用对象数组而不是两个平行数组：`noUncheckedIndexedAccess` 打开之后，
 * 按下标取出来的是 `T | undefined`，模板里就得到处写 `?? ''` ——
 * 而那个默认值会把"算错了"和"这一天没日期"变成同一件事。
 */
const dayColumns = computed<DayColumn[]>(() => {
  const current = semester.value
  if (!current) {
    return []
  }
  const todayString = today()
  return DAYS_OF_WEEK.map((label, index) => {
    const dayOfWeek = index + 1
    // 第 N 周的星期 D：从学期第一天往后数 (N-1)*7 + (D-1) 天
    const date = addDays(current.startDate, (weekIndex.value - 1) * 7 + index)
    return { dayOfWeek, label, date, isToday: date === todayString }
  })
})

/** 当前这一周要画的课。单双周在这里生效 —— 与首页"今日课程"是同一条规则 */
const weekCourses = computed(() =>
  courses.value.filter(course => occursOnWeek(course, weekIndex.value)),
)

/** 6 × 7 个空格子，铺出网格的底 */
const slots = computed(() =>
  Array.from({ length: MAX_SECTION - MIN_SECTION + 1 }, (_, row) => row + MIN_SECTION)
    .flatMap(section =>
      DAYS_OF_WEEK.map((_, index) => ({ dayOfWeek: index + 1, section })),
    ),
)

/**
 * 某一天某一节上有哪些课。
 *
 * 只用来判断"这格是不是空的"（点了要填进去），**不用来画课** ——
 * 画课走的是每条课自己那一块（`grid-row` 跨行），否则一门 1-2 节的课
 * 会在两个格子里各出现一次，看着像两门课。
 */
function coursesAt(dayOfWeek: number, section: number): Course[] {
  return weekCourses.value.filter(
    course =>
      course.dayOfWeek === dayOfWeek
      && course.startSection <= section
      && section <= course.endSection,
  )
}

/** 课块在网格里的位置：列 = 星期几 + 1（第 1 列是时段），行 = 节次 + 1（第 1 行是表头） */
function blockStyle(course: Course) {
  return {
    gridColumn: `${course.dayOfWeek + 1}`,
    gridRow: `${course.startSection + 1} / span ${course.endSection - course.startSection + 1}`,
  }
}

function bandStyle(band: (typeof SECTION_BANDS)[number]) {
  return {
    gridColumn: '1',
    gridRow: `${band.from + 1} / span ${band.to - band.from + 1}`,
  }
}

/** "09-15 ~ 09-21"，工具栏中间那行日期 */
const weekDateRange = computed(() => {
  const columns = dayColumns.value
  const first = columns[0]?.date.slice(5) ?? ''
  const last = columns[columns.length - 1]?.date.slice(5) ?? ''
  return `${first} ~ ${last}`
})

/** 课块上那行"周次"，与首页今日课程用的是同一个说法（见 types/course.ts） */
function courseWeekText(course: Course): string {
  return weekRangeText(course.startWeek, course.endWeek)
}

// ---------- 加载 ----------

async function loadSemesters(): Promise<void> {
  semesters.value = await semesterApi.list()
}

/**
 * 拉当前学期的课程。
 *
 * **不动 `weekIndex`**：改完一门课重新拉一遍时，用户停在哪一周得留在哪一周 ——
 * 一保存就跳回"本周"的话，翻到第 7 周改一次就又被弹回去，看不出改的是不是那一周。
 * 周次只在**切学期**时重置（见 selectSemester）。
 *
 * 每次重新拉而不是就地改数组：新增或修改都会影响排序与单双周，
 * 本地推演一遍等于把后端的规则抄到前端，迟早对不上。
 */
async function loadCourses(): Promise<void> {
  const id = semesterId.value
  courses.value = []
  if (id === null) {
    return
  }
  loading.value = true
  try {
    courses.value = await courseApi.listBySemester(id)
  }
  finally {
    loading.value = false
  }
}

/** 切学期：课程重拉，周次回到该学期的"本周"（今天不在学期内则由 todayWeek 自己夹） */
async function selectSemester(id: number | null): Promise<void> {
  semesterId.value = id
  weekIndex.value = todayWeek.value
  await loadCourses()
}

async function init(): Promise<void> {
  await loadSemesters()
  // 默认选最近开始的那个学期（后端已按开始日期倒序返回）。
  // 此处不做"找包含今天的那个"—— 判断开学没有是后端的事，
  // 前端再推一遍就多一份"今天是第几周"的口径
  await selectSemester(semesters.value[0]?.id ?? null)
}

// 加载失败时拦截器已经弹过提示，这里接住是因为 onMounted 的回调没人 await ——
// 不接住会冒成 unhandled rejection，页面本身仍然可用（空课表 + 一个新建按钮）
onMounted(() => {
  void init().catch(() => {})
})

// ---------- 周次导航 ----------

function goPrevWeek(): void {
  if (weekIndex.value > 1) {
    weekIndex.value -= 1
  }
}

function goNextWeek(): void {
  if (weekIndex.value < totalWeeks.value) {
    weekIndex.value += 1
  }
}

function goTodayWeek(): void {
  weekIndex.value = todayWeek.value
}

// ---------- 课程表单 ----------

const courseDialogVisible = ref(false)
const editingCourseId = ref<number | null>(null)
const submitting = ref(false)
const courseFormRef = ref<FormInstance>()

const courseForm = reactive<CourseParams>({
  semesterId: 0,
  name: '',
  teacher: '',
  location: '',
  dayOfWeek: 1,
  startSection: 1,
  endSection: 2,
  startWeek: 1,
  endWeek: 18,
  weekType: WEEK_TYPE_EVERY,
})

const courseRules: FormRules<CourseParams> = {
  name: [
    { required: true, message: '请填写课程名', trigger: 'blur' },
    { max: 50, message: '不能超过 50 个字', trigger: 'blur' },
  ],
  teacher: [{ max: 50, message: '不能超过 50 个字', trigger: 'blur' }],
  location: [{ max: 50, message: '不能超过 50 个字', trigger: 'blur' }],
}

/** 结束节次的可选项：不能早于开始节次。后端也会拦，这里是为了不让用户白填一次 */
const endSectionOptions = computed(() =>
  SECTIONS.filter(section => section >= courseForm.startSection),
)

/** 结束周的可选项：不能早于起始周，也不能超过学期总周数 */
const endWeekOptions = computed(() =>
  Array.from({ length: totalWeeks.value }, (_, index) => index + 1)
    .filter(week => week >= courseForm.startWeek),
)

// 起始节次被往后调时，结束节次可能就落在它前面了，夹回来。
// 不夹的话会提交一个后端必然拒绝的组合，用户只拿到一句"开始节次不能晚于结束节次"
watch(() => courseForm.startSection, (start) => {
  if (courseForm.endSection < start) {
    courseForm.endSection = start
  }
})

watch(() => courseForm.startWeek, (start) => {
  if (courseForm.endWeek < start) {
    courseForm.endWeek = start
  }
})

async function openCreateCourse(dayOfWeek = 1, section = MIN_SECTION): Promise<void> {
  const current = semester.value
  if (!current) {
    ElMessage.warning('请先新建一个学期')
    return
  }

  editingCourseId.value = null
  Object.assign(courseForm, {
    semesterId: current.id,
    name: '',
    teacher: '',
    location: '',
    dayOfWeek,
    startSection: section,
    // 默认占两节，但不超过这一天最后一节
    endSection: Math.min(section + 1, MAX_SECTION),
    startWeek: 1,
    endWeek: current.totalWeeks,
    weekType: WEEK_TYPE_EVERY,
  })
  courseDialogVisible.value = true
  await nextTick()
  courseFormRef.value?.clearValidate()
}

async function openEditCourse(course: Course): Promise<void> {
  editingCourseId.value = course.id
  Object.assign(courseForm, {
    semesterId: course.semesterId,
    name: course.name,
    teacher: course.teacher,
    location: course.location,
    dayOfWeek: course.dayOfWeek,
    startSection: course.startSection,
    endSection: course.endSection,
    startWeek: course.startWeek,
    endWeek: course.endWeek,
    weekType: course.weekType,
  })
  courseDialogVisible.value = true
  await nextTick()
  courseFormRef.value?.clearValidate()
}

async function saveCourse(): Promise<void> {
  if (!courseFormRef.value) {
    return
  }

  try {
    await courseFormRef.value.validate()
  }
  catch {
    // 校验没过，字段下方已经有提示了，这里不需要再弹一个
    return
  }

  const id = editingCourseId.value
  const isCreate = id === null

  submitting.value = true
  try {
    const saved
      = id === null
        ? await courseApi.create({ ...courseForm })
        : await courseApi.update(id, { ...courseForm })

    courseDialogVisible.value = false
    await loadCourses()

    // 保存后如果这一周看不到它，就跳到它的起始周并说明原因。
    // 不跳的话，用户在第 2 周给第 5 周加了一门课，界面看上去像没保存上 ——
    // 而它其实已经写进库了（同消费页"落在筛选范围之外就重置筛选"那条）
    if (!occursOnWeek(saved, weekIndex.value)) {
      weekIndex.value = Math.min(Math.max(saved.startWeek, 1), totalWeeks.value)
      ElMessage.info(`已保存；这门课从第 ${saved.startWeek} 周开始，已跳到那一周`)
    }
    else {
      ElMessage.success(isCreate ? '已添加' : '已保存')
    }
  }
  catch {
    // 后端拒绝时（周次超出学期、被别人删了学期等）拦截器已经弹过提示，
    // 这里只需要让这次保存安静地结束。接住它的两个作用：
    // 弹窗**保持打开**，用户能就地改；以及不把异常冒成一条 unhandled rejection ——
    // 那种红字出现在控制台里，指向的是这次点击，读起来却像前端崩了
  }
  finally {
    submitting.value = false
  }
}

async function removeCourse(): Promise<void> {
  const id = editingCourseId.value
  if (id === null) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除「${courseForm.name}」吗？`,
      '删除课程',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  }
  catch {
    // 用户点了取消。ElMessageBox 取消时是 reject，不接住会冒成未处理的异常
    return
  }

  try {
    await courseApi.remove(id)
  }
  catch {
    // 同 saveCourse：拦截器弹过提示了，接住是为了不冒成 unhandled rejection。
    // 弹窗留着，用户可以重试或直接取消
    return
  }

  courseDialogVisible.value = false
  ElMessage.success('已删除')
  await loadCourses()
}

// ---------- 学期表单 ----------

const semesterDialogVisible = ref(false)
const editingSemesterId = ref<number | null>(null)
const semesterSubmitting = ref(false)
const semesterFormRef = ref<FormInstance>()

const semesterForm = reactive<SemesterParams>({
  name: '',
  startDate: '',
  totalWeeks: 18,
})

const semesterRules: FormRules<SemesterParams> = {
  name: [
    { required: true, message: '请填写学期名称', trigger: 'blur' },
    { max: 50, message: '不能超过 50 个字', trigger: 'blur' },
  ],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  totalWeeks: [{ required: true, message: '请填写总周数', trigger: 'change' }],
}

/**
 * 日期选择器只放行周一。
 *
 * 整张课表按"第 1 周的周一"换算每一天的日期，起点一旦不是周一，
 * 课表会整体偏几天 —— 而它看上去仍是一张完全正常的课表。
 * 后端同样会拒绝非周一（并说明那天是星期几），这里只是不让用户白选一次。
 *
 * 参数是 Element Plus 递给我们的 `Date`，不是从字符串解析出来的 ——
 * `getDay()` 在这里只作"是不是周一"的比较，没有跨时区换算的问题
 * （见 utils/date.ts 开头讲的那两个坑，那是另一回事）。
 */
function isNotMonday(date: Date): boolean {
  return date.getDay() !== 1
}

async function openCreateSemester(): Promise<void> {
  editingSemesterId.value = null
  // 默认下一个周一：学期多是从某个周一开的头。
  // 今天正好是周一也给下一个 —— "下周一"比"今天"更接近用户要填的那个日期
  const daysToNextMonday = (8 - weekdayOf(today())) % 7 || 7
  Object.assign(semesterForm, {
    name: '',
    startDate: addDays(today(), daysToNextMonday),
    totalWeeks: 18,
  })
  semesterDialogVisible.value = true
  await nextTick()
  semesterFormRef.value?.clearValidate()
}

async function openEditSemester(): Promise<void> {
  const current = semester.value
  if (!current) {
    return
  }
  editingSemesterId.value = current.id
  Object.assign(semesterForm, {
    name: current.name,
    startDate: current.startDate,
    totalWeeks: current.totalWeeks,
  })
  semesterDialogVisible.value = true
  await nextTick()
  semesterFormRef.value?.clearValidate()
}

async function saveSemester(): Promise<void> {
  if (!semesterFormRef.value) {
    return
  }

  try {
    await semesterFormRef.value.validate()
  }
  catch {
    return
  }

  const id = editingSemesterId.value
  const isCreate = id === null

  semesterSubmitting.value = true
  try {
    const saved
      = id === null
        ? await semesterApi.create({ ...semesterForm })
        : await semesterApi.update(id, { ...semesterForm })

    semesterDialogVisible.value = false
    ElMessage.success(isCreate ? '学期已创建' : '已保存')
    await loadSemesters()
    // 新建的学期直接切过去，否则用户建完还在原来那个学期的课表上，
    // 会以为没建成功。改的若是当前学期，也要重新拉一遍课
    await selectSemester(saved.id)
  }
  catch {
    // 改小总周数时若有课的周次落在范围外，后端会拒绝并说明是哪几门课 ——
    // 这句话正是用户接下去要照着改的，所以弹窗必须留着（同 saveCourse）
  }
  finally {
    semesterSubmitting.value = false
  }
}

async function removeSemester(): Promise<void> {
  const current = semester.value
  if (!current) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除学期「${current.name}」吗？该学期下的课程需要先删掉。`,
      '删除学期',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  }
  catch {
    return
  }

  try {
    await semesterApi.remove(current.id)
  }
  catch {
    // 学期下还有课时后端会拒绝。这条路径是**常规路径**（用户就是会先删旧的再建新的），
    // 提示里写着还差几门课，所以接住它、留在原地，不要冒成 unhandled rejection
    return
  }

  ElMessage.success('已删除')
  await loadSemesters()
  await selectSemester(semesters.value[0]?.id ?? null)
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1 class="title">
          课程表
        </h1>
        <p class="subtitle">
          一周一屏，翻到第几周就看到第几周。
        </p>
      </div>

      <el-button
        type="primary"
        :icon="Plus"
        :disabled="semester === null"
        @click="openCreateCourse()"
      >
        添加课程
      </el-button>
    </header>

    <!-- ========== 学期与周次 ========== -->
    <section class="wb-card panel toolbar">
      <div class="toolbar-row">
        <span class="toolbar-label">学期</span>
        <el-select
          :model-value="semesterId"
          class="semester-select"
          placeholder="选择学期"
          @update:model-value="selectSemester"
        >
          <el-option
            v-for="item in semesters"
            :key="item.id"
            :label="`${item.name}（共 ${item.totalWeeks} 周）`"
            :value="item.id"
          />
        </el-select>

        <el-button
          :icon="Plus"
          @click="openCreateSemester"
        >
          新建
        </el-button>
        <el-button
          :icon="EditPen"
          :disabled="semester === null"
          @click="openEditSemester"
        >
          编辑
        </el-button>
        <el-button
          :icon="Delete"
          :disabled="semester === null"
          @click="removeSemester"
        >
          删除
        </el-button>
      </div>

      <div
        v-if="semester"
        class="toolbar-row week-row"
      >
        <el-button
          :icon="ArrowLeft"
          :disabled="weekIndex <= 1"
          @click="goPrevWeek"
        >
          上一周
        </el-button>

        <div class="week-badge">
          <span class="week-number">第 {{ weekIndex }} 周</span>
          <span class="week-range">{{ weekDateRange }}</span>
        </div>

        <el-button
          :disabled="weekIndex >= totalWeeks"
          @click="goNextWeek"
        >
          下一周
          <el-icon class="el-icon--right">
            <ArrowRight />
          </el-icon>
        </el-button>

        <el-button
          v-if="!isTodayInView"
          link
          type="primary"
          @click="goTodayWeek"
        >
          回到本周（第 {{ todayWeek }} 周）
        </el-button>

        <span class="toolbar-meta">{{ rangeText }}</span>
      </div>
    </section>

    <!-- ========== 还没有学期 ========== -->
    <div
      v-if="!semesters.length"
      class="wb-card panel empty-panel"
    >
      <p class="empty-title">
        还没有学期
      </p>
      <p class="empty-hint">
        课表要按"第 1 周的周一"换算每一天的日期，所以先建一个学期。
      </p>
      <el-button
        type="primary"
        :icon="Plus"
        @click="openCreateSemester"
      >
        新建学期
      </el-button>
    </div>

    <!-- ========== 网格 ========== -->
    <div
      v-else
      v-loading="loading"
      class="wb-card panel grid-panel"
    >
      <div class="grid-scroll">
        <div class="grid">
          <!-- 表头：星期与这一周的日期 -->
          <div
            class="corner"
            :style="{ gridColumn: '1', gridRow: '1' }"
          />

          <div
            v-for="column in dayColumns"
            :key="column.dayOfWeek"
            class="day-head"
            :class="{ 'is-today': column.isToday }"
            :style="{ gridColumn: `${column.dayOfWeek + 1}`, gridRow: '1' }"
          >
            <span class="day-name">{{ column.label }}</span>
            <span class="day-date">{{ column.date.slice(5) }}</span>
            <span
              v-if="column.isToday"
              class="day-today-tag"
            >今天</span>
          </div>

          <!-- 左列：时段。由节次推导，不存库 -->
          <div
            v-for="band in SECTION_BANDS"
            :key="band.label"
            class="band"
            :style="bandStyle(band)"
          >
            <span class="band-label">{{ band.label }}</span>
            <span class="band-sections">
              {{ band.from === band.to ? `第 ${band.from} 节` : `第 ${band.from}-${band.to} 节` }}
            </span>
          </div>

          <!-- 底格。点一下就带着这个格子新建一门课 -->
          <div
            v-for="slot in slots"
            :key="`${slot.dayOfWeek}-${slot.section}`"
            class="slot"
            :class="{ 'is-taken': coursesAt(slot.dayOfWeek, slot.section).length > 0 }"
            :style="{ gridColumn: `${slot.dayOfWeek + 1}`, gridRow: `${slot.section + 1}` }"
            @click="openCreateCourse(slot.dayOfWeek, slot.section)"
          />

          <!-- 课块。跨多个节次就跨多行，不重复画 -->
          <div
            v-for="course in weekCourses"
            :key="course.id"
            class="block"
            :style="blockStyle(course)"
            :title="`${course.name}｜${sectionText(course.startSection, course.endSection)} · ${weekTypeLabel(course.weekType)}｜${courseWeekText(course)}`"
            @click="openEditCourse(course)"
          >
            <span class="block-name">{{ course.name }}</span>
            <span
              v-if="course.location"
              class="block-meta"
            >{{ course.location }}</span>
            <span
              v-if="course.teacher"
              class="block-meta"
            >{{ course.teacher }}</span>
            <span class="block-week">{{ courseWeekText(course) }}</span>
          </div>
        </div>
      </div>

      <p
        v-if="!weekCourses.length && !loading"
        class="empty"
      >
        {{ courses.length ? `第 ${weekIndex} 周没有课` : '这个学期还没有排课，点任意格子就能添加' }}
      </p>
    </div>

    <!-- ========== 课程表单 ========== -->
    <el-dialog
      v-model="courseDialogVisible"
      :title="editingCourseId === null ? '添加课程' : '编辑课程'"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="courseFormRef"
        :model="courseForm"
        :rules="courseRules"
        label-width="72px"
      >
        <el-form-item label="学期">
          <!-- 只读：学期由页面上那个下拉框决定。这里再给一次选择的话，
               用户可能把课加到另一个学期，然后"添加成功但看不见" -->
          <el-input
            :model-value="semester?.name ?? ''"
            disabled
          />
        </el-form-item>

        <el-form-item
          label="课程名"
          prop="name"
        >
          <el-input
            v-model="courseForm.name"
            maxlength="50"
            placeholder="高等数学"
          />
        </el-form-item>

        <el-form-item label="星期">
          <el-select v-model="courseForm.dayOfWeek">
            <el-option
              v-for="(label, index) in DAYS_OF_WEEK"
              :key="label"
              :label="label"
              :value="index + 1"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="节次">
          <div class="range-row">
            <el-form-item>
              <el-select v-model="courseForm.startSection">
                <el-option
                  v-for="section in SECTIONS"
                  :key="section"
                  :label="`第 ${section} 节`"
                  :value="section"
                />
              </el-select>
            </el-form-item>
            <span class="range-dash">至</span>
            <el-form-item>
              <el-select v-model="courseForm.endSection">
                <el-option
                  v-for="section in endSectionOptions"
                  :key="section"
                  :label="`第 ${section} 节`"
                  :value="section"
                />
              </el-select>
            </el-form-item>
          </div>
        </el-form-item>

        <el-form-item label="周次">
          <div class="range-row">
            <el-form-item>
              <el-select v-model="courseForm.startWeek">
                <el-option
                  v-for="week in totalWeeks"
                  :key="week"
                  :label="`第 ${week} 周`"
                  :value="week"
                />
              </el-select>
            </el-form-item>
            <span class="range-dash">至</span>
            <el-form-item>
              <el-select v-model="courseForm.endWeek">
                <el-option
                  v-for="week in endWeekOptions"
                  :key="week"
                  :label="`第 ${week} 周`"
                  :value="week"
                />
              </el-select>
            </el-form-item>
          </div>
          <p class="field-hint">
            可选范围是 1 到 {{ totalWeeks }} 周（该学期的总周数）
          </p>
        </el-form-item>

        <el-form-item label="周类型">
          <el-radio-group v-model="courseForm.weekType">
            <el-radio-button
              v-for="option in WEEK_TYPE_OPTIONS"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          label="老师"
          prop="teacher"
        >
          <el-input
            v-model="courseForm.teacher"
            maxlength="50"
            placeholder="选填"
          />
        </el-form-item>

        <el-form-item
          label="地点"
          prop="location"
        >
          <el-input
            v-model="courseForm.location"
            maxlength="50"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button
          v-if="editingCourseId !== null"
          class="footer-delete"
          :icon="Delete"
          @click="removeCourse"
        >
          删除
        </el-button>
        <el-button @click="courseDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="saveCourse"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 学期表单 ========== -->
    <el-dialog
      v-model="semesterDialogVisible"
      :title="editingSemesterId === null ? '新建学期' : '编辑学期'"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="semesterFormRef"
        :model="semesterForm"
        :rules="semesterRules"
        label-width="88px"
      >
        <el-form-item
          label="名称"
          prop="name"
        >
          <el-input
            v-model="semesterForm.name"
            maxlength="50"
            placeholder="2026 春季学期"
          />
        </el-form-item>

        <el-form-item
          label="开始日期"
          prop="startDate"
        >
          <el-date-picker
            v-model="semesterForm.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择第 1 周的周一"
            :disabled-date="isNotMonday"
          />
          <p class="field-hint">
            选第 1 周的周一。整张课表按它换算日期，所以只放行周一。
          </p>
        </el-form-item>

        <el-form-item
          label="总周数"
          prop="totalWeeks"
        >
          <el-input-number
            v-model="semesterForm.totalWeeks"
            :min="1"
            :max="60"
          />
          <p class="field-hint">
            改小时若有课程的周次落在范围外，会先让你调整那些课程。
          </p>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="semesterDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="semesterSubmitting"
          @click="saveSemester"
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

/* ---------- 工具栏 ---------- */
.panel {
  padding: 16px 20px;
}

.toolbar {
  margin-bottom: 16px;
}

.toolbar-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.toolbar-row + .toolbar-row {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--wb-border);
}

.toolbar-label {
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

.semester-select {
  width: 220px;
}

.week-badge {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 108px;
  padding: 0 4px;
  text-align: center;
}

.week-number {
  font-size: var(--wb-text-base);
  font-weight: 600;
}

.week-range {
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
}

/* 学期起止推到最右边，它是个背景信息，不参与操作 */
.toolbar-meta {
  margin-left: auto;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-faint);
}

/* ---------- 空状态 ---------- */
.empty-panel {
  padding: 48px 20px;
  text-align: center;
}

.empty-title {
  font-size: var(--wb-text-lg);
  font-weight: 600;
}

.empty-hint {
  margin: 8px 0 20px;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

/* ---------- 网格 ---------- */
.grid-panel {
  padding: 16px 20px 20px;
}

/* 窄屏时横向滚动，而不是把 7 列压扁到看不清 */
.grid-scroll {
  overflow-x: auto;
}

.grid {
  display: grid;
  /* 第 1 列是时段带，其余 7 列是星期。minmax(0, 1fr) 让长课名可以在格子里截断，
     而不是把整列撑宽（默认的 min-width: auto 会） */
  grid-template-columns: 68px repeat(7, minmax(0, 1fr));
  /* 第 1 行是表头，6 节各占一行。行高给足，一门跨 2 节的课要放得下课名和地点 */
  grid-template-rows: auto repeat(6, minmax(76px, auto));
  gap: 1px;
  min-width: 760px;
  background-color: var(--wb-border);
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius);
}

.corner,
.day-head,
.band,
.slot,
.block {
  background-color: var(--wb-surface);
}

/* ---------- 表头 ---------- */
.day-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  justify-content: center;
  padding: 10px 4px;
}

.day-name {
  font-size: var(--wb-text-sm);
  font-weight: 500;
}

.day-date {
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
}

/* 今天那一列：整块淡色底 + 一个角标，不改变字号，免得表头高低不齐 */
.day-head.is-today {
  background-color: var(--wb-primary-soft);
}

.day-head.is-today .day-name,
.day-head.is-today .day-date {
  color: var(--wb-text);
}

.day-today-tag {
  font-size: var(--wb-text-xs);
  color: var(--wb-primary);
}

/* ---------- 时段带 ---------- */
.band {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  justify-content: center;
  padding: 8px 2px;
}

.band-label {
  font-size: var(--wb-text-sm);
  font-weight: 500;
  color: var(--wb-text-secondary);
}

.band-sections {
  font-size: var(--wb-text-xs);
  color: var(--wb-text-faint);
}

/* ---------- 底格 ---------- */
.slot {
  cursor: pointer;
  transition: background-color 0.12s ease;
}

.slot:hover {
  background-color: var(--wb-surface-hover);
}

/* 已经有课的格子不响应悬停、也不可点 —— 点上去只会走进"新增"，
   而用户想的是编辑那门课，课块本身就能点开编辑 */
.slot.is-taken {
  cursor: default;
}

.slot.is-taken:hover {
  background-color: var(--wb-surface);
}

/* ---------- 课块 ---------- */
/* 课块画在底格**之上**：它带 grid-row 跨行，和底格占同一批格子。
   DOM 顺序在底格之后，本来就在上层，这里再加个 z-index 说明意图 */
.block {
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
  justify-content: center;
  padding: 6px 8px;
  margin: 2px;
  overflow: hidden;
  cursor: pointer;
  background-color: var(--wb-primary-soft);
  border-left: 3px solid var(--wb-primary);
  border-radius: var(--wb-radius-sm);
  transition: background-color 0.12s ease;
}

/* 悬停只加深左侧那道竖条，不换底色：换底色会让整块课在网格里
   "跳"一下，而它本来就和周围的白格子对比明显 */
.block:hover {
  border-left-color: var(--wb-primary-hover);
}

.block-name {
  overflow: hidden;
  font-size: var(--wb-text-sm);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-meta {
  overflow: hidden;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-week {
  font-size: var(--wb-text-xs);
  color: var(--wb-text-faint);
}

/* ---------- 表单 ---------- */
/* 两个并排的下拉（节次、周次）与"至"字。
   flex: 1 不能少：el-select 的宽度是 100%，而它在 flex 行里作为
   "内容尺寸"的项会被压到只剩一个箭头宽，选中值随即被裁掉，
   看上去像没选上（见 AnniversaryView 的 .date-row，同一个坑） */
.range-row {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.range-row :deep(.el-form-item) {
  flex: 1;
  min-width: 0;
  margin-bottom: 0;
}

.range-dash {
  flex-shrink: 0;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

.field-hint {
  margin-top: 4px;
  font-size: var(--wb-text-xs);
  line-height: 1.5;
  color: var(--wb-text-muted);
}

/* 删除放最左边，和"取消/保存"拉开距离，免得点错。
   `margin-right: auto` 单独写在按钮上**不管用** —— el-dialog__footer 是
   `text-align: right` 的行内布局，auto 外边距只对块级/弹性项生效。
   所以得先把它改成 flex。它是 el-dialog 渲染的，不是本组件的模板，
   scoped 选择器够不到，要 :deep() */
:deep(.el-dialog__footer) {
  display: flex;
  justify-content: flex-end;
}

.footer-delete {
  margin-right: auto;
}

.empty {
  padding: 20px 0 4px;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-align: center;
}
</style>
