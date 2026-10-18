// Genera todos los archivos de la marca TechStore a partir de una sola fuente
// de verdad: las curvas del isotipo (abajo) y la tipografía Space Grotesk.
//
// Uso, desde esta carpeta:
//   npm install
//   node generar-marca.mjs
//
// Escribe en cuatro lugares del repositorio, y en ninguno más:
//   docs/marca/recursos/              kit de marca (SVG y PNG)
//   techstore-mobile/assets/branding/ ícono adaptativo y splash de la app
//   techstore-frontend/public/        isotipo y logos de la web
//   techstore-backend/.../logo/       isotipo de los PDF (factura, KuDE)
//
// Si se toca una curva o un color, se vuelve a correr esto y se regeneran los
// íconos de Android con `dart run flutter_launcher_icons` y
// `dart run flutter_native_splash:create` desde techstore-mobile.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import opentype from 'opentype.js';
import { Resvg } from '@resvg/resvg-js';

const aqui = path.dirname(fileURLToPath(import.meta.url));
const raiz = path.resolve(aqui, '../../..');
const destinos = {
  kit: path.join(raiz, 'docs/marca/recursos'),
  app: path.join(raiz, 'techstore-mobile/assets/branding'),
  web: path.join(raiz, 'techstore-frontend/public'),
  pdf: path.join(raiz, 'techstore-backend/src/main/resources/logo'),
};
for (const dir of Object.values(destinos)) fs.mkdirSync(dir, { recursive: true });
fs.mkdirSync(path.join(destinos.kit, 'png'), { recursive: true });

// ---------------------------------------------------------------------------
// El isotipo, en una grilla de 96. La T y el corte del fuste son paralelos al
// chaflán de la esquina: juntos forman un «//», el comentario del código. El
// fragmento de abajo es el cursor.
// ---------------------------------------------------------------------------
const T = 'M12 26L28 16H81Q84 16 84 19V33Q84 36 81 36H64V52L32 72V36H15Q12 36 12 33Z';
const CURSOR = 'M32 78L64 58V79Q64 82 61 82H35Q32 82 32 79Z';
// Caja que ocupan las dos piezas dentro de la grilla.
const CAJA = { x: 12, y: 16, ancho: 72, alto: 66 };
const CENTRO = { x: CAJA.x + CAJA.ancho / 2, y: CAJA.y + CAJA.alto / 2 };

// Colores. «oscuro» es para fondos negros (el uso principal); «claro», para
// papel y fondos blancos, donde el cian se lava y el azul necesita más tinta.
const PALETA = {
  oscuro: { tDesde: '#2563EB', tHasta: '#3B82F6', cursor: '#38BDF8', tech: '#F8FAFC', store: '#60A5FA' },
  claro: { tDesde: '#1D4ED8', tHasta: '#2563EB', cursor: '#0284C7', tech: '#080A10', store: '#2563EB' },
};

let idGradiente = 0;
/** Las dos piezas del isotipo, ya pintadas, como fragmento SVG. */
function piezas(variante, { mono = null } = {}) {
  if (mono) return `<path d="${T}" fill="${mono}"/><path d="${CURSOR}" fill="${mono}"/>`;
  const c = PALETA[variante];
  const id = `ts-t-${variante}-${idGradiente++}`;
  return `<defs><linearGradient id="${id}" x1="12" y1="16" x2="84" y2="82" gradientUnits="userSpaceOnUse">` +
    `<stop offset="0" stop-color="${c.tDesde}"/><stop offset="1" stop-color="${c.tHasta}"/></linearGradient></defs>` +
    `<path d="${T}" fill="url(#${id})"/><path d="${CURSOR}" fill="${c.cursor}"/>`;
}

/** Isotipo suelto, recortado a su caja con un margen proporcional. */
function isotipoSuelto(variante, opciones = {}) {
  const m = 6;
  const vb = `${CAJA.x - m} ${CAJA.y - m} ${CAJA.ancho + 2 * m} ${CAJA.alto + 2 * m}`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}" role="img" aria-label="TechStore"><title>TechStore</title>${piezas(variante, opciones)}</svg>`;
}

