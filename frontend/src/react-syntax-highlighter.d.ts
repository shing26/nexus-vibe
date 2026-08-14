declare module 'react-syntax-highlighter/dist/esm/prism-light' {
  import type { ComponentType, CSSProperties } from 'react';

  interface PrismLightProps {
    language?: string | undefined;
    style?: { [key: string]: CSSProperties } | undefined;
    children: string | string[];
    customStyle?: CSSProperties | undefined;
    PreTag?: string | ComponentType<Record<string, unknown>> | undefined;
    [key: string]: unknown;
  }

  const PrismLight: ComponentType<PrismLightProps> & {
    registerLanguage(name: string, func: unknown): void;
    alias(name: string, aliases: string | string[]): void;
  };

  export default PrismLight;
}

declare module 'react-syntax-highlighter/dist/esm/styles/prism' {
  import type { CSSProperties } from 'react';

  export const oneDark: { [key: string]: CSSProperties };
}

declare module 'react-syntax-highlighter/dist/esm/languages/prism/*' {
  const language: unknown;
  export default language;
}
