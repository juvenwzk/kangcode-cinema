# Java 补漏 · 03 JVM

> 这一篇解释"你的代码运行时到底发生了什么"。
> 不用背所有参数，重点是：**出问题时你知道去哪个区域找、用哪个工具看。**

---

## 第 1 章　内存结构：你的对象住在哪

### 1.1 先看你的应用启动命令

```bash
java -jar server-1.0.0-SNAPSHOT.jar --server.port=8080
```

**没加任何 `-Xmx` 参数。** JVM 会按物理内存的 1/4 给堆设上限
（比如 16G 的机器，最大堆约 4G）。本地开发够用，但要知道这件事存在。

### 1.2 五个区域

```
┌─────────────────────────────────────────────────────┐
│  线程共享                                             │
│  ┌──────────────┐  ┌──────────────────────────────┐  │
│  │     堆 Heap   │  │  元空间 Metaspace（本地内存）  │  │
│  │  对象、数组    │  │  类元信息、常量、静态变量       │  │
│  └──────────────┘  └──────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────┐
│  线程私有（每个线程一份）                              │
│  虚拟机栈（局部变量表、操作数栈）                       │
│  本地方法栈 / 程序计数器                                │
└─────────────────────────────────────────────────────┘
```

| 区域 | 存什么 | 会不会 OOM |
| --- | --- | --- |
| **堆** | `new` 出来的对象、数组 | 会（`Java heap space`） |
| **元空间** | 类结构、方法字节码、常量池 | 会（`Metaspace`，JDK 8+ 在本地内存，不限默认上限） |
| **虚拟机栈** | 局部变量、方法调用栈 | 会（`StackOverflowError` 或 `unable to create new native thread`） |
| **程序计数器** | 当前执行到哪条字节码 | 不会 |

> **JDK 8 的变化**：永久代（PermGen）被移除，改成**元空间**，从堆里搬到了本地内存。
> 这就是为什么现在很少见 `PermGen space` 的 OOM 了。

### 1.3 你项目里能具体对应上什么

| 你的代码 | 在哪 |
| --- | --- |
| `new TrainOrder()`、`orders` 列表 | 堆 |
| `private static final int STATUS_PAID = 1` | 元空间（类信息里的静态变量） |
| `UserContext` 里的 `ThreadLocal` 值 | **堆**（对象在堆上，只是被 Thread 引用） |
| 方法里的 `int rows`、循环变量 | 虚拟机栈（局部变量表） |

> 注意一个常见误解：**"ThreadLocal 的值在线程栈上"是错的**，值在堆里，栈里只是引用。
> 这也解释了为什么 ThreadLocal 会内存泄漏 —— 堆上的对象被 Thread 强引用着。

### 1.4 自测

1. JDK 8 之后字符串常量池在哪？（提示：JDK 7 就搬到堆里了）
2. 为什么 ThreadLocal 的值在堆上，而不是栈上？
3. 元空间满了会报什么错？和堆满了有什么区别？

---

## 第 2 章　一个对象是怎么被创建出来的

### 2.1 五步

```java
TrainOrder order = new TrainOrder();
```

| 步骤 | 做什么 |
| --- | --- |
| ① 类加载检查 | 常量池里有没有 `TrainOrder` 的符号引用？类加载过没有？没有就先加载 |
| ② 分配内存 | 堆里划分一块空间（**指针碰撞** 或 **空闲列表**） |
| ③ 内存置零 | 保证实例字段有默认值（0 / null / false） |
| ④ 设置对象头 | 存 Mark Word（hash、锁状态、GC 年龄）+ 类型指针 |
| ⑤ 执行 `<init>` | 构造器，你写的赋值在这里执行 |

**第 ③ 步解释了一个现象**：

```java
TrainOrder o = new TrainOrder();
System.out.println(o.getStatus());   // 0 —— 不是 null，因为 int 默认值就是 0
```

这就是为什么你的 `status` 字段没赋值时是 `0`（待支付），而不是"未设置"。

### 2.2 分配内存怎么避免竞争

多线程同时 `new` 对象，怎么保证不撞车？两种办法：

| 办法 | 说明 |
| --- | --- |
| CAS + 失败重试 | 直接抢指针 |
| **TLAB**（Thread Local Allocation Buffer） | **每个线程在 Eden 里预分一小块私有空间**，自己分配不用加锁，用完了再申请新的 |

