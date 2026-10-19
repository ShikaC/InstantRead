# Markdown 兼容语料

这份样例用于 R4.1 的手工核对，覆盖即阅声明支持的常见 Markdown 语法。自动化回归见
`MarkdownPreviewRendererTest`；本文件只用于真实渲染观感核对。

## 标题层级

### 三级标题

#### 四级标题

##### 五级标题

###### 六级标题

## 文本样式

普通段落，**粗体**、*斜体*、~~删除线~~、`行内代码` 与中文标点。

反斜杠转义：\*字面星号\*、\[不是链接](https://example.com)、\\ 反斜杠。

## 列表与任务

1. 有序第一项
2. 有序第二项

- 无序项目
  - 二级项目
- [x] 已完成任务
- [ ] 未完成任务

## 引用

> 第一段引用
>
> 第二段引用

## 代码块

```kotlin
fun hello(name: String): String {
    return "Hello, $name"
}
```

## 表格

| 名称 | 数量 | 状态 |
| :--- | ---: | :---: |
| 即阅 | 3 | 可阅读 |

## 链接与锚点

[返回本文开头](#markdown-兼容语料) 与 [外部站点](https://example.com)。

## 内嵌图片

![内嵌图片](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAGElEQVR4nGNk+M8AB0wMDBiYGJiAIAMAJ5oBAvzJN3kAAAAASUVORK5CYII=)

## 未支持的语法（应保留为文本或明确受限）

- 网络图片：![网络图片](https://example.com/remote.png)
- 相对文档跳转：[相对文档](notes/guide.md)
- 公式：$E = mc^2$
- 脚注：[^1]
- Mermaid：```mermaid graph TD; A-->B; ```
