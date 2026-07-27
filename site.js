"use strict";

const sections = {
  actions: { label: "语句" }, selectors: { label: "选择器" }, triggers: { label: "触发器" },
  properties: { label: "属性" }, types: { label: "类型" }
};
const state = { registry: null, section: "actions", query: "", selectedId: null };
const tabs = document.querySelector("#sectionTabs");
const navList = document.querySelector("#navList");
const resultSummary = document.querySelector("#resultSummary");
const content = document.querySelector("#content");
const searchInput = document.querySelector("#searchInput");
const menuButton = document.querySelector("#menuButton");
const backdrop = document.querySelector("#sidebarBackdrop");

const escapeHtml = (value) => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
const array = (value) => Array.isArray(value) ? value : [];
const displayValue = (value) => value === null || value === undefined || value === "" ? "-" : String(value);
const code = (value) => `<code class="inline-code">${escapeHtml(displayValue(value))}</code>`;

function itemsFor(section) {
  const source = state.registry?.[section];
  if (Array.isArray(source)) return source;
  return Object.entries(source ?? {}).map(([id, value]) => ({ id, ...value }));
}

function searchText(item) {
  return [item.name, item.id, item.description, item.syntax, item.category, item.group, item.source?.group,
    ...array(item.aliases).map((alias) => typeof alias === "string" ? alias : alias.name)]
    .filter(Boolean).join(" ").toLocaleLowerCase("zh-CN");
}

function groupName(item) {
  if (state.section === "actions") return item.source?.group || item.category || "其他";
  if (state.section === "triggers") return item.category || "其他";
  if (state.section === "properties") return item.group || "其他";
  if (state.section === "types") return item.widget || "其他";
  return "选择器";
}

function filteredItems() {
  const query = state.query.trim().toLocaleLowerCase("zh-CN");
  return itemsFor(state.section).filter((item) => !query || searchText(item).includes(query));
}

function parseHash() {
  const [section, encodedId] = location.hash.slice(1).split("/");
  if (sections[section]) state.section = section;
  state.selectedId = encodedId ? decodeURIComponent(encodedId) : null;
}

function updateHash() {
  const suffix = state.selectedId ? `/${encodeURIComponent(state.selectedId)}` : "";
  const next = `#${state.section}${suffix}`;
  if (location.hash !== next) history.replaceState(null, "", next);
}

function renderTabs() {
  tabs.innerHTML = Object.entries(sections).map(([key, section]) => `<button class="tab-button" type="button" role="tab"
    data-section="${key}" aria-selected="${key === state.section}">${section.label}<span class="tab-count">${itemsFor(key).length}</span></button>`).join("");
}

function renderNavigation() {
  const items = filteredItems();
  resultSummary.textContent = state.query ? `找到 ${items.length} 项` : `共 ${items.length} 项`;
  if (!items.some((item) => item.id === state.selectedId)) state.selectedId = items[0]?.id ?? null;
  updateHash();
  if (!items.length) {
    navList.innerHTML = '<div class="empty-nav">没有匹配的文档</div>';
    return;
  }
  const groups = new Map();
  for (const item of items) {
    const group = groupName(item);
    if (!groups.has(group)) groups.set(group, []);
    groups.get(group).push(item);
  }
  navList.innerHTML = [...groups.entries()].sort(([a], [b]) => a.localeCompare(b, "zh-CN")).map(([group, entries]) => `
    <section class="nav-group"><h2 class="nav-group-title">${escapeHtml(group)}</h2>
    ${entries.sort((a, b) => String(a.name).localeCompare(String(b.name), "zh-CN")).map((item) => `
      <button class="nav-item" type="button" data-id="${escapeHtml(item.id)}" aria-current="${item.id === state.selectedId ? "page" : "false"}">
        <span class="nav-name">${escapeHtml(item.name || item.id)}</span>
        <span class="nav-description">${escapeHtml(item.description || item.rawType || item.id)}</span>
      </button>`).join("")}</section>`).join("");
}