/** Isotipo dentro de su cuadrado negro: favicon, avatar y documentos. */
function isotipoInsignia() {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96" width="96" height="96" role="img" aria-label="TechStore">` +
    `<title>TechStore</title>` +
    // El cuadrado trae su propio fondo: se lee igual sobre una pestaña clara,
    // una barra oscura o el papel.
    `<rect width="96" height="96" rx="22" fill="#000000"/>` +
    `<rect x="0.5" y="0.5" width="95" height="95" rx="21.5" fill="none" stroke="#253247" stroke-opacity="0.9"/>` +
    `<g transform="translate(${48 - CENTRO.x} ${48 - CENTRO.y})">${piezas('oscuro')}</g></svg>`;
}

// ---------------------------------------------------------------------------
// Logotipo: «TechStore» en Space Grotesk Bold, convertido a curvas para que no
// dependa de tener la fuente instalada.
// ---------------------------------------------------------------------------
const fuentes = path.join(raiz, 'techstore-mobile/assets/fonts');
const bold = opentype.loadSync(path.join(fuentes, 'SpaceGrotesk-700.ttf'));
const ALTO_MAYUSCULA = bold.tables.os2.sCapHeight / bold.unitsPerEm; // en em
const ESPACIADO = -20; // milésimas de em: apretado, como un titular

function palabra(texto, size, x, y) {
  let cursor = x;
  const glifos = bold.stringToGlyphs(texto);
  const camino = new opentype.Path();
  glifos.forEach((g, i) => {
    camino.extend(g.getPath(cursor, y, size));
    const kern = i < glifos.length - 1 ? bold.getKerningValue(g, glifos[i + 1]) : 0;
    cursor += ((g.advanceWidth + kern) / bold.unitsPerEm) * size + (ESPACIADO / 1000) * size;
  });
  return { d: camino.toPathData(2), fin: cursor, caja: camino.getBoundingBox() };
}

/** «Tech» y «Store» a partir de x, con la línea de base en y. */
function logotipo(variante, size, x, y) {
  const c = PALETA[variante];
  const tech = palabra('Tech', size, x, y);
  const store = palabra('Store', size, tech.fin, y);
  return {
    svg: `<path d="${tech.d}" fill="${c.tech}"/><path d="${store.d}" fill="${c.store}"/>`,
    x1: Math.min(tech.caja.x1, store.caja.x1),
    x2: Math.max(tech.caja.x2, store.caja.x2),
    y1: Math.min(tech.caja.y1, store.caja.y1),
    y2: Math.max(tech.caja.y2, store.caja.y2),
  };
}

function soloPalabra(variante) {
  const size = 100;
  const p = logotipo(variante, size, 0, 100);
  const m = size * ALTO_MAYUSCULA * 0.5;
  const vb = [p.x1 - m, p.y1 - m, p.x2 - p.x1 + 2 * m, p.y2 - p.y1 + 2 * m].map((n) => n.toFixed(2)).join(' ');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}" role="img" aria-label="TechStore"><title>TechStore</title>${p.svg}</svg>`;
}

/** Isotipo a la izquierda y nombre a la derecha. */
function imagotipoHorizontal(variante) {
  const size = 100;
  const mayuscula = size * ALTO_MAYUSCULA;
  const base = 100;
  // El isotipo mide un poco más que la mayúscula y se centra en su banda.
  const escala = (mayuscula * 1.18) / CAJA.alto;
  const altoIsotipo = CAJA.alto * escala;
  const anchoIsotipo = CAJA.ancho * escala;
  const yIsotipo = base - mayuscula - (altoIsotipo - mayuscula) / 2;
  const separacion = mayuscula * 0.42;
  const p = logotipo(variante, size, anchoIsotipo + separacion, base);
  const iso = `<g transform="translate(0 ${yIsotipo.toFixed(2)}) scale(${escala.toFixed(4)}) translate(${-CAJA.x} ${-CAJA.y})">${piezas(variante)}</g>`;
  const resguardo = mayuscula * 0.5;
  const y1 = Math.min(yIsotipo, p.y1);
  const y2 = Math.max(yIsotipo + altoIsotipo, p.y2);
  const vb = [-resguardo, y1 - resguardo, p.x2 + 2 * resguardo, y2 - y1 + 2 * resguardo].map((n) => n.toFixed(2)).join(' ');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}" role="img" aria-label="TechStore"><title>TechStore</title>${iso}${p.svg}</svg>`;
}

