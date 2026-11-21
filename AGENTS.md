# AGENTS.md

本文件面向在本仓库中工作的自动化代理与协作者。

## 临时文件

- 临时文件、截图与日志一律写入仓库根目录的 `.tmp/`，不要散落到源码或资源目录。
- 任务结束前清理 `.tmp/` 下的产物，避免残留。
- `.tmp/` 不入版本库，不进提交。
- 需要长期保留的素材放 `docs/`，且不得混入临时/调试内容。

## Git

- 未经明确许可，禁止执行 `git commit` 与 `git push`。
- 需要提交或推送时，先说明将要提交的内容并征得同意，再执行。

## 构建与运行

- 构建 Debug APK：`./gradlew :app:assembleDebug`
- 安装到已连接设备：`./gradlew :app:installDebug`
- 命令行构建若在受限沙箱中失败，可将 Gradle 缓存指向仓库内（`GRADLE_USER_HOME=./.gradle-home`）。