function badges(values) {
  return values.filter((entry) => entry?.value !== null && entry?.value !== undefined && entry.value !== "")
    .map((entry) => `<span class="badge ${entry.className ?? ""}">${escapeHtml(entry.label ? `${entry.label}: ${entry.value}` : entry.value)}</span>`).join("");
}

function syntaxBlock(value) {
  if (!value) return "";
  return `<section class="doc-section"><h2>语法</h2><div class="syntax-block"><pre><code>${escapeHtml(value)}</code></pre>
    <button class="copy-button" type="button" data-copy="${escapeHtml(value)}">复制</button></div></section>`;
}

function tableSection(title, columns, rows) {
  if (!rows.length) return "";
  return `<section class="doc-section"><h2>${escapeHtml(title)}</h2><div class="table-wrap"><table class="doc-table">
    <thead><tr>${columns.map((column) => `<th>${escapeHtml(column.label)}</th>`).join("")}</tr></thead>
    <tbody>${rows.map((row) => `<tr>${columns.map((column) => `<td>${column.render ? column.render(row) : escapeHtml(displayValue(row[column.key]))}</td>`).join("")}</tr>`).join("")}</tbody>
  </table></div></section>`;
}

function examplesSection(examples) {
  const values = array(examples).filter(Boolean);
  if (!values.length) return "";
  return `<section class="doc-section"><h2>示例</h2><div class="example-list">${values.map((example) => `<pre class="example">${escapeHtml(typeof example === "string" ? example : example.code ?? JSON.stringify(example, null, 2))}</pre>`).join("")}</div></section>`;
}

function definitionSection(title, rows) {
  const visible = rows.filter((row) => row.value !== undefined && row.value !== null && row.value !== "");
  if (!visible.length) return "";
  return `<section class="doc-section"><h2>${escapeHtml(title)}</h2><dl class="definition-list">${visible.map((row) => `
    <div class="definition-row"><dt>${escapeHtml(row.label)}</dt><dd>${row.html ? row.value : escapeHtml(displayValue(row.value))}</dd></div>`).join("")}</dl></section>`;
}

const fieldColumns = [
  { label: "名称", render: (row) => code(row.name ?? row.key) },
  { label: "类型", render: (row) => code(row.type ?? array(row.acceptedTypes).join(" | ")) },
  { label: "读写/必填", render: (row) => escapeHtml(row.required ? "必填" : `${row.readable === false ? "不可读" : "可读"}${row.writable ? " / 可写" : ""}`) },
  { label: "说明", render: (row) => escapeHtml(row.description || (row.default !== undefined ? `默认: ${displayValue(row.default)}` : "-")) }
];

function renderAction(item) {
  return syntaxBlock(item.grammar?.syntax) + tableSection("参数", fieldColumns, array(item.grammar?.inputs)) +
    tableSection("语法变体", [
      { label: "标识", render: (row) => code(row.id) }, { label: "语法", render: (row) => code(row.syntax) },
      { label: "参数数", render: (row) => escapeHtml(array(row.inputs).length) }
    ], array(item.grammar?.variants).filter((variant, index, all) => all.length > 1 || index > 0)) +
    examplesSection(item.examples) + definitionSection("执行信息", [
      { label: "线程", value: item.execution?.thread }, { label: "可挂起", value: item.execution?.suspends ? "是" : "否" },
      { label: "上下文", value: array(item.execution?.contexts).join(", ") },
      { label: "输出", value: item.output?.status === "none" ? "无返回值" : item.output?.type || item.output?.status },
      { label: "注册来源", value: item.source?.registry }, { label: "源码符号", value: item.source?.symbol },
      { label: "要求", value: array(item.requirements).join(", ") }
    ]);
}

function renderSelector(item) {
  return syntaxBlock(item.syntax) + tableSection("参数", fieldColumns, array(item.params)) + examplesSection(item.examples);
}

function renderTrigger(item) {
  return definitionSection("事件信息", [
    { label: "Bukkit 事件", value: item.eventClass }, { label: "可取消", value: item.cancellable ? "是" : "否" }
  ]) + tableSection("变量", fieldColumns, array(item.variables)) + tableSection("特殊键", fieldColumns, array(item.specialKeys));
}

