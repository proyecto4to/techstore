export default {
  content: [
    "./index.html",
    "./src/**/*.{js,jsx}",
  ],
  theme: {
    extend: {
      colors: {
        // El 300 es el azul que se usa como texto: enlaces, importes, énfasis.
        // Estaba en #3E82F0, que sobre el fondo de tarjeta da 3.94:1 y no llega
        // al 4.5:1 que pide WCAG AA para texto normal. #518EF1 es el mismo tono
        // apenas más claro: 4.52:1 sobre tarjeta y 5.52:1 sobre la página.
        // El 400 sigue siendo el azul de fondos y bordes, no de texto.
        primary: { 300: '#518EF1', 400: '#0F66E6', 600: '#0B48B2' },
        secondary: { 400: '#7C3AED', 600: '#5B21B6' },
        accent: { 400: '#06B6D4', 600: '#0E7490' },
        dark: { bg: '#0F172A', card: '#1E293B', lighter: '#334155', border: '#475569' },
        neutral: { text: '#F1F5F9', secondary: '#CBD5E1', muted: '#94A3B8' },
      },
    },
  },
  plugins: [],
}
