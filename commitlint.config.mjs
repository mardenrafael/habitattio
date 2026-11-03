// Commit message convention (AGENTS.md §10, specs/002-contribution-conventions).
// Self-contained on purpose: no `extends`, so commitlint can run through `npx -p`
// without a package.json or node_modules (research R2).
export default {
  parserPreset: {
    parserOpts: {
      headerPattern: /^(\w*)(?:\(([^)]*)\))?(!)?: (.*)$/,
      headerCorrespondence: ['type', 'scope', 'breaking', 'subject'],
      noteKeywords: ['BREAKING CHANGE', 'BREAKING-CHANGE'],
    },
  },
  rules: {
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'build', 'ci', 'chore'],
    ],
    'type-empty': [2, 'never'],
    'type-case': [2, 'always', 'lower-case'],
    'scope-empty': [2, 'never'],
    'subject-empty': [2, 'never'],
    'header-max-length': [2, 'always', 100],
  },
};
