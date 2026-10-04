<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as newsApi from '@/api/newsApi'
import { timeText } from '@/types/news'
import type { News } from '@/types/news'

/**
 * 每日新闻。对应 docs/接口清单.md §8。
 *
 * 这是全站唯一一个**只读**页面：数据由后端的定时任务抓取，
 * 所以没有新建 / 编辑 / 删除按钮，也没有分页（一天就几十条）。
 * 页面上那句"每小时自动更新一次"是实话也是全部说明 ——
 * 刻意**不做"立即刷新"按钮**：抓取打的是按次计费的第三方接口，
 * 放到请求路径上几十个用户就能把当天配额耗光（§8 定的架构决定）。
 *
 * 首页那张卡片和这一页读的是**同一个接口**（首页走 `/api/dashboard` 聚合，
 * 后端两处调的是同一个 Service 方法），所以两边的条数和顺序不会分叉。
 */

const loading = ref(false)
const list = ref<News[]>([])

async function load(): Promise<void> {
  loading.value = true
  try {
    list.value = await newsApi.listToday()
  }
  catch {
    // request.ts 的拦截器已经弹过错误提示了，这里再弹一次是重复的。
    // 接住是为了不让它冒成一条来源不明的 unhandled rejection（见 CLAUDE.md）
  }
  finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="wb-page">
    <header class="wb-page-header">
      <div>
        <h1 class="wb-title">
          每日新闻
        </h1>
        <p class="wb-subtitle">
          每天值得一读的几条，每小时自动更新一次。
        </p>
      </div>
    </header>

    <section class="wb-card panel">
      <!-- 遮罩要有高度可罩，否则加载中整块会塌成 0 高，页面跟着跳一下 -->
      <div
        v-loading="loading"
        class="list-wrap"
      >
        <ul
          v-if="list.length"
          class="news-list"
        >
          <li
            v-for="item in list"
            :key="item.id"
            class="news-item"
          >
            <!-- 第三方偶尔不给 url，那种记录仍然留着但不可点。
                 不做成一个空 href 的链接 —— 空 href 指向当前页，
                 点一下像是页面刷新了，比不可点更让人困惑 -->
            <a
              v-if="item.url"
              class="news-title"
              :href="item.url"
              target="_blank"
              rel="noopener noreferrer"
            >{{ item.title }}</a>
            <span
              v-else
              class="news-title is-plain"
            >{{ item.title }}</span>

            <span class="news-meta">
              <span v-if="item.source">{{ item.source }}</span>
              <!-- publishTime 可能为 null（第三方的时间解析不了），
                   那种记录排在最后，这里就只显示来源 -->
              <span v-if="item.publishTime">{{ timeText(item.publishTime) }}</span>
            </span>
          </li>
        </ul>

        <p
          v-else-if="!loading"
          class="empty"
        >
          今天还没有新闻。
          <span class="empty-hint">
            抓取需要一个聚合数据的 appkey（环境变量 <code>JUHE_NEWS_KEY</code>），
            配置好后每小时自动抓一次。
          </span>
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 页面外壳（.wb-page / .wb-title / .wb-subtitle / .wb-page-header）已提到
   styles/index.css，那里也是窄屏 padding 的唯一一处实现。此处不再重复。 */

.panel {
  padding: 12px 20px 20px;
}

.list-wrap {
  min-height: 80px;
}

.news-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.news-item {
  display: flex;
  gap: 12px;
  align-items: baseline;
  min-height: 40px;
  padding: 9px 0;
  border-bottom: 1px solid var(--wb-border);
}

.news-item:last-child {
  border-bottom: none;
}

/* 标题占满剩余宽度、超长省略：这是这一行里唯一需要读的东西，
   来源和时间是可选的补充信息，不该跟它抢宽度 */
.news-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--wb-text-base);
  color: var(--wb-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

a.news-title:hover {
  color: var(--wb-text-secondary);
  text-decoration: underline;
}

/* 没有链接的那几条：不是链接就不要摆出链接的样子 */
.news-title.is-plain {
  color: var(--wb-text-secondary);
}

.news-meta {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: baseline;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-muted);
}

.empty {
  padding: 32px 0;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
}

/* 补一句"为什么是空的"。空列表最容易被当成功能坏了，
   而这里确实有一个外部前提（appkey）—— 不写出来，
   将来自己回头看也会先怀疑是不是抓取任务挂了 */
.empty-hint {
  display: block;
  margin-top: 8px;
  font-size: var(--wb-text-xs);
  color: var(--wb-text-faint);
}

.empty-hint code {
  font-size: inherit;
}

/* ---------- 窄屏 ---------- */
@media (max-width: 768px) {
  /* 标题 + 来源 + 时间挤一行时，标题只剩下几个字的宽度（它是这一行里
     唯一需要读的东西）。窄屏让来源和时间整行落到第二行。 */
  .news-item {
    flex-wrap: wrap;
    row-gap: 2px;
  }

  .news-meta {
    flex-basis: 100%;
  }
}
</style>