/** Isotipo arriba y nombre abajo, centrados. */
function imagotipoVertical(variante) {
  const size = 100;
  const mayuscula = size * ALTO_MAYUSCULA;
  const prueba = logotipo(variante, size, 0, 0);
  const anchoPalabra = prueba.x2 - prueba.x1;
  const escala = (mayuscula * 2.3) / CAJA.alto;
  const anchoIsotipo = CAJA.ancho * escala;
  const altoIsotipo = CAJA.alto * escala;
  const separacion = mayuscula * 0.62;
  const base = altoIsotipo + separacion + mayuscula;
  const p = logotipo(variante, size, -prueba.x1, base);
  const xIsotipo = (anchoPalabra - anchoIsotipo) / 2;
  const iso = `<g transform="translate(${xIsotipo.toFixed(2)} 0) scale(${escala.toFixed(4)}) translate(${-CAJA.x} ${-CAJA.y})">${piezas(variante)}</g>`;
  const resguardo = mayuscula * 0.6;
  const vb = [-resguardo, -resguardo, anchoPalabra + 2 * resguardo, p.y2 + 2 * resguardo].map((n) => n.toFixed(2)).join(' ');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}" role="img" aria-label="TechStore"><title>TechStore</title>${iso}${p.svg}</svg>`;
}

// ---------------------------------------------------------------------------
// Íconos de Android. El lienzo de 1024 representa los 108 dp del ícono
// adaptativo; el sistema recorta con su máscara (círculo, gota, cuadrado) y
// solo garantiza el círculo central de 66 dp. El isotipo se escala para que su
// esquina más lejana quede adentro de ese círculo.
// ---------------------------------------------------------------------------
const RADIO_ISOTIPO = Math.max(
  ...[[12, 26], [28, 16], [84, 16], [84, 36], [64, 82], [32, 82], [12, 36]]
    .map(([x, y]) => Math.hypot(x - CENTRO.x, y - CENTRO.y)),
);

function lienzo(tamano, contenido, fondo = '') {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${tamano}" height="${tamano}" viewBox="0 0 ${tamano} ${tamano}">${fondo}${contenido}</svg>`;
}

function centrado(tamano, unidad, fragmento) {
  return `<g transform="translate(${tamano / 2} ${tamano / 2}) scale(${unidad}) translate(${-CENTRO.x} ${-CENTRO.y})">${fragmento}</g>`;
}

const SEGURO_ADAPTATIVO = (1024 * 66) / 108 / 2; // radio del círculo seguro
const unidadIcono = (SEGURO_ADAPTATIVO * 0.96) / RADIO_ISOTIPO;

const frente = lienzo(1024, centrado(1024, unidadIcono, piezas('oscuro')));
const monocromo = lienzo(1024, centrado(1024, unidadIcono, piezas('oscuro', { mono: '#FFFFFF' })));
const fondoIcono = lienzo(1024, '', `<defs><radialGradient id="r" cx="0.5" cy="0.46" r="0.62">` +
  `<stop offset="0" stop-color="#0B1120"/><stop offset="0.55" stop-color="#05070C"/><stop offset="1" stop-color="#000000"/></radialGradient>` +
  `<radialGradient id="h" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#2563EB" stop-opacity="0.22"/><stop offset="1" stop-color="#2563EB" stop-opacity="0"/></radialGradient></defs>` +
  `<rect width="1024" height="1024" fill="url(#r)"/><circle cx="512" cy="512" r="330" fill="url(#h)"/>`);
// Ícono clásico (Android 7 y anteriores, y la ficha de la tienda): el cuadrado
// negro completo, con el mismo halo que el adaptativo.
const clasico = lienzo(1024,
  `<g transform="translate(40 40) scale(${944 / 96})"><rect width="96" height="96" rx="22" fill="#000"/></g>` +
  `<circle cx="512" cy="512" r="330" fill="url(#h)"/>` +
  centrado(1024, (944 * 0.34) / RADIO_ISOTIPO, piezas('oscuro')),
  `<defs><radialGradient id="h" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#2563EB" stop-opacity="0.2"/><stop offset="1" stop-color="#2563EB" stop-opacity="0"/></radialGradient></defs>`);

