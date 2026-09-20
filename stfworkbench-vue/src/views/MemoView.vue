<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import * as memoApi from '@/api/memoApi'
import type { Memo, MemoParams } from '@/types/memo'

/**
 * 备忘录。对应 docs/接口清单.md §6 的 5 个接口。
 *
 * 这是目前唯一一个**分页**的模块：每日计划和生日纪念日的量天然很小，
 * 备忘则会越攒越多，列表必须分页。也是唯一带搜索的。
 *
 * 列表接口不返回正文（只给摘要），所以"点一条"要再发一次详情请求。
 * 这也是为什么打开编辑有一个独立的 loading —— 正文还在路上。
 */

const PAGE_SIZE = 10

const loading = ref(false)
const list = ref<Memo[]>([])
const total = ref(0)
const pageNum = ref(1)
const keyword = ref('')

async function load(): Promise<void> {
  loading.value = true
  try {
    // 关键词只去空格、不截断：截断是后端的事（见 MemoServiceImpl#normalizeKeyword），
    // 前端再截一遍就多出一个可能对不上的口径
    const result = await memoApi.page(pageNum.value, PAGE_SIZE, keyword.value.trim())
    list.value = result.records
    total.value = result.total
  }
  finally {
    // 出错时 request.ts 的拦截器已经弹过提示了，这里只负责把 loading 收掉。
    // 不 catch 掉异常，是为了让调用方（比如 save 之后的重新加载）能感知到失败
    loading.value = false
  }
}

function handleSearch(): void {
  // 换了搜索条件必须回到第一页。停在第 3 页去搜一个只有 2 条结果的词，
  // 会得到一张空白列表，看上去像是搜索坏了
  pageNum.value = 1
  void load()
}

function handlePageChange(): void {
  void load()
}

/** 标题可以单独为空（后端只要求两者不同时为空），列表里得有个东西可看 */
function displayTitle(item: Memo): string {
  return item.title || '无标题'
}

/** `2026-09-19 23:45:01` → `2026-09-19 23:45`，秒对备忘没有意义 */
function shortTime(value: string): string {
  return value.slice(0, 16)
}

// ---------- 表单 ----------

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const detailLoading = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<MemoParams>({ title: '', content: '' })

