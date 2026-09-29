/**
 * 权限键匹配。必须与后端 sa-token 的语义一致，否则会出现"后端放行、前端不显示按钮"
 * 或者反过来的错位。
 *
 * 后端的规则（已从 sa-token 字节码确认）：
 * 1. 先做精确匹配（`List.contains`）
 * 2. 未命中则逐个用 `SaFoxUtil.vagueMatch(列表元素, 传入参数)` 比对 ——
 *    **列表里的元素是模式，传入的参数是目标**
 * 3. 模式里若不含 `*` 就是精确比较；含 `*` 则 `*` 匹配任意长度（含 0）的字符
 *
 * 注意第 2 条的方向：用户持有 `GET:/api/ops/*` 时，请求 `GET:/api/ops/1` 会命中。
 * 反过来（持有具体键、去匹配一个模式）不会命中。
 */

/**
 * 判断权限键列表里是否有能覆盖 `required` 的项。
 *
 * @param permissions 当前用户持有的权限键列表（模式列表）
 * @param required    本次要判断的具体权限键
 */
export function hasPermission(permissions: string[], required: string): boolean {
  if (!required) {
    return true
  }
  if (permissions.includes(required)) {
    return true
  }
  return permissions.some((pattern) => wildcardMatch(pattern, required))
}

/** 是否持有超级权限。持有者能看到全部按钮与菜单。 */
export function isSuperUser(permissions: string[]): boolean {
  return hasPermission(permissions, '*')
}

/**
 * 把带 `*` 的模式转成正则来匹配。
 *
 * 用"按 `*` 切分再转义拼接"而不是手写递归，是因为要保证正则元字符（`.`、`(`、`/` 等）
 * 在权限键里被当作字面量。权限键里出现 `{id}`、`:` 之类很常见，漏转义会导致误匹配 ——
 * 那是安全方向的错误，不能靠"看起来对"。
 */
function wildcardMatch(pattern: string, target: string): boolean {
  if (!pattern.includes('*')) {
    return pattern === target
  }
  const regexSource = pattern.split('*').map(escapeRegExp).join('.*')
  return new RegExp(`^${regexSource}$`).test(target)
}

/** 转义正则元字符，让它按字面量匹配 */
function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
