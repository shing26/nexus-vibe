import type { CSSProperties } from 'react';
import PrismLight from 'react-syntax-highlighter/dist/esm/prism-light';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { useThemeStore } from '../stores/themeStore';
import bash from 'react-syntax-highlighter/dist/esm/languages/prism/bash';
import c from 'react-syntax-highlighter/dist/esm/languages/prism/c';
import cpp from 'react-syntax-highlighter/dist/esm/languages/prism/cpp';
import csharp from 'react-syntax-highlighter/dist/esm/languages/prism/csharp';
import css from 'react-syntax-highlighter/dist/esm/languages/prism/css';
import diff from 'react-syntax-highlighter/dist/esm/languages/prism/diff';
import docker from 'react-syntax-highlighter/dist/esm/languages/prism/docker';
import go from 'react-syntax-highlighter/dist/esm/languages/prism/go';
import graphql from 'react-syntax-highlighter/dist/esm/languages/prism/graphql';
import ini from 'react-syntax-highlighter/dist/esm/languages/prism/ini';
import java from 'react-syntax-highlighter/dist/esm/languages/prism/java';
import javascript from 'react-syntax-highlighter/dist/esm/languages/prism/javascript';
import json from 'react-syntax-highlighter/dist/esm/languages/prism/json';
import jsx from 'react-syntax-highlighter/dist/esm/languages/prism/jsx';
import kotlin from 'react-syntax-highlighter/dist/esm/languages/prism/kotlin';
import less from 'react-syntax-highlighter/dist/esm/languages/prism/less';
import markdown from 'react-syntax-highlighter/dist/esm/languages/prism/markdown';
import markup from 'react-syntax-highlighter/dist/esm/languages/prism/markup';
import objectivec from 'react-syntax-highlighter/dist/esm/languages/prism/objectivec';
import php from 'react-syntax-highlighter/dist/esm/languages/prism/php';
import python from 'react-syntax-highlighter/dist/esm/languages/prism/python';
import ruby from 'react-syntax-highlighter/dist/esm/languages/prism/ruby';
import rust from 'react-syntax-highlighter/dist/esm/languages/prism/rust';
import scss from 'react-syntax-highlighter/dist/esm/languages/prism/scss';
import sql from 'react-syntax-highlighter/dist/esm/languages/prism/sql';
import swift from 'react-syntax-highlighter/dist/esm/languages/prism/swift';
import toml from 'react-syntax-highlighter/dist/esm/languages/prism/toml';
import tsx from 'react-syntax-highlighter/dist/esm/languages/prism/tsx';
import typescript from 'react-syntax-highlighter/dist/esm/languages/prism/typescript';
import yaml from 'react-syntax-highlighter/dist/esm/languages/prism/yaml';

PrismLight.registerLanguage('bash', bash);
PrismLight.registerLanguage('c', c);
PrismLight.registerLanguage('cpp', cpp);
PrismLight.registerLanguage('csharp', csharp);
PrismLight.registerLanguage('css', css);
PrismLight.registerLanguage('diff', diff);
PrismLight.registerLanguage('docker', docker);
PrismLight.registerLanguage('go', go);
PrismLight.registerLanguage('graphql', graphql);
PrismLight.registerLanguage('ini', ini);
PrismLight.registerLanguage('java', java);
PrismLight.registerLanguage('javascript', javascript);
PrismLight.registerLanguage('json', json);
PrismLight.registerLanguage('jsx', jsx);
PrismLight.registerLanguage('kotlin', kotlin);
PrismLight.registerLanguage('less', less);
PrismLight.registerLanguage('markdown', markdown);
PrismLight.registerLanguage('markup', markup);
PrismLight.registerLanguage('objectivec', objectivec);
PrismLight.registerLanguage('php', php);
PrismLight.registerLanguage('python', python);
PrismLight.registerLanguage('ruby', ruby);
PrismLight.registerLanguage('rust', rust);
PrismLight.registerLanguage('scss', scss);
PrismLight.registerLanguage('sql', sql);
PrismLight.registerLanguage('swift', swift);
PrismLight.registerLanguage('toml', toml);
PrismLight.registerLanguage('tsx', tsx);
PrismLight.registerLanguage('typescript', typescript);
PrismLight.registerLanguage('yaml', yaml);

const LANGUAGE_ALIASES: Record<string, string> = {
  js: 'javascript',
  ts: 'typescript',
  py: 'python',
  sh: 'bash',
  shell: 'bash',
  zsh: 'bash',
  html: 'markup',
  htm: 'markup',
  yml: 'yaml',
  xml: 'markup',
  md: 'markdown',
  'c++': 'cpp',
  cs: 'csharp',
  golang: 'go',
  rb: 'ruby',
  rs: 'rust',
  kt: 'kotlin',
  dockerfile: 'docker',
  'objective-c': 'objectivec',
};

const SUPPORTED_LANGUAGES = new Set([
  'bash', 'c', 'cpp', 'csharp', 'css', 'diff', 'docker', 'go', 'graphql',
  'ini', 'java', 'javascript', 'json', 'jsx', 'kotlin', 'less', 'markdown',
  'markup', 'objectivec', 'php', 'python', 'ruby', 'rust', 'scss', 'sql',
  'swift', 'toml', 'tsx', 'typescript', 'yaml',
]);

