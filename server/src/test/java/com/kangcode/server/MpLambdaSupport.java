package com.kangcode.server;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯 Mockito 单元测试的 MyBatis-Plus 支持类。
 *
 * <p><b>为什么需要它？</b>
 * <p>{@code new LambdaQueryWrapper<TrainOrder>().eq(TrainOrder::getStatus, 0)} 这种写法，
 * MyBatis-Plus 要在构造时就"解析方法引用 → 得到列名"，而这个解析依赖实体的元数据
 * （{@code TableInfo}）。 正常启动 Spring 时由 MyBatis 自动初始化；但在纯单元测试里
 * 没有 Spring，元数据是空的，于是抛：
 *
 * <pre>MybatisPlusException: can not find lambda cache for this entity [...]</pre>
 *
 * <p>所以凡是测试里会 new 出 Lambda Wrapper 的类，在 {@code @BeforeAll} 里调一次
 * {@link #init(Class[])} 即可。
 *
 * <p><b>⚠️ 这个坑很隐蔽</b>：它依赖测试的执行顺序——如果同一个 JVM 里先跑了别的
 * 启动过 Spring 的测试类（如 {@code @SpringBootTest}），元数据就被顺带初始化了，
 * 后面的测试"看起来能过"。<b>单独跑那一个类就会挂</b>。 别靠运气。
 */
final class MpLambdaSupport {

    private MpLambdaSupport() {
    }

    /** 初始化指定实体的 MyBatis-Plus 元数据（可传多个） */
    static void init(Class<?>... entities) {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }
}
