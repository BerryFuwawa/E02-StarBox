# GitHub 发布维护

2026-10-06。用户已明确批准“上传github，同时更新说明文档与使用教程”。

仓库已从 Galaxy-E02-Starbox 改名为 **E02-StarBox**，保留原提交、Issue 和 Release；origin 已切换为 `https://github.com/BerryFuwawa/E02-StarBox.git`。旧版本链接保留兼容。

本次发布为 v1.7.0 / versionCode 10。APK、SHA256、源码 ZIP、公开文档、真实截图及清单来自同批成果；签名与车机既有版本一致。安装包 SHA256：`1fd6a4dc7abf80494ed4361010fd6213e20a7848fe1c69c759dd37706d83dede`。

发布顺序：先提交源码、教程和截图，创建 v1.7.0 标签；草稿上传资产并核对哈希与大小，发布后再提交 `update/stable.json`。安装包可下载前不启用新清单。

当前结果由本次最终发布记录补充；完整用户入口为 README、使用指南和版本验证范围。历史开发记录保留作为证据，不替代最新验证结论。

以后每次 GitHub 修改仍须单独取得用户批准；本次授权不自动延伸到下一版本。不提交实际 Token、穿透密钥、签名私钥、原始聊天、设备配置或有效配对码。GitHub CLI 认证不通过第三方 CDN。

后续版本必须递增 versionCode；本次已安装 code10 的本地测试用户需要手动覆盖。默认 build.py 使用开发签名，不能覆盖原发布签名安装；维护者需传入既有密钥和密码环境变量。
