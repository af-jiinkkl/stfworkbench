<script setup lang="ts">
import { TYPE_MEMORIAL, type UpcomingAnniversary } from '@/types/anniversary'

/**
 * 即将到来的生日/纪念日列表。首页的提前提示和纪念日页面共用这一份。
 *
 * 只负责渲染 —— 数据由调用方从 `GET /api/anniversary/upcoming` 取。
 * 抽成组件是为了让"还有几天"的说法**只有一处实现**：
 * 两边各写一遍的话，改了一边忘了另一边，同一个生日在首页和列表页会显示成
 * 两个不同的天数，而这种不一致很难被人当成 bug 报上来。
 */
defineProps<{ items: UpcomingAnniversary[] }>()

/** 剩余天数说成人话。0 天要强调"就是今天"，不能显示成"0 天后" */
function countdownText(daysUntil: number): string {
  if (daysUntil === 0) {
    return '就是今天'
  }
  if (daysUntil === 1) {
    return '明天'
  }
  return `${daysUntil} 天后`
}

/** `yyyy-MM-dd` → `10 月 5 日`。nextDate 由后端算好，这里只做格式转换 */
function monthDayText(dateStr: string): string {
  const parts = dateStr.split('-').map(Number)
  return `${parts[1] ?? ''} 月 ${parts[2] ?? ''} 日`
}

function typeLabel(type: number): string {
  return type === TYPE_MEMORIAL ? '纪念日' : '生日'
}
</script>

<template>
  <ul class="soon-list">
    <li
      v-for="item in items"
      :key="item.id"
      class="soon"
      :class="{ 'is-today': item.daysUntil === 0 }"
    >
      <span class="soon-name">{{ item.name }}</span>
      <span class="soon-meta">
        {{ monthDayText(item.nextDate) }} · {{ typeLabel(item.type) }}
        <template v-if="item.relation"> · {{ item.relation }}</template>
      </span>
      <span class="soon-count">{{ countdownText(item.daysUntil) }}</span>
    </li>
  </ul>
</template>

<style scoped>
.soon-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.soon {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 10px 12px;
  border-radius: var(--wb-radius);
}

/* 今天就用浅底整行托一下 —— 不改成彩色，这套风格里彩色只留给主操作 */
.soon.is-today {
  background-color: var(--wb-primary-soft);
}

/* 姓名最长 50 个字，而这里是 flex-shrink: 0 且没有省略号 —— 名字一长，
   它会把整行顶破（窄屏上尤其明显）。允许它收缩并截断。
   为什么会轮到它而不是 `.soon-meta`：meta 的 flex-basis 是 0，
   按收缩权重算下来承担不了任何收缩量，所以超出的部分只会落在姓名上 ——
   正好是想要的那个行为，不必再给它设 max-width。 */
.soon-name {
  flex-shrink: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--wb-text-base);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.soon-meta {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.soon-count {
  flex-shrink: 0;
  font-size: var(--wb-text-base);
  font-weight: 500;
  color: var(--wb-text-secondary);
}

/* ---------- 窄屏 ---------- */
@media (max-width: 768px) {
  /* 不换行的话下面那条 flex-basis: 100% 会被挤回同一行，等于没写 */
  .soon {
    flex-wrap: wrap;
    row-gap: 2px;
  }

  /* 一行三项（姓名 / 日期·类型·关系 / 倒计时）在 390px 下，
     中间那项会被挤到只剩省略号 —— 而它恰恰是"这是什么日子"的唯一说明。
     目标排法是两行：第一行"谁 + 还有几天"，第二行日期·类型·关系。

     要凑出这个排法需要两条规则配合，缺一条都会退化成三行
     （实测两次都是这么退化的），所以两条写在一起看：

     一是姓名。它的 flex-basis 必须归零，不能留默认的 auto ——
     **换行是按 flex base size 判的，不是按收缩后的宽度**，而姓名是
     `white-space: nowrap`，base size 就是那串很长的不换行文本，一个人就超过
     整行宽度，于是倒计时必然被挤到下一行，**怎么调 order 都没用**。
     basis 归零后它不再参与"放不放得下"的判断，第一行就装得下姓名 + 倒计时，
     姓名再靠 flex-grow 吃掉剩下的宽度。

     二是顺序。DOM 顺序是 姓名 → 日期 → 倒计时，所以光给日期那项
     `flex-basis: 100%` 会把**倒计时**挤到第三行。用 order 把它提到日期前面，
     布局顺序变成 姓名 → 倒计时 → 日期，第一行才正好是"谁 + 还有几天"。
     order 只改视觉顺序、不动 DOM，读屏软件读到的仍是"姓名、日期、倒计时"。

     组件是首页与纪念日页共用的，改这一处两处都生效。 */
  .soon-name {
    flex: 1 1 0;
  }

  .soon-count {
    order: 1;
  }

  .soon-meta {
    order: 2;
    flex-basis: 100%;
  }
}
</style>
