package org.example.workbenchserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务。目前只有 {@code NewsFetchJob} 一个 —— 每小时抓一次新闻缓存到库里，
 * 见 docs/接口清单.md §8。
 *
 * <p><b>为什么单独一个类，而不是把 {@code @EnableScheduling} 加到启动类上</b>：
 * 和 {@code @MapperScan} 必须留在 {@code MybatisPlusConfig} 上的理由是同一条 ——
 * 切片测试（{@code @JsonTest} / {@code @WebMvcTest}）沿包向上会把启动类当成配置类捡走。
 * 一旦启动类带了 {@code @EnableScheduling}，每条切片用例都会真的把调度器拉起来，
 * 于是测试进程里凭空多出一个按小时跑的线程去连数据库。
 * 普通 {@code @Configuration} 会被切片排除，放这里才安全。
 *
 * <p>本类**不配线程池**：只有一个任务、周期是小时级，默认的单线程调度器绰绰有余。
 * 将来任务多到互相拖累时再加 {@code TaskScheduler}，现在加是提前优化。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