function renderProperty(item) {
  return syntaxBlock(item.usage) + tableSection("属性键", fieldColumns, array(item.keys));
}

function renderType(item) {
  return definitionSection("类型信息", [
    { label: "原始 Java 类型", value: item.rawType }, { label: "输入控件", value: item.widget },
    { label: "支持 Kether 填充", value: item.ketherFillable ? "是" : "否" }, { label: "输入提示", value: item.inputHint },
    { label: "父类型", value: array(item.parents).map(code).join(" ") || "无", html: true },
    { label: "子类型", value: array(item.children).map(code).join(" ") || "无", html: true },
    { label: "可接受类型", value: array(item.assignableFrom).map(code).join(" "), html: true }
  ]);
}

function renderDocument() {
  const item = itemsFor(state.section).find((entry) => entry.id === state.selectedId);
  if (!item) {
    content.innerHTML = '<div class="error-state"><strong>没有可显示的条目</strong><span>请调整搜索条件或选择其他文档类型。</span></div>';
    return;
  }
  const aliases = array(item.aliases).map((alias) => typeof alias === "string" ? alias : alias.name).filter(Boolean);
  const body = { actions: renderAction, selectors: renderSelector, triggers: renderTrigger,
    properties: renderProperty, types: renderType }[state.section](item);
  content.innerHTML = `<article class="doc">
    <p class="doc-breadcrumb">${escapeHtml(sections[state.section].label)} / ${escapeHtml(groupName(item))}</p>
    <h1>${escapeHtml(item.name || item.id)}</h1>
    <p class="doc-description">${escapeHtml(item.description || item.inputHint || "该条目暂无说明。")}</p>
    <div class="meta-row">${badges([
      { value: item.category, label: "分类", className: "badge-accent" }, { value: item.namespace, label: "命名空间" },
      { value: item.visibility, label: "可见性" }, { value: item.flow, label: "流程" },
      { value: aliases.join(", "), label: "别名" }, { value: item.deprecated, label: "已弃用", className: "badge-danger" }
    ])}</div>${body}<section class="doc-section"><h2>文档标识</h2><p>${code(item.id)}</p></section>
  </article>`;
}

function renderAll() { renderTabs(); renderNavigation(); renderDocument(); }
function closeSidebar() { document.body.classList.remove("sidebar-open"); menuButton.setAttribute("aria-expanded", "false"); }

tabs.addEventListener("click", (event) => {
  const button = event.target.closest("[data-section]");
  if (!button) return;
  state.section = button.dataset.section; state.selectedId = null; state.query = ""; searchInput.value = ""; renderAll();
});
navList.addEventListener("click", (event) => {
  const button = event.target.closest("[data-id]");
  if (!button) return;
  state.selectedId = button.dataset.id; renderNavigation(); renderDocument(); content.scrollTop = 0; closeSidebar();
});
searchInput.addEventListener("input", () => {
  state.query = searchInput.value; state.selectedId = null; renderNavigation(); renderDocument();
});
content.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-copy]");
  if (!button) return;
  await navigator.clipboard.writeText(button.dataset.copy); button.textContent = "已复制";
  window.setTimeout(() => { button.textContent = "复制"; }, 1200);
});
menuButton.addEventListener("click", () => {
  const open = document.body.classList.toggle("sidebar-open"); menuButton.setAttribute("aria-expanded", String(open));
});
backdrop.addEventListener("click", closeSidebar);
window.addEventListener("hashchange", () => { parseHash(); renderAll(); });

fetch("kether/kether-registry.json").then((response) => {
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}).then((registry) => {
  state.registry = registry; parseHash(); renderAll();
}).catch((error) => {
  content.innerHTML = `<div class="error-state"><strong>文档数据加载失败</strong><span>${escapeHtml(error.message)}</span><a href="kether/kether-registry.json">直接打开 Registry JSON</a></div>`;
});
