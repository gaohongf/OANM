import type { MenuNode } from '../../../types/api'

/** 拖动落点：行的上边缘 / 中部 / 下边缘 */
export type MenuDropPosition = 'before' | 'inside' | 'after'

/**
 * 一次移动需要写回后端的节点。
 *
 * 只需要真正变化的行：挂为子项时只有被拖动的节点变；同级换序时，
 * 被拖动的节点和它前后位置变化的兄弟节点都要重写 `sort`。
 */
export interface MenuMovePlan {
  id: string
  parentId: string | null
  sort: number
}

export function findMenuNode(tree: MenuNode[], id: string): MenuNode | null {
  for (const node of tree) {
    if (node.id === id) {
      return node
    }
    const found = findMenuNode(node.children, id)
    if (found) {
      return found
    }
  }
  return null
}

/** targetId 是否在 root 的子树里（含 root 自身） */
export function containsMenuNode(root: MenuNode, targetId: string): boolean {
  return root.id === targetId || root.children.some((child) => containsMenuNode(child, targetId))
}

/** 所有节点 id，用于默认展开整棵树 */
export function collectMenuKeys(tree: MenuNode[]): string[] {
  return tree.flatMap((node) => [node.id, ...collectMenuKeys(node.children)])
}

/**
 * 计算把 `draggedId` 放到 `targetId` 的某个位置后，需要写回的节点。
 *
 * ## 落点语义
 * - `inside`：成为目标节点的最后一个子项；
 * - `before` / `after`：与目标同一父节点，排在目标前 / 后。
 *
 * ## 同级换序会重新编号
 * 后端按 `sort`（相同时按 id）排序，只把被拖动的行改成"目标邻居的 sort"会撞值，
 * 最终顺序取决于 id，用户看到的顺序和拖出来的不一定一致。所以整组兄弟按顺序重排为
 * 1..n，只提交 sort 真正变化的行。
 *
 * ## 非法移动返回空数组
 * - 拖到自己身上，或拖进自己的子树（会成环）。后端也会拒绝，这里先拦住，
 *   避免一次注定失败的请求和一次"闪一下又回去"的动画。
 */
export function planMenuMove(
  tree: MenuNode[],
  draggedId: string,
  targetId: string,
  position: MenuDropPosition,
): MenuMovePlan[] {
  const dragged = findMenuNode(tree, draggedId)
  const target = findMenuNode(tree, targetId)
  if (!dragged || !target) {
    return []
  }
  if (dragged.id === target.id || containsMenuNode(dragged, target.id)) {
    return []
  }

  if (position === 'inside') {
    const siblings = target.children.filter((child) => child.id !== dragged.id)
    const maxSort = siblings.reduce((max, child) => Math.max(max, child.sort), 0)
    // 已经在目标下且本来就是最后一个子项：不需要写库
    if (dragged.parentId === target.id && dragged.sort > maxSort) {
      return []
    }
    return [{ id: dragged.id, parentId: target.id, sort: maxSort + 1 }]
  }

  const siblings =
    target.parentId === null
      ? tree
      : (findMenuNode(tree, target.parentId)?.children ?? [])
  const ordered = siblings.filter((node) => node.id !== dragged.id)
  const targetIndex = ordered.findIndex((node) => node.id === target.id)
  if (targetIndex < 0) {
    return []
  }

  ordered.splice(position === 'before' ? targetIndex : targetIndex + 1, 0, dragged)

  return ordered
    .map((node, index) => ({
      id: node.id,
      parentId: target.parentId,
      sort: index + 1,
      current: node,
    }))
    .filter(({ sort, current }) => sort !== current.sort || target.parentId !== current.parentId)
    .map(({ id, parentId, sort }) => ({ id, parentId, sort }))
}