// Splash: 1152 px son los 288 dp de Android 12 a 4x; el isotipo entra en el
// círculo de 192 dp (768 px) que el sistema deja sin recortar. La misma imagen
// sirve de splash para las versiones anteriores, así el isotipo aparece del
// mismo tamaño en todos los teléfonos.
const UNIDAD_SPLASH = (768 / 2) * 0.97 / RADIO_ISOTIPO;
const splash = lienzo(1152, centrado(1152, UNIDAD_SPLASH, piezas('oscuro')));

// ---------------------------------------------------------------------------
// Escritura.
// ---------------------------------------------------------------------------
function png(svg, ancho, archivo) {
  const out = new Resvg(svg, { fitTo: { mode: 'width', value: ancho } }).render().asPng();
  fs.writeFileSync(archivo, out);
}
function escribir(archivo, texto) {
  fs.writeFileSync(archivo, texto.replace(/\s+\n/g, '\n') + '\n');
}

const kit = (n) => path.join(destinos.kit, n);
const insignia = isotipoInsignia();
escribir(kit('isotipo.svg'), isotipoSuelto('oscuro'));
escribir(kit('isotipo-claro.svg'), isotipoSuelto('claro'));
escribir(kit('isotipo-mono.svg'), isotipoSuelto('oscuro', { mono: 'currentColor' }));
escribir(kit('isotipo-insignia.svg'), insignia);
escribir(kit('logotipo.svg'), soloPalabra('oscuro'));
escribir(kit('logotipo-claro.svg'), soloPalabra('claro'));
escribir(kit('imagotipo-horizontal.svg'), imagotipoHorizontal('oscuro'));
escribir(kit('imagotipo-horizontal-claro.svg'), imagotipoHorizontal('claro'));
escribir(kit('imagotipo-vertical.svg'), imagotipoVertical('oscuro'));
escribir(kit('imagotipo-vertical-claro.svg'), imagotipoVertical('claro'));

for (const t of [16, 32, 48, 64, 128, 180, 192, 256, 512, 1024]) {
  png(insignia, t, kit(`png/isotipo-insignia-${t}.png`));
}
png(isotipoSuelto('oscuro'), 1024, kit('png/isotipo-1024.png'));
png(isotipoSuelto('claro'), 1024, kit('png/isotipo-claro-1024.png'));
png(imagotipoHorizontal('oscuro'), 1600, kit('png/imagotipo-horizontal-1600.png'));
png(imagotipoHorizontal('claro'), 1600, kit('png/imagotipo-horizontal-claro-1600.png'));
png(imagotipoVertical('oscuro'), 1000, kit('png/imagotipo-vertical-1000.png'));
png(imagotipoVertical('claro'), 1000, kit('png/imagotipo-vertical-claro-1000.png'));

const app = (n) => path.join(destinos.app, n);
png(frente, 1024, app('icono_app_frente.png'));
png(fondoIcono, 1024, app('icono_app_fondo.png'));
png(monocromo, 1024, app('icono_app_mono.png'));
png(clasico, 1024, app('icono_app.png'));
png(splash, 1152, app('splash_isotipo.png'));

// La web usa el isotipo con su cuadrado: es el favicon y va en la barra.
escribir(path.join(destinos.web, 'isotipo.svg'), insignia);
escribir(path.join(destinos.web, 'logo.svg'), imagotipoHorizontal('claro'));
escribir(path.join(destinos.web, 'logo-blanco.svg'), imagotipoHorizontal('oscuro'));

// En el PDF va sobre papel: la variante clara, sin cuadrado, para no gastar
// tinta en un bloque negro en cada factura.
png(isotipoSuelto('claro'), 512, path.join(destinos.pdf, 'isotipo.png'));

console.log(`Marca generada. Unidad del ícono: ${unidadIcono.toFixed(3)} px; del splash: ${UNIDAD_SPLASH.toFixed(3)} px por unidad de grilla.`);
console.log(`Isotipo en el splash: ${(CAJA.ancho * UNIDAD_SPLASH / 4).toFixed(1)} dp de ancho.`);
