import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const root = path.resolve(import.meta.dirname, '..')
const contract = JSON.parse(
  fs.readFileSync(path.join(root, 'docs/api/techstore-v1.openapi.json'), 'utf8')
)

const normalize = (value) => value
  .split('?')[0]
  .replace(/\$\{?[$A-Z_a-z][$\w.]*(?:\})?/g, '{}')
  .replace(/\{[^}]+}/g, '{}')

const canonical = new Set(Object.keys(contract.paths).map(normalize))
const operations = new Set()
for (const [route, definition] of Object.entries(contract.paths)) {
  for (const method of ['get', 'post', 'put', 'delete', 'patch']) {
    if (definition[method]) operations.add(`${method} ${normalize(route)}`)
  }
}
const sources = [
  path.join(root, 'techstore-mobile/lib'),
]
const extensions = new Set(['.dart'])
const used = new Map()
const calledOperations = new Map()

function walk(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) walk(target)
    else if (extensions.has(path.extname(entry.name))) inspect(target)
  }
}

function inspect(file) {
  const content = fs.readFileSync(file, 'utf8').replace(/\$\{[^}]+}/g, '{}')
  for (const match of content.matchAll(/['"`](\/api\/v1[^'"`\r\n]*)['"`]/g)) {
    const raw = match[1]
    const endpoint = normalize(raw)
    const locations = used.get(endpoint) ?? new Set()
    locations.add(path.relative(root, file).replaceAll('\\', '/'))
    used.set(endpoint, locations)
  }
  for (const match of content.matchAll(
    /\.(get|post|put|delete|patch)(?:<[^()]+>)?\s*\(\s*['"`](\/api\/v1[^'"`\r\n]*)['"`]/g
  )) {
    const operation = `${match[1]} ${normalize(match[2])}`
    const locations = calledOperations.get(operation) ?? new Set()
    locations.add(path.relative(root, file).replaceAll('\\', '/'))
    calledOperations.set(operation, locations)
  }
}

for (const source of sources) walk(source)

const missing = [...used]
  .filter(([endpoint]) => !canonical.has(endpoint))
  .sort(([left], [right]) => left.localeCompare(right))
const missingOperations = [...calledOperations]
  .filter(([operation]) => !operations.has(operation))
  .sort(([left], [right]) => left.localeCompare(right))

console.log(
  `API_USAGE_AUDIT routes=${used.size} operations=${calledOperations.size} ` +
  `missing_routes=${missing.length} missing_operations=${missingOperations.length}`
)
for (const [endpoint, locations] of missing) {
  console.error(`- ${endpoint} <- ${[...locations].join(', ')}`)
}
for (const [operation, locations] of missingOperations) {
  console.error(`- ${operation} <- ${[...locations].join(', ')}`)
}
if (missing.length || missingOperations.length) process.exitCode = 1
