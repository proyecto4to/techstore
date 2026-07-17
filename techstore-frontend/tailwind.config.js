export default {
  content: [
    "./index.html",
    "./src/**/*.{js,jsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: { 300: '#3E82F0', 400: '#0F66E6', 600: '#0B48B2' },
        secondary: { 400: '#7C3AED', 600: '#5B21B6' },
        accent: { 400: '#06B6D4', 600: '#0E7490' },
        dark: { bg: '#0F172A', card: '#1E293B', lighter: '#334155', border: '#475569' },
        neutral: { text: '#F1F5F9', secondary: '#CBD5E1', muted: '#94A3B8' },
      },
    },
  },
  plugins: [],
}