**TLAB 是默认开启的**，所以绝大多数 `new` 是无锁的。

### 2.3 对象在内存里长什么样

```
┌────────────┬──────────────┬────────────┬──────────┐
│  Mark Word │ 类型指针      │  实例数据   │ 对齐填充  │
│  (8 字节)   │ (4/8 字节)   │ 字段         │ 补齐 8 的倍数│
└────────────┴──────────────┴────────────┴──────────┘
```

**Mark Word 就是锁升级发生的地方**（上一篇第 5 章讲过）：
它同时存 hashCode、GC 分代年龄、锁标志位、偏向线程 ID —— 这些信息是**复用同一块内存**的。

### 2.4 逃逸分析：不是所有对象都在堆上

```java
public int calc() {
    TrainSeat s = new TrainSeat();   // 只在方法内用，没"逃逸"出去
    s.setRemain(10);
    return s.getRemain();
}
```

JIT 可能把这个对象**拆成几个标量放在栈上**（标量替换），根本不分配在堆里。
这就是"栈上分配"（实际是逃逸分析的优化结果，不是真的分配到栈帧）。

### 2.5 自测

1. `new` 一个对象的五个步骤？
2. 为什么 `int` 字段不赋值就是 0？在哪一步决定的？
3. TLAB 解决了什么问题？
4. Mark Word 里存了哪些信息？为什么能"复用"？

---

## 第 3 章　GC：什么时候会卡一下

### 3.1 分代：为什么要分

**绝大多数对象朝生夕死**（比如你每次请求里 `new` 的 DTO、VO），少数对象长期存活（缓存、连接池）。
分代让 JVM 用不同策略处理这两类：

```
堆
├── 新生代 (Young)  默认占 1/3
│    ├── Eden        8
│    ├── Survivor0   1
│    └── Survivor1   1
└── 老年代 (Old)    默认占 2/3
```

比例 `8:1:1` 的意思：每次 Minor GC 后，Eden + 一个 Survivor 里存活的对象
**复制到另一个 Survivor**（所以只需要 10% 的空间做复制，浪费少）。

### 3.2 对象什么时候进老年代

| 条件 | 说明 |
| --- | --- |
| 年龄到阈值 | 每熬过一次 Minor GC 年龄 +1，默认 **15** 岁进老年代 |
| 大对象 | 超过 `-XX:PretenureSizeThreshold` 直接进老年代 |
| 动态年龄判断 | Survivor 里同龄对象总和超过一半，这批直接晋升 |
| Survivor 放不下 | 担保机制，直接进老年代 |

### 3.3 Minor GC / Major GC / Full GC

| 类型 | 范围 | 停顿 |
| --- | --- | --- |
| Minor / Young GC | 只回收新生代 | 短（毫秒级） |
| Major / Old GC | 回收老年代 | 长 |
| **Full GC** | 整个堆 + 元空间 | **最长，通常几百 ms 到几秒** |

**Full GC 什么时候触发**（面试常问）：

1. 老年代空间不足（晋升失败 / 大对象）
2. 元空间不足
3. **空间分配担保失败**（Minor GC 前发现老年代放不下）
4. 显式调用 `System.gc()`（建议靠 `-XX:+DisableExplicitGC` 禁掉）

### 3.4 收集器：现在默认用什么

| 收集器 | 特点 | 适用 |
| --- | --- | --- |
| Serial | 单线程，简单 | 客户端、小内存 |
| Parallel | 多线程，吞吐优先 | JDK 8 默认 |
| CMS | 并发标记清除，低停顿 | JDK 9 废弃，JDK 14 移除 |
| **G1** | **分区（Region）+ 可预测停顿** | **JDK 9+ 默认** |
| ZGC | 超低停顿（<1ms） | 大堆、低延迟要求 |

**G1 的核心**：把堆分成很多等大的 **Region**（不再物理分代），
每次只回收"垃圾最多"的那批 Region（叫 Mixed GC），并尽量控制在 `-XX:MaxGCPauseMillis` 内。

> 你项目如果用 JDK 17 跑，**默认就是 G1**。面试说"我们用的 G1"比说"我不清楚"好。

### 3.5 看一次 GC 日志

