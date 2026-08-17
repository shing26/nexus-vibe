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
           cyan: 'rgb(var(--vibe-cyan) / <alpha-value>)',
           neon: 'rgb(var(--vibe-neon) / <alpha-value>)',
           purple: 'rgb(var(--vibe-purple) / <alpha-value>)',
           emerald: 'rgb(var(--vibe-emerald) / <alpha-value>)',
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
       animation: {
         'border-beam': 'border-beam calc(var(--duration, 8) * 1s) infinite linear',
         'shimmer': 'shimmer 2.5s infinite linear',
       },
       keyframes: {
         'border-beam': {
           '100%': { 'offset-distance': '100%' },
         },
         'shimmer': {
           '0%': { backgroundPosition: '200% 0' },
           '100%': { backgroundPosition: '-200% 0' },
         }
       }
     },
   },
   plugins: [],
 }
