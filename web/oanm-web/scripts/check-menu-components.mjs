/**
 * 校验后端菜单里的 component 值都能在前端白名单里找到。
 *
 * ## 为什么需要它
 * `menus.component` 是后端存的一个字符串，前端按它去白名单里找页面组件。
 * 两边对不上时**不会报错**，只会渲染出"页面未注册" —— 而且只有点开那个菜单的人才会发现。
 * 加一个页面、或改一次文件名，就可能悄悄产生这种不一致。
 *
 * ## 用法
 * 需要后端在跑（会去调 /api/auth/me 拿菜单树）：
 *   node scripts/check-menu-components.mjs <token> [baseUrl]
 * 或者在浏览器里登录后从 localStorage 里取 oanm.token。
 *
 * ## 与 registry.ts 的关系
 * 这里的"白名单"是照着 registry.ts 的 glob 规则复刻的（列出 src/pages 下的 .tsx、
 * 排除 Login/NotFound、去掉前缀与扩展名）。没法直接复用那个 glob —— `import.meta.glob`
 * 只在 Vite 构建期存在。所以**两处要一起改**，改了 glob 记得回来看这里。
 */
import { readdirSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const PAGES_DIR = join(ROOT, 'src', 'pages')

/** 与 registry.ts 的 glob 排除项保持一致 */
const EXCLUDED = new Set(['Login.tsx', 'NotFound.tsx'])

/**
 * 页面私有的子组件目录，同样不在白名单里（见 registry.ts 的说明）。
 * 与 registry.ts 一样用目录名做约定，而不是逐个列文件名。
 */
const EXCLUDED_DIRS = new Set(['components'])

const token = process.argv[2]
const baseUrl = process.argv[3] ?? 'http://localhost:5173'

if (!token) {
  console.error('用法: node scripts/check-menu-components.mjs <token> [baseUrl]')
  console.error('提示: 浏览器登录后从 localStorage 的 oanm.token 里取。')
  process.exit(2)
}

/** 递归列出 pages 下所有页面，转成白名单键（相对 pages/、不带扩展名、用 / 分隔） */
function listWhitelist(dir = PAGES_DIR) {
  const keys = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (EXCLUDED_DIRS.has(entry)) {
        continue
      }
      keys.push(...listWhitelist(full))
      continue
    }
    if (!entry.endsWith('.tsx') || EXCLUDED.has(entry)) {
      continue
    }
    keys.push(relative(PAGES_DIR, full).split(sep).join('/').replace(/\.tsx$/, ''))
  }
  return keys.sort()
}

/** 收集菜单树里所有用到的 component */
function collectComponents(nodes, found = new Map()) {
  for (const node of nodes) {
    if (node.component) {
      found.set(node.component, node.name)
    }
    collectComponents(node.children ?? [], found)
  }
  return found
}

const whitelist = new Set(listWhitelist())

const response = await fetch(`${baseUrl}/api/auth/me`, { headers: { satoken: token } })
if (!response.ok) {
  console.error(`请求 /api/auth/me 失败: HTTP ${response.status}`)
  process.exit(2)
}

const body = await response.json()
if (body.type !== 'SUCCESS') {
  console.error(`接口返回失败: ${body.msg}`)
  process.exit(2)
}

const used = collectComponents(body.data.menus ?? [])

console.log(`前端白名单（${whitelist.size} 个）:`)
for (const key of whitelist) {
  console.log(`  ${key}`)
}

console.log(`\n菜单里引用的 component（${used.size} 个）:`)
const missing = []
for (const [component, menuName] of used) {
  const ok = whitelist.has(component)
  if (!ok) {
    missing.push({ component, menuName })
  }
  console.log(`  ${ok ? '✓' : '✗'} ${component}  （菜单：${menuName}）`)
}

const unused = [...whitelist].filter((key) => !used.has(key))
if (unused.length > 0) {
  console.log(`\n白名单里还没被任何菜单引用的页面（正常，不一定是问题）:`)
  for (const key of unused) {
    console.log(`  - ${key}`)
  }
}

if (missing.length > 0) {
  console.error(`\n有 ${missing.length} 个菜单指向了不存在的页面，点开会是"页面未注册":`)
  for (const { component, menuName } of missing) {
    console.error(`  「${menuName}」-> "${component}"`)
  }
  process.exit(1)
}

console.log('\n全部匹配：菜单指向的页面都存在。')
