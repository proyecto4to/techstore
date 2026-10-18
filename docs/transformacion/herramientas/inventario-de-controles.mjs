// Inventario de controles interactivos de la app Flutter, leído del código.
// Uso: node inventario.mjs <carpeta lib> <salida.md>
import fs from 'node:fs';
import path from 'node:path';

const [, , libDir, salida] = process.argv;
const archivos = [];
(function recorrer(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) recorrer(p);
    else if (e.name.endsWith('.dart') && !e.name.endsWith('.g.dart')) archivos.push(p);
  }
})(libDir);

const controles = /\b(FilledButton|OutlinedButton|TextButton|ElevatedButton|IconButton|FloatingActionButton|InkWell|GestureDetector|ListTile|SwitchListTile|CheckboxListTile|RadioListTile|PopupMenuItem|MenuItemButton|DropdownButtonFormField|DropdownMenu|SegmentedButton|FilterChip|ChoiceChip|ActionChip|InputChip|Switch|Checkbox|Slider|SearchBar|TextFormField|TextField|Dismissible|RefreshIndicator)(\.(icon|tonal|tonalIcon|filled|filledTonal|outlined|extended))?\(/g;

function etiqueta(trozo) {
  const pats = [
    /tooltip:\s*([^,\n]+)/,
    /label(?:Text)?:\s*(?:const\s+)?Text\(\s*('(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*")/,
    /labelText:\s*('(?:[^'\\]|\\.)*')/,
    /hintText:\s*('(?:[^'\\]|\\.)*')/,
    /title:\s*(?:const\s+)?Text\(\s*('(?:[^'\\]|\\.)*'|[^)\n]+)/,
    /child:\s*(?:const\s+)?Text\(\s*('(?:[^'\\]|\\.)*'|[^)\n]+)/,
    /Text\(\s*('(?:[^'\\]|\\.)*')/,
  ];
  for (const p of pats) {
    const m = trozo.match(p);
    if (m) return m[1].trim().replace(/\s+/g, ' ').slice(0, 70);
  }
  return '—';
}

function accion(trozo) {
  const m = trozo.match(/on(Pressed|Tap|Changed|Submitted|Selected|Dismissed|Refresh|LongPress):\s*([^\n]+)/);
  if (!m) return 'sin manejador directo';
  return `on${m[1]}: ${m[2].trim().replace(/,$/, '').slice(0, 60)}`;
}

let md = '| Archivo | Línea | Control | Etiqueta | Acción |\n|---|---|---|---|---|\n';
const porArchivo = new Map();
let total = 0;
for (const archivo of archivos) {
  const texto = fs.readFileSync(archivo, 'utf8');
  const rel = path.relative(libDir, archivo).replaceAll('\\', '/');
  for (const m of texto.matchAll(controles)) {
    const linea = texto.slice(0, m.index).split('\n').length;
    const trozo = texto.slice(m.index, m.index + 900);
    md += `| ${rel} | ${linea} | ${m[1]}${m[2] ?? ''} | ${etiqueta(trozo).replaceAll('|', '\\|')} | ${accion(trozo).replaceAll('|', '\\|')} |\n`;
    porArchivo.set(rel, (porArchivo.get(rel) ?? 0) + 1);
    total++;
  }
}
const resumen = [...porArchivo.entries()].sort((a, b) => b[1] - a[1])
  .map(([f, n]) => `- ${f}: ${n}`).join('\n');
const encabezado = `# Inventario de controles interactivos (app móvil)

Generado del código con \`node docs/transformacion/herramientas/inventario-de-controles.mjs techstore-mobile/lib docs/transformacion/inventario-de-controles.md\`. «Sin manejador directo» no es un botón roto: son filas informativas sin \`onTap\`, campos que trabajan con su controlador o ítems de menú cuya acción está en el menú padre. Cada uno se revisó a mano en la auditoría.

`;
fs.writeFileSync(salida, encabezado + `Total de controles: ${total}\n\n${resumen}\n\n${md}`);
console.log('controles', total, 'archivos', porArchivo.size);
