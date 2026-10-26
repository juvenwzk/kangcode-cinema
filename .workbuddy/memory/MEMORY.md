# 项目长期记忆 · 康轨 KangRail

## 文档写作约定（用户明确要求过）

- **先讲"为什么"，再给代码。** 用户明确说过"你这个也太草率了吧，我都看不懂为什么要这么做"。
- 禁止操作手册式写法（直接甩表 + 代码块）。必须：
  1. 先用**一个具体故障故事/时间线**让读者看到数字变错
  2. 再解释机制（为什么会这样）
  3. 再讲**错误做法为什么不可行**（含并发/边界）
  4. 最后才给代码，并逐段说明"这行不写会怎样"
- 面向"学习者"而不是"执行者"：术语首次出现要展开（如 at-least-once、TOCTOU、outbox）。
- 每章保持小步推进，允许重复关键结论（幂等、事务边界）。

## 协作约定

- **涉及"个人材料"（简历、自我介绍、求职信等）必须先问清基本信息，不要用占位符交付。**
  用户原话："你怎么没有问你相关信息就制作了"。这类内容（有无实习、学校层次、求职方向）
  直接决定怎么写，占位符等于把活推回给用户。
- 只有**纯技术产出**（文档、代码、报告）才适合先做再问；个人信息类一律先问。

## 技术约定

- 统一 JDK 17 编译（`C:\Users\王照康\.jdks\jdk-17.0.2`）；构建用 JDK 21 + Maven 3.9.16 也可。
- 纯单测命名 `P*Test`（不需要中间件）；集成测试命名 `P7*IT` 之类（需要真 MySQL/Redis/MQ）。
- `@MapperScan("com.kangcode.**.mapper")` 已存在，新 Mapper 不用加 `@Mapper`。
- `scripts/db-query.ps1` 用 `executeQuery`，**不能执行 DDL/UPDATE**。
- 应用启动需显式 `--server.port=8080`，否则会被分到随机端口。
- 用户可能自己在 IDEA 里启动应用占用 8080，**不要 kill 用户的实例**。
- `mvn clean` 会删掉 `target` 下的 jar，之后要补 `package`。

## 工具技巧（本机已验证可用）

- **HTML → PDF**：本机有 Chrome，用无头模式转换，效果比任何 Python 库都好（中文字体正确）：
  ```bash
  "/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --disable-gpu \
    --no-pdf-header-footer --print-to-pdf="C:\Users\Public\out.pdf" "file:///C:/Users/Public/in.html"
  ```
  **注意**：路径有中文（用户名"王照康"）时，先把 HTML 复制到 `C:\Users\Public\` 这种纯英文路径再转，
  否则无头模式可能失败；转完再 cp 回目标目录。
- 校验 PDF 页数与尺寸：python 正则读 `/Count (\d+)` 和 `/MediaBox`，A4 = 595×842pt。
- 视觉校验：`--screenshot` + `--window-size` 截图，再用 Read 看图。
