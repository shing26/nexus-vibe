/** @type {import('tailwindcss').Config} */
export default {
   darkMode: 'class',
   content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
   theme: {
     extend: {
       colors: {
         vibe: {
           bg: 'rgb(var(--vibe-bg) / <alpha-value>)',
           surface: 'rgb(var(--vibe-surface) / <alpha-value>)',
           card: 'rgb(var(--vibe-card) / <alpha-value>)',
           border: 'rgb(var(--vibe-border) / <alpha-value>)',
           accent: 'rgb(var(--vibe-accent) / <alpha-value>)',
           success: 'rgb(var(--vibe-success) / <alpha-value>)',
           danger: 'rgb(var(--vibe-danger) / <alpha-value>)',
           warning: 'rgb(var(--vibe-warning) / <alpha-value>)',
           high: 'rgb(var(--vibe-high) / <alpha-value>)',
           text: 'rgb(var(--vibe-text) / <alpha-value>)',
           muted: 'rgb(var(--vibe-muted) / <alpha-value>)',
           faint: 'rgb(var(--vibe-faint) / <alpha-value>)',
         },
         slate: {
           100: 'rgb(var(--slate-100) / <alpha-value>)',
           200: 'rgb(var(--slate-200) / <alpha-value>)',
           300: 'rgb(var(--slate-300) / <alpha-value>)',
           400: 'rgb(var(--slate-400) / <alpha-value>)',
           500: 'rgb(var(--slate-500) / <alpha-value>)',
           600: 'rgb(var(--slate-600) / <alpha-value>)',
           700: 'rgb(var(--slate-700) / <alpha-value>)',
           800: 'rgb(var(--slate-800) / <alpha-value>)',
           900: 'rgb(var(--slate-900) / <alpha-value>)',
           950: 'rgb(var(--slate-950) / <alpha-value>)',
         },
       },
       fontFamily: {
         sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
         mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'monospace'],
       },
       animation: {
         'fade-in': 'fade-in 0.2s ease-out',
       },
       keyframes: {
         'fade-in': {
           '0%': { opacity: '0' },
           '100%': { opacity: '1' },
         },
       },
     },
   },
   plugins: [],
 }
