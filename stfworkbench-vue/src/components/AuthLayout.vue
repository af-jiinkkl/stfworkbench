<script setup lang="ts">
/**
 * 登录 / 注册页共用的外壳：全屏居中的一张白卡片 + 品牌头。
 *
 * 抽出来是因为两个页面除了标题和表单内容，别的一模一样 ——
 * 复制一遍的话，以后调间距要记得改两个地方。
 *
 * 刻意**不用** el-card：它自带投影和头部样式，而这套风格靠 1px 细线
 * 和留白做层次，投影是多余的，不如直接用 .wb-card 自己排。
 */
defineProps<{
  /** 卡片主标题，同时也是页面的 h1 */
  title: string
}>()
</script>

<template>
  <div class="auth-page">
    <div class="wb-card auth-panel">
      <div class="auth-brand">
        <span class="brand-mark">S</span>
        <span class="brand-name">stfworkbench</span>
      </div>

      <h1 class="auth-title">
        {{ title }}
      </h1>

      <slot />

      <div class="auth-footer">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 24px;
  background-color: var(--wb-bg);
}

.auth-panel {
  width: 100%;
  max-width: 396px;
  padding: 32px 28px 24px;
}

.auth-brand {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  font-size: var(--wb-text-sm);
  font-weight: 600;
  color: #fff;
  background-color: var(--wb-primary);
  border-radius: var(--wb-radius-sm);
}

.brand-name {
  font-size: var(--wb-text-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.auth-title {
  margin: 20px 0 26px;
  font-size: var(--wb-text-xl);
  font-weight: 600;
  color: var(--wb-text);
  text-align: center;
  letter-spacing: -0.01em;
}

.auth-footer {
  margin-top: 20px;
  font-size: var(--wb-text-sm);
  color: var(--wb-text-muted);
  text-align: center;
}

/* 页脚里的"去注册 / 去登录"链接。用 :deep 是因为它们由各个页面
   通过插槽传进来，不带本组件的 scoped 属性。 */
.auth-footer :deep(a) {
  font-weight: 500;
  color: var(--wb-text);
}

.auth-footer :deep(a:hover) {
  text-decoration: underline;
}
</style>
