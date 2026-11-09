#!/usr/bin/env node
// Vue2 -> Vue3 模板层 codemod（mall-pc / mall-web 一次性迁移用，跑完即可弃用）
// 用法: node v3-codemod.mjs <appDir>   例如 node v3-codemod.mjs mall-web
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const app = process.argv[2]
if (!app) { console.error('usage: node v3-codemod.mjs <appDir>'); process.exit(1) }

function walk(dir) {
  return readdirSync(dir).flatMap(n => {
    const p = join(dir, n)
    return statSync(p).isDirectory() ? walk(p) : (p.endsWith('.vue') ? [p] : [])
  })
}
const files = walk(join(app, 'src'))
let stats = { visibleSync: 0, genericSync: 0, tplSlotScope: 0, tplSlot: 0, native: 0, deep: 0, setDel: 0, wrapSlot: 0 }

for (const f of files) {
  let src = readFileSync(f, 'utf8')
  const orig = src

  // 1) :visible.sync="x" -> v-model="x"
  src = src.replace(/:visible\.sync="([^"]+)"/g, (_, v) => { stats.visibleSync++; return `v-model="${v}"` })
  // 2) 其余 .sync -> v-model:prop（通用语义：Vue2 .sync = Vue3 v-model:prop）
  src = src.replace(/:([\w-]+)\.sync="([^"]+)"/g, (_, p, v) => { stats.genericSync++; return `v-model:${p}="${v}"` })

  // 3) <template slot-scope="x"> / <template slot="n" slot-scope="x"> / <template slot="n">
  src = src.replace(/<template\s+slot="([\w-]+)"\s+slot-scope="([^"]+)">/g, (_, n, sc) => { stats.tplSlotScope++; return `<template #${n}="${sc}">` })
  src = src.replace(/<template\s+slot-scope="([^"]+)">/g, (_, sc) => { stats.tplSlotScope++; return `<template #default="${sc}">` })
  src = src.replace(/<template\s+slot="([\w-]+)">/g, (_, n) => { stats.tplSlot++; return `<template #${n}>` })

  // 4) .native 移除：@click.native= / @keyup.enter.native= / @submit.native.prevent 等
  src = src.replace(/(@[\w-]+(?:\.[\w-]+)*)\.native\b/g, (_, ev) => { stats.native++; return ev })

  // 5) style 块内 >>> -> :deep(...)
  src = src.replace(/(<style[^>]*>)([\s\S]*?)(<\/style>)/g, (_, open, css, close) => {
    const next = css.replace(/^(\s*)([^{}\n]*?)\s*>>>\s*([^{,\n]+)([,{}]?)/gm,
      (_, ind, pre, sel, tail) => { stats.deep++; return `${ind}${pre} :deep(${sel.trim()})${tail}` })
    return open + next + close
  })

  // 6) $set / $delete -> 直接赋值（Vue3 proxy 原生响应）
  src = src.replace(/this\.\$set\(([\w.$[\]"']+),\s*([^,]+?),\s*(.+)\)(\s*;?)/g,
    (_, obj, key, val, semi) => { stats.setDel++; return `${obj}[${key.trim()}] = ${val}${semi}` })
  src = src.replace(/this\.\$delete\(([\w.$[\]"']+),\s*([^,]+?)\)(\s*;?)/g,
    (_, obj, key, semi) => { stats.setDel++; return `delete ${obj}[${key}]${semi}` })

  // 7) 非模板元素上的 slot="name" -> 包 <template #name>（配对闭合标签，深度计数）
  src = wrapSlotElements(src)

  if (src !== orig) { writeFileSync(f, src); console.log('modified', f) }
}
console.log('stats', stats)

function wrapSlotElements(src) {
  // 逐行扫描，找到 <tag ... slot="name" ...>，替换开标签并在配对 </tag> 后补 </template>
  const lines = src.split('\n')
  const out = []
  const TAG = /<([a-zA-Z][\w-]*)((?:\s+[\w:.\-@"']+=(?:"[^"]*"|'[^']*')|\s+[\w:.\-@"']+)*?)?\s*(\/?)>/
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    const m = line.match(TAG)
    if (m && !m[3] && /\sslot="([\w-]+)"/.test(line) && !/^<template\b/.test(line)) {
      const tag = m[1], slot = line.match(/\sslot="([\w-]+)"/)[1]
      // 只处理已知形态（footer/header/dropdown/append/label/prepend），避免误伤
      if (!['footer', 'header', 'dropdown', 'append', 'label', 'prepend'].includes(slot)) { out.push(line); i++; continue }
      const openNoSlot = line.replace(/\s*slot="[\w-]+"/, '')
      if (/\/>\s*$/.test(line)) {
        out.push(openNoSlot.replace(TAG, (_s, t, attrs) => `<template #${slot}><${t}${attrs || ''}/>`) + '</template>')
        stats.wrapSlot++; i++; continue
      }
      // 同行闭合：<el-button slot="append" ...>文本</el-button> → <template #append><el-button ...>文本</el-button></template>
      const closeIdx = openNoSlot.indexOf(`</${tag}>`)
      if (closeIdx !== -1) {
        out.push(`<template #${slot}>` + openNoSlot + '</template>')
        stats.wrapSlot++; i++; continue
      }
      out.push(openNoSlot.replace(TAG, (_s, t, attrs) => `<template #${slot}><${t}${attrs || ''}>`))
      // 深度扫描配对闭合，扫描到的中间行照常推进输出
      let depth = 1
      let j = i + 1
      for (; j < lines.length && depth > 0; j++) {
        const openTags = (lines[j].match(new RegExp(`<${tag}[\\s>]`, 'g')) || []).length
        const closeTags = (lines[j].match(new RegExp(`</${tag}>`, 'g')) || []).length
        depth += openTags - closeTags
        if (depth === 0) {
          out.push(lines[j].replace(new RegExp(`(</${tag}>)`), (_s, c) => `${c}</template>`))
        } else {
          out.push(lines[j])
        }
      }
      if (depth !== 0) throw new Error(`unbalanced <${tag}> for slot=${slot} at line ${i + 1}`)
      stats.wrapSlot++
      i = j
      continue
    }
    out.push(line)
    i++
  }
  return out.join('\n')
}
