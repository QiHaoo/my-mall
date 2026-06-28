/**
 * 树形数据工具函数
 * 用于分类管理的拖拽排序计算等场景
 */

/**
 * 遍历树形数据（深度优先）
 * @param tree 树形数组
 * @param callback 回调函数，返回 false 可跳过当前节点的子节点遍历
 */
export function traverseTree<T extends { children?: T[] }>(
  tree: T[],
  callback: (node: T, parent: T | null) => boolean | void
): void {
  function walk(nodes: T[], parent: T | null) {
    for (const node of nodes) {
      const result = callback(node, parent)
      if (result === false) continue
      if (node.children && node.children.length > 0) {
        walk(node.children, node)
      }
    }
  }
  walk(tree, null)
}

/**
 * 在树中按条件查找第一个匹配的节点
 * @param tree 树形数组
 * @param predicate 判断函数
 * @returns 匹配的节点，未找到返回 null
 */
export function findNode<T extends { children?: T[] }>(
  tree: T[],
  predicate: (node: T) => boolean
): T | null {
  for (const node of tree) {
    if (predicate(node)) return node
    if (node.children && node.children.length > 0) {
      const found = findNode(node.children, predicate)
      if (found) return found
    }
  }
  return null
}

/**
 * 获取节点的完整路径（从根到该节点的路径数组）
 * @param tree 树形数组
 * @param predicate 判断函数，匹配目标节点
 * @returns 从根到目标节点的路径数组，未找到返回空数组
 */
export function getNodePath<T extends { children?: T[] }>(
  tree: T[],
  predicate: (node: T) => boolean
): T[] {
  const path: T[] = []
  function walk(nodes: T[]): boolean {
    for (const node of nodes) {
      path.push(node)
      if (predicate(node)) return true
      if (node.children && node.children.length > 0) {
        if (walk(node.children)) return true
      }
      path.pop()
    }
    return false
  }
  walk(tree)
  return path
}

/**
 * 判断 target 是否是 ancestor 的子孙节点（用于拖拽时检测循环引用）
 * @param tree 树形数组
 * @param ancestorId 祖先节点 ID
 * @param targetId 目标节点 ID
 * @param idKey 主键字段名
 */
export function isDescendant<T extends Record<string, any>>(
  tree: T[],
  ancestorId: any,
  targetId: any,
  idKey: string
): boolean {
  function findSubtree(nodes: T[]): T | null {
    for (const node of nodes) {
      if (node[idKey] === ancestorId) return node
      if (node.children && node.children.length > 0) {
        const found = findSubtree(node.children)
        if (found) return found
      }
    }
    return null
  }

  const ancestor = findSubtree(tree)
  if (!ancestor || !ancestor.children) return false

  function checkDescendant(nodes: T[]): boolean {
    for (const node of nodes) {
      if (node[idKey] === targetId) return true
      if (node.children && node.children.length > 0) {
        if (checkDescendant(node.children)) return true
      }
    }
    return false
  }

  return checkDescendant(ancestor.children)
}
