<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts/core'
import type { EChartsCoreOption } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

/**
 * 一个薄薄的 echarts 容器，只负责**生命周期**，不碰业务。
 *
 * 抽出来是因为"初始化和销毁"这段每个图都要写一遍，而写错的方式很安静：
 * 忘了 dispose，组件卸载后实例还挂在那个已经不在文档里的 div 上；
 * 忘了 resize，窗口一变宽图就停在旧尺寸，右边空一块。
 *
 * 这里**按需注册**图表与组件（`echarts/core` 而不是 `echarts`），
 * 完整包的体积是一兆上下，这个页面只用得上饼图和折线图。
 * 代价是将来要用别的图（柱状、地图等）得回到这里补注册 ——
 * 忘了补的症状是"图整个不显示"，控制台会明说缺哪个组件，不算难查。
 */
echarts.use([
  PieChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer,
])

const props = defineProps<{
  /** 完整的 echarts option。父组件用 computed 算好后传进来 */
  option: EChartsCoreOption
}>()

const el = ref<HTMLDivElement>()

/**
 * `shallowRef` 而不是 `ref`。
 *
 * echarts 实例内部有几百个互相引用的对象。放进 `ref` 会让 Vue 递归遍历
 * 整棵树去建 Proxy —— 初始化肉眼可见地卡，而且 echarts 自己改自己内部状态时
 * 还会触发一批无谓的依赖通知。这里永远只整个替换实例，不做深层改动，
 * 浅层响应式正是想要的粒度。
 */
const chart = shallowRef<echarts.ECharts>()

let observer: ResizeObserver | undefined

onMounted(() => {
  if (!el.value) {
    return
  }

  chart.value = echarts.init(el.value)
  chart.value.setOption(props.option)

  // 用 ResizeObserver 而不是 window 的 resize 事件：这个容器变尺寸未必
  // 因为窗口变了 —— 侧边栏折叠、路由切换重排都会让它变窄，
  // 而那些时候 window 的 resize 根本不触发，图就停在旧宽度上
  observer = new ResizeObserver(() => chart.value?.resize())
  observer.observe(el.value)
})

watch(
  () => props.option,
  (next) => {
    // 第二个参数 notMerge = true：这是"数据整个换了"，不是"打个小补丁"。
    // 默认的合并模式在**系列数量变少**时会把消失的系列留在图上 ——
    // 从 6 个分类筛到 2 个，饼图仍然画着 6 块，后 4 块的数字还是旧的。
    // 这种"看到的和筛出来的对不上"很难被当成 bug 报上来
    chart.value?.setOption(next, true)
  },
)

onBeforeUnmount(() => {
  // 先 disconnect 再 dispose：反过来的话，dispose 触发的尺寸变化
  // 仍会回调进来，此时 chart 已经是一具空壳，resize 会抛错
  observer?.disconnect()
  observer = undefined
  chart.value?.dispose()
})
</script>

<template>
  <div
    ref="el"
    class="chart"
  />
</template>

<style scoped>
/* 高度必须是**具体的**。echarts 初始化时量容器高度，百分比高度在父元素
   没有确定高度时会算成 0，图就静悄悄地不出来（控制台只有一句 warning）。
   所以尺寸由外面给：用这个组件时保证父元素有高度，或直接给它定高 */
.chart {
  width: 100%;
  height: 100%;
}
</style>