const rules: FormRules<MemoParams> = {
  title: [{ max: 100, message: '标题不能超过 100 个字', trigger: 'blur' }],
  content: [
    { max: 15000, message: '正文不能超过 15000 个字', trigger: 'blur' },
    {
      // 后端也拦这一条（MemoServiceImpl#apply）。这里再写一遍不是为了安全 ——
      // 而是为了让用户当场看到红字，而不是填完点保存、等一个来回才被 toast 告知
      validator: (_rule, _value, callback) => {
        if (!form.title.trim() && !form.content.trim()) {
          callback(new Error('标题和正文不能都为空'))
        }
        else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function openCreate(): Promise<void> {
  editingId.value = null
  form.title = ''
  form.content = ''
  dialogVisible.value = true
  // 弹窗用了 destroy-on-close，表单是刚建出来的，校验态本来就是空的；
  // 这里留着是为了防止将来去掉 destroy-on-close 后残留上一次的红字
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(item: Memo): Promise<void> {
  editingId.value = item.id
  form.title = ''
  form.content = ''
  dialogVisible.value = true

  // 列表里没有正文，得先取回来才能编辑。这一步没拿到就什么都不该让用户改
  detailLoading.value = true
  try {
    const detail = await memoApi.detail(item.id)
    form.title = detail.title
    form.content = detail.content
  }
  catch {
    // 多半是这条刚被删掉了。留在弹窗里让用户对着一个空表单编辑，
    // 保存时必然又是一个 404
    dialogVisible.value = false
    editingId.value = null
    return
  }
  finally {
    detailLoading.value = false
  }

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

  // 先取出来再判空：直接读 editingId.value 的话 TS 收窄不到那个范围，
  // 到 update 那行就得靠 as number 硬掰，而硬掰是会骗人的
  const id = editingId.value
  const isCreate = id === null

  submitting.value = true
  try {
    // 展开传一份普通对象：直接把 reactive 交给 axios 也能跑，
    // 但序列化时拿到的是 Proxy，出问题时不好看
    if (id === null) {
      await memoApi.create({ ...form })
    }
    else {
      await memoApi.update(id, { ...form })
    }
    dialogVisible.value = false
    ElMessage.success(isCreate ? '已保存' : '已更新')

    // 新建的按 updateTime 倒序排在最前，所以新建后要回第一页才看得见它。
    // 编辑则留在当前页 —— 用户正看着这一页，把他弹走是帮倒忙
    if (isCreate) {
      pageNum.value = 1
    }
    await load()
  }
  finally {
    submitting.value = false
  }
}

async function remove(item: Memo): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除「${displayTitle(item)}」吗？`,
      '删除备忘',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  }
  catch {
    // 用户点了取消。ElMessageBox 取消时是 reject，不接住会冒成未处理的异常
    return
  }

  await memoApi.remove(item.id)
  ElMessage.success('已删除')

  // 删掉的是当前页最后一条时往前退一页。不退的话会停在一张空列表上，
  // 而上面的总数又明明不为 0 —— 看上去像数据丢了
  if (list.value.length === 1 && pageNum.value > 1) {
    pageNum.value -= 1
  }
  await load()
}

/**
 * 行内点击打开编辑。
 *
 * 判一下点的是不是操作按钮，否则点"删除"会连着把编辑框也弹出来。
 * （用 @click.stop 也行，但那要求每个内层元素都记得加，漏一个就出问题；
 * 从事件源头判只写一处。）
 *
 * 这里**不能**写成 `event.target !== event.currentTarget`：那等于要求用户
 * 点在这行的内边距上才算数 —— 点在标题文字上时 target 是那个 span，
 * 于是最常见的点击位置反而没反应。closest 判的是"点在不在按钮里"，
 * 正是想要的那个问题。
 */
function openFromRow(item: Memo, event: MouseEvent | KeyboardEvent): void {
  if ((event.target as HTMLElement).closest('.memo-actions')) {
    return
  }
  void openEdit(item)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1 class="title">
          备忘录
        </h1>
        <p class="subtitle">
          随手记下的碎片，回头搜得到。
        </p>
      </div>

      <el-button
        type="primary"
        :icon="Plus"
        @click="openCreate"
      >
        新建
      </el-button>
    </header>

    <section class="wb-card panel">
      <div class="search-row">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索标题或正文"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button @click="handleSearch">
          搜索
        </el-button>
      </div>

      <div
        v-loading="loading"
        class="list-wrap"
      >
        <ul
          v-if="list.length"
          class="memo-list"
        >
          <li
            v-for="item in list"
            :key="item.id"
            class="memo-item"
            role="button"
            tabindex="0"
            @click="openFromRow(item, $event)"
            @keydown.enter="openFromRow(item, $event)"
          >
            <div class="memo-main">
              <span class="memo-title">{{ displayTitle(item) }}</span>
              <span
                v-if="item.summary"
                class="memo-summary"
              >{{ item.summary }}</span>
            </div>

            <span class="memo-time">{{ shortTime(item.updateTime) }}</span>

            <span class="memo-actions">
              <el-button
                link
                :icon="Delete"
                title="删除"
                @click="remove(item)"
              />
            </span>
          </li>
        </ul>

        <p
          v-else-if="!loading"
          class="empty"
        >
          {{ keyword.trim() ? '没有匹配的备忘' : '还没有备忘，点右上角「新建」写一条吧' }}
        </p>
      </div>

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

    <!-- ========== 新建 / 编辑 ========== -->
    <!-- destroy-on-close：关闭即销毁表单，下次打开不会残留上一次的校验红字 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建备忘' : '编辑备忘'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        v-loading="detailLoading"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item
          label="标题"
          prop="title"
        >
          <el-input
            v-model="form.title"
            maxlength="100"
            placeholder="选填"
          />
        </el-form-item>

        <el-form-item
          label="正文"
          prop="content"
        >
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            maxlength="15000"
            show-word-limit
            placeholder="标题和正文至少写一个"
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
  padding: 16px 20px 20px;
}

/* ---------- 搜索 ---------- */
.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

/* el-input 默认宽度 100%，放在 flex 行里会把搜索按钮挤出去。
   让它自己伸缩、按钮保持原宽 */
.search-input {
  flex: 1;
  min-width: 0;
}

/* ---------- 列表 ---------- */
/* loading 遮罩要有高度可罩，否则加载中整块会塌成 0 高，页面跟着跳一下 */
.list-wrap {
  min-height: 80px;
}

.memo-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.memo-item {
  display: flex;
  gap: 12px;
  align-items: baseline;
  min-height: 44px;
  padding: 9px 8px;
  cursor: pointer;
  border-radius: var(--wb-radius-sm);
  transition: background-color 0.12s ease;
}

.memo-item:hover,
.memo-item:focus-visible {
  background-color: var(--wb-surface-hover);
}

.memo-main {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: baseline;
  min-width: 0;
}

.memo-title {
  flex-shrink: 0;
  max-width: 40%;
  overflow: hidden;
  font-size: var(--wb-text-base);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.memo-summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.memo-time {
  flex-shrink: 0;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-faint);
}

/* 与生日纪念日页同一套：平时收起来，悬停或键盘聚焦时才出现 */
.memo-actions {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.12s ease;
}

.memo-item:hover .memo-actions,
.memo-item:focus-within .memo-actions {
  opacity: 1;
}

/* ---------- 分页 ---------- */
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
</style>
