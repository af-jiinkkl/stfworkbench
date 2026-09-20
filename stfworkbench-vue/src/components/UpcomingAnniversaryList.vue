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

.soon-name {
  flex-shrink: 0;
  font-size: var(--wb-text-base);
  font-weight: 600;
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
</style>