```bash
java -Xms512m -Xmx512m -Xlog:gc*:file=gc.log -jar server-1.0.0-SNAPSHOT.jar
```

日志里关注三个数：

| 指标 | 含义 |
| --- | --- |
| `Pause Young` 的频率和耗时 | 频繁且耗时长 → 新生代太小或分配太快 |
| `Pause Full` 有没有出现 | 出现就说明有晋升失败或内存泄漏 |
| 回收后老年代占用 | **回收后仍然很高 → 有对象一直被引用（泄漏）** |

### 3.6 自测

1. 为什么新生代用复制算法，老年代用标记-整理？
2. 对象什么时候进老年代？（四个条件）
3. Full GC 的四个触发条件？
4. G1 相比 CMS 的核心改进是什么？
5. 怎么从 GC 日志判断"是内存泄漏还是内存不够"？

---

## 第 4 章　类加载：你的 `@Service` 是怎么变成对象的

### 4.1 五个阶段

```
加载 → 验证 → 准备 → 解析 → 初始化
```

| 阶段 | 做什么 | 注意 |
| --- | --- | --- |
| 加载 | 读 `.class` 文件，生成 `Class` 对象 | |
| 验证 | 校验字节码合法性 | |
| **准备** | 给静态变量分配内存并赋**零值** | `static int a = 10` 在这一步是 **0** |
| 解析 | 符号引用 → 直接引用 | |
| **初始化** | 执行 `<clinit>`（静态代码块 + 静态赋值） | 这里才变成 10 |

**"准备阶段赋零值"是个高频陷阱题**：

```java
static int a = 10;              // 准备阶段 a=0，初始化阶段 a=10
static final int B = 20;        // 编译期常量，准备阶段直接就是 20
```

### 4.2 双亲委派

```
Custom ClassLoader
      ↓ 先问
Application ClassLoader（你的 jar、classpath）
      ↓ 先问
Platform ClassLoader（JDK 模块）
      ↓ 先问
Bootstrap ClassLoader（rt.jar / java.base）
      ↓ 都没有，才自己加载
```

**好处**：① 同一个类不会被重复加载 ② 防止你写个 `java.lang.String` 把 JDK 的替换掉。

### 4.3 什么时候会"打破"双亲委派

| 场景 | 怎么打破 | 为什么 |
| --- | --- | --- |
| **JDBC** | 用 `Thread.currentThread().getContextClassLoader()` | DriverManager 在 `java.base` 里，加载不到你 classpath 下的 MySQL 驱动 |
| **Tomcat** | 每个 webapp 一个 ClassLoader | 两个应用依赖不同版本的同一个库，要隔离 |
| 热部署 / OSGi | 自定义加载顺序 | 支持替换类 |

> 你项目里 MySQL 驱动就是 `runtime` 依赖，**JDBC SPI 的加载就依赖打破双亲委派**。
> 这个点讲出来，说明你不是只背了"双亲委派"四个字。

### 4.4 自测

1. `static int a = 10` 在准备阶段是多少？为什么？
2. `static final int B = 20` 和上面有什么区别？
3. 双亲委派的两个好处？
4. JDBC 为什么要打破双亲委派？

---

## 第 5 章　排查工具：出问题时你敲什么命令

### 5.1 五个必备命令

| 命令 | 用途 | 一句话 |
| --- | --- | --- |
| `jps -l` | 列出 Java 进程 | 先拿到 PID |
| `jstack <pid>` | 打印线程栈 | **看死锁、看卡在哪** |
| `jmap -histo:live <pid>` | 堆里各类对象数量 | 哪个类的对象最多 |
| `jmap -dump:format=b,file=heap.hprof <pid>` | 导出堆快照 | 用 MAT 分析泄漏 |
| `jstat -gcutil <pid> 1000` | 每秒打印 GC 统计 | 看 GC 频率和耗时 |

### 5.2 场景一：CPU 100%，怎么定位

```bash
# 1. 找到占 CPU 的线程（top -Hp 是按线程看）
top -Hp <pid>
#    假设最热的线程 tid = 12345

# 2. 转成 16 进制（jstack 里是 16 进制）
printf "%x\n" 12345     # → 3039

# 3. 在 jstack 输出里搜这个 nid
jstack <pid> | grep -A 30 "nid=0x3039"
```

