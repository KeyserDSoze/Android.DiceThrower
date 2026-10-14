/**
 * Non-random, read-only rendering of a Roll formula at the selected character
 * level. Mirrors RollFormulaResolver's variable substitution and rule repeat
 * counts. This is a human-readable expression preview, not a dice execution or
 * an Android serialization change.
 */
export function resolveLevelTokens(expression, doc, level = doc.character.level) {
  const variables = new Map([['level', level]]);
  for (const modifier of doc.modifiers || []) {
    variables.set(modifier.name.trim().toLowerCase(), modifier.value);
  }
  const expanded = expression.replace(/(?<=[0-9)}])(?=\{)/g, 'x')
    .replace(/\{([^{}]+)\}/g, (match, name) => {
      const key = name.trim().toLowerCase();
      if (variables.has(key)) return String(variables.get(key));
      if (key.startsWith('levelstep:')) {
        const thresholds = key.slice('levelstep:'.length).split(':').map(Number);
        if (thresholds.every(Number.isInteger)) {
          return String(thresholds.filter(threshold => level >= threshold).length);
        }
      }
      // Advanced Part-ID references must be evaluated by the Android engine.
      return match;
    });
  let normalized = expanded;
  while (/\+[-+]|-[+-]/.test(normalized)) {
    normalized = normalized.replaceAll('+-', '-').replaceAll('-+', '-')
      .replaceAll('++', '+').replaceAll('--', '+');
  }
  return normalized;
}

export function rollLevelPreview(doc, roll, level = doc.character.level) {
  const base = resolveLevelTokens(roll.expression, doc, level);
  const additions = (roll.levelRules || []).map(rule => {
    const count = rule.kind === 'FROM_LEVEL' ? Number(level >= rule.trigger)
      : rule.kind === 'EVERY_LEVELS'
        ? Math.min(rule.maxRepeats ?? Infinity, rule.startLevel == null
          ? Math.floor(level / rule.trigger)
          : level < rule.startLevel ? 0
            : 1 + Math.floor((level - rule.startLevel) / rule.trigger))
        : 0;
    return { rule, count, expression: resolveLevelTokens(rule.expression, doc, level) };
  });
  const segments = [base];
  for (const {count, expression} of additions) {
    if (count === 1) segments.push('(' + expression + ')');
    else if (count > 1) segments.push(count + ' × (' + expression + ')');
  }
  const formula = segments.join(' + ');
  const hasLevelRules = additions.length > 0;
  const hasLevelVariables = /\{(?:level\}|levelstep:)/i.test(roll.expression) ||
    (roll.subgroups || []).some(part => /\{(?:level\}|levelstep:)/i.test(part.expression));
  return {
    level,
    formula,
    base,
    additions,
    dynamic: hasLevelRules || hasLevelVariables,
    available: level >= (roll.minimumLevel || 1) && roll.enabled !== false,
  };
}
