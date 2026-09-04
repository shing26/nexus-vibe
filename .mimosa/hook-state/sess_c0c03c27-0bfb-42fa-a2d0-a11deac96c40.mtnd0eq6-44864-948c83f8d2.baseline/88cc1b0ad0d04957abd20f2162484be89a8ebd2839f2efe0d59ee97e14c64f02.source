import PrismLight from 'react-syntax-highlighter/dist/esm/prism-light';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
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

export default function CodeBlock({ language, code }: { language: string; code: string }) {
  const normalized = LANGUAGE_ALIASES[language.toLowerCase()] ?? language.toLowerCase();
  if (!SUPPORTED_LANGUAGES.has(normalized)) {
    return <pre className="overflow-x-auto p-4 text-xs text-slate-300">{code}</pre>;
  }

  return (
    <PrismLight
      style={oneDark}
      language={normalized}
      PreTag="div"
      customStyle={{ margin: 0, borderRadius: 0, fontSize: '0.8rem' }}
    >
      {code}
    </PrismLight>
  );
}