/**
 * GitHub-light syntax tokens on a light background. `ghcolors`/`oneLight` ship
 * with the package, but several of their tokens (comments, strings) fall below
 * WCAG AA on white. These values are GitHub's own light-theme palette, chosen so
 * every token keeps >= 4.5:1 on #FFFFFF: text 15.0:1, comment 6.3:1,
 * keyword 5.9:1, string 11.2:1, number/constant 7.4:1, function 4.9:1,
 * variable 7.3:1, tag 6.7:1.
 */
const githubLight: Record<string, CSSProperties> = {
  'code[class*="language-"]': {
    color: '#1F2328',
    background: 'none',
    fontFamily: '"JetBrains Mono", ui-monospace, monospace',
  },
  'pre[class*="language-"]': {
    color: '#1F2328',
    background: '#FFFFFF',
    fontFamily: '"JetBrains Mono", ui-monospace, monospace',
  },
  comment: { color: '#59636E', fontStyle: 'italic' },
  prolog: { color: '#59636E', fontStyle: 'italic' },
  doctype: { color: '#59636E', fontStyle: 'italic' },
  cdata: { color: '#59636E', fontStyle: 'italic' },
  punctuation: { color: '#1F2328' },
  operator: { color: '#0550AE' },
  keyword: { color: '#CF222E' },
  'attr-name': { color: '#953800' },
  'attr-value': { color: '#0A3069' },
  string: { color: '#0A3069' },
  char: { color: '#0A3069' },
  regex: { color: '#0A3069' },
  number: { color: '#0550AE' },
  boolean: { color: '#0550AE' },
  constant: { color: '#0550AE' },
  symbol: { color: '#0550AE' },
  builtin: { color: '#0550AE' },
  function: { color: '#8250DF' },
  'class-name': { color: '#8250DF' },
  selector: { color: '#8250DF' },
  variable: { color: '#953800' },
  property: { color: '#0550AE' },
  tag: { color: '#116329' },
  entity: { color: '#116329' },
  url: { color: '#0A3069' },
  inserted: { color: '#116329' },
  deleted: { color: '#CF222E' },
  important: { color: '#CF222E', fontWeight: 'bold' },
  bold: { fontWeight: 'bold' },
  italic: { fontStyle: 'italic' },
  namespace: { opacity: 0.8 },
};

/**
 * `highlightLines` marks 1-based line numbers with a left rail. The Chaos Theatre
 * uses it to point at the line the review is about — the unguarded division, then
 * the guard the fix adds. Omitting it keeps every existing call site unchanged.
 */
export default function CodeBlock({
  language,
  code,
  highlightLines,
}: {
  language: string;
  code: string;
  highlightLines?: number[];
}) {
  const dark = useThemeStore((s) => s.dark);
  const normalized = LANGUAGE_ALIASES[language.toLowerCase()] ?? language.toLowerCase();
  const marked = highlightLines ?? [];
  if (!SUPPORTED_LANGUAGES.has(normalized)) {
    return (
      <pre className="overflow-x-auto bg-vibe-bg p-4 text-xs text-vibe-text">
        {marked.length
          ? code.split('\n').map((line, index) => (
              <span
                key={index}
                data-testid={marked.includes(index + 1) ? 'code-highlight-line' : undefined}
                className={
                  marked.includes(index + 1)
                    ? 'block border-l-2 border-vibe-warning bg-vibe-warning/10 pl-2'
                    : 'block'
                }
              >
                {line || ' '}
              </span>
            ))
          : code}
      </pre>
    );
  }

  return (
    <PrismLight
      style={dark ? oneDark : githubLight}
      language={normalized}
      PreTag="div"
      wrapLines={marked.length > 0}
      // `lineProps` only receives a real line number when the generator is asked
      // for line numbers; the column itself is hidden, we only want the number.
      showLineNumbers={marked.length > 0}
      lineNumberContainerStyle={{ display: 'none' }}
      lineNumberStyle={{ display: 'none' }}
      lineProps={
        marked.length > 0
          ? (lineNumber: number) => ({
              style: {
                display: 'block',
                ...(marked.includes(lineNumber)
                  ? {
                      borderLeft: '2px solid var(--color-vibe-warning, #d9a13b)',
                      backgroundColor: 'rgba(217, 161, 59, 0.12)',
                      paddingLeft: '0.5rem',
                    }
                  : {}),
              },
              'data-testid': marked.includes(lineNumber) ? 'code-highlight-line' : undefined,
            })
          : undefined
      }
      // R6: the block lives inside a TerminalWindow whose rounded corners come from
      // overflow-hidden, so a line longer than the viewport used to be clipped with no
      // way to reach the rest of it - on a phone, permanently. PreTag is a div, so
      // overflow-x:auto belongs here rather than on a pre that is never rendered.
      customStyle={{
        margin: 0,
        borderRadius: 0,
        fontSize: '0.8rem',
        maxWidth: '100%',
        overflowX: 'auto',
        WebkitOverflowScrolling: 'touch',
      }}
    >
      {code}
    </PrismLight>
  );
}
