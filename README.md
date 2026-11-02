# 网络阅读器 NovelReader

一个直接读 NAS 里小说的 Android 阅读器。文件留在服务器上，书架、进度、排版设置都存在手机本地。

SMB / WebDAV 双协议，TXT、EPUB、PDF、Office 文档都能打开，翻页和滚动两种阅读方式共用同一个阅读位置。

## 功能

**连接**
- SMB（jcifs-ng）与 WebDAV（自研 HTTP/1.1 客户端，支持 Basic 认证、自签名证书、Range 请求）
- 局域网 mDNS 自动发现服务，也可手填地址
- 明文 HTTP 与自签名 HTTPS 均可使用

**书架**
- 读过的书自动收录，不需要手动导入
- 按最近阅读 / 书名 / 阅读进度排序，支持置顶
- 长按进入批量模式，可一次移出、置顶多本
- 竖版书封按格式分色，进度直接压在封面下沿

**阅读**
- 格式：TXT、EPUB、PDF、DOCX/XLSX/PPTX、HTML、RTF
- 左右翻页与上下滚动两种模式，切换时位置精确衔接；滚动模式可连续跨章
- 字号、行距、字体（宋体 / 黑体 / 等宽）、字重、段间距可调
- 纸白 / 米黄 / 护眼绿 / 夜间四种背景
- 目录自动识别「第 N 章 / 回 / 卷」「Chapter N」、EPUB spine、Markdown 标题
- 进度以全书字符偏移为锚点（PDF 用页号），换字号、换模式、重开 App 都能回到原处

## 下载

正式版 APK 见 [Releases](../../releases) 页，下载 `*.apk` 直接安装即可（Android 8.0 及以上）。

## 构建

环境：JDK 17、Android SDK 35、Gradle 8.9。

```bash
# local.properties 里写上 sdk.dir=<你的 Android SDK 路径>
gradle :app:assembleRelease
```

签名信息从根目录的 `keystore.properties` 读取（该文件与 `keystore/` 不入库）。没有这个文件时 release 包不签名，debug 包可直接 `gradle :app:assembleDebug` 构建，包名带 `.debug` 后缀，与正式版共存。

`app/libs/guava-33.0.0-android.jar` 是本地依赖，用来在离线环境完成构建。

## 代码结构

```
app/src/main/java/com/dyx/novelreader/
├── data/
│   ├── books/      文档解析：TXT 编码识别、EPUB、OOXML、HTML、章节切分
│   ├── model/      数据模型（Book / RemoteServer / ReaderSettings …）
│   ├── PrefsStore.kt   DataStore 持久化
│   ├── SmbSource.kt    SMB 数据源
│   └── WebDav*.kt      WebDAV 客户端与数据源
├── ui/
│   ├── shelf/      书架（卡片、批量操作、排序）
│   ├── browser/    服务器与目录浏览
│   ├── reader/     阅读页（分页、滚动、设置面板、目录）
│   └── common/     通用组件
└── viewmodel/      ShelfViewModel / BrowserViewModel / ReaderViewModel
```

## 说明

本项目为个人自用工具，未做多设备同步与云端备份。阅读进度只存在本机，卸载 App 会一并清除。