**Windows 下用 Arthas 更简单**：

```bash
arthas-boot.jar
> thread -n 3        # 直接列出最忙的 3 个线程和它们的栈
```

### 5.3 场景二：内存一直涨，是不是泄漏

```bash
# 连续看几次 GC 后的老年代占用
jstat -gcutil <pid> 1000

# 关注 OU（老年代使用率）：
#   每次 Full GC 后 OU 都下降 → 正常
#   每次 Full GC 后 OU 依然很高 → 有对象被一直引用，疑似泄漏
```

然后 dump 下来分析：

```bash
jmap -dump:live,format=b,file=/tmp/heap.hprof <pid>
```

用 Eclipse MAT 或 JProfiler 打开，看 **Dominator Tree**（谁占的内存最多）。

**你项目里最可能泄漏的地方**（提前想好答案）：
- `ThreadLocal` 没 remove（你已经处理了）
- 静态 `Map` 只增不减（比如自己写的本地缓存）
- MQ 消息体被大量持有

### 5.4 场景三：OOM 的五种类型

| 报错 | 原因 | 去哪看 |
| --- | --- | --- |
| `Java heap space` | 对象太多/太大，堆不够 | 堆 dump |
| `Metaspace` | 动态生成类太多（CGLIB、反射） | 加 `-XX:MaxMetaspaceSize` |
| `GC overhead limit exceeded` | GC 花了 98% 时间只回收 2% 空间 | 基本等于堆泄漏 |
| `unable to create new native thread` | 线程数达上限 | 查线程数、`ulimit` |
| `Direct buffer memory` | 堆外内存（NIO）没释放 | 查 Netty/文件流 |

### 5.5 你项目里已经有的"半个监控"

你接入了 Actuator，可以拿到 JVM 指标：

```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
curl http://localhost:8080/actuator/metrics/jvm.threads.live
```

**面试时这句话很好用**：
> "我把 CPU、堆、GC 次数、活跃线程数都通过 Micrometer 暴露出来了，接 Prometheus + Grafana 就能看趋势。"

### 5.6 自测

1. CPU 100% 的排查步骤（四步）？
2. 怎么从 `jstat` 判断是"内存泄漏"还是"内存不够"？
3. `unable to create new native thread` 一般是什么原因？
4. 你项目里有哪几处可能发生 ThreadLocal 泄漏？

---

## 第 6 章　本章自测汇总

| # | 题 | 会 / 不会 |
| --- | --- | --- |
| 1 | 堆、元空间、栈分别存什么？各会报什么错？ | ☐ |
| 2 | `new` 对象的五个步骤？零值在哪一步？ | ☐ |
| 3 | TLAB 是什么？为什么默认开启？ | ☐ |
| 4 | 对象进老年代的四个条件？ | ☐ |
| 5 | Full GC 的四个触发条件？ | ☐ |
| 6 | G1 相比 CMS 的核心改进？ | ☐ |
| 7 | 从 GC 日志怎么判断内存泄漏？ | ☐ |
| 8 | 准备阶段 `static int a = 10` 是多少？ | ☐ |
| 9 | 双亲委派的好处？JDBC 为什么要打破它？ | ☐ |
| 10 | CPU 100% 的四步排查？ | ☐ |

---

## 附：三个能直接用在面试里的"项目话术"

| 场景 | 怎么说 |
| --- | --- |
| 问 JVM 调优 | "我本地用的是 JDK 17 默认的 G1。这个项目 QPS 不高，所以没做参数调优，但我知道要看 GC 频率和 Full GC 次数。我通过 Actuator 暴露了 `jvm.gc.pause` 指标。" |
| 问内存泄漏 | "我在拦截器的 `afterCompletion` 里清了 ThreadLocal 和 MDC。因为 Tomcat 线程是复用的，不清会串号，而且 Thread 强引用着 Map，线程不死就不释放。" |
| 问线上排查 | "如果 CPU 打满，我会先 `top -Hp` 找线程，转 16 进制，再用 `jstack` 搜 nid 定位栈；Windows 环境下用 Arthas 的 `thread -n 3` 更直接。" |

> **不要装懂**。不会就说"这块我只了解原理，没有实战调优经验" —— 比瞎编强。
