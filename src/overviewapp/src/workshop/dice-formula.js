/** RNG-free validation matching Android DiceExpression / RollFormulaResolver, not an evaluator. */
export const DICE_EXPRESSION_LIMITS = Object.freeze({
  length: 4096, nesting: 32, dicePerTerm: 100, totalDice: 500, terms: 1000, integer: 2147483647,
});
const SUPPORTED_SIDES = new Set([2, 3, 4, 6, 8, 10, 12, 20, 100]);
// Kotlin Char.isWhitespace includes Unicode separators and ASCII 0x1c–0x1f, but not BOM.
const whitespace = /[\u0009-\u000d\u001c-\u001f\p{Z}]/u;
const digit = value => value != null && value >= '0' && value <= '9';
const requireFormula = (condition, message) => {
  if (!condition) throw new Error('Invalid dice expression: ' + message);
};
function trimNative(value) {
  let start = 0, end = value.length;
  while (start < end && whitespace.test(value[start])) start++;
  while (end > start && whitespace.test(value[end - 1])) end--;
  return value.slice(start, end);
}

export function parseDiceExpression(raw) {
  requireFormula(typeof raw === 'string' && raw.length <= DICE_EXPRESSION_LIMITS.length,
    'use at most 4096 characters');
  const tokens = [];
  const operators = { d: 'die', D: 'die', '+': '+', '-': '-', '*': '*', x: '*', X: '*', '×': '*', '(': '(', ')': ')' };
  for (let index = 0; index < raw.length;) {
    if (whitespace.test(raw[index])) { index++; continue; }
    const start = index;
    if (digit(raw[index])) {
      while (digit(raw[index])) index++;
      tokens.push({ type: 'number', text: raw.slice(start, index) });
    } else {
      const type = operators[raw[index]];
      requireFormula(type, 'unexpected character near position ' + index);
      tokens.push({ type, text: raw[index++] });
    }
  }
  requireFormula(tokens.length > 0, 'a number or die is required');
  let cursor = 0, depth = 0, diceCount = 0, termCount = 0;
  const peek = () => tokens[cursor]?.type;
  const integer = text => {
    const value = Number(text);
    requireFormula(Number.isInteger(value) && value <= DICE_EXPRESSION_LIMITS.integer, 'integer range exceeded');
    return value;
  };
  const bounded = (magnitude, exact, hasDice) => {
    requireFormula(magnitude <= DICE_EXPRESSION_LIMITS.integer, 'integer range exceeded');
    return { magnitude, exact, hasDice };
  };
  const combine = (left, right, operation) => {
    if (operation === '*') requireFormula(!(left.hasDice && right.hasDice), 'dice-by-dice multiplication is not supported');
    const exact = left.exact != null && right.exact != null
      ? operation === '*' ? left.exact * right.exact
        : operation === '+' ? left.exact + right.exact : left.exact - right.exact
      : null;
    const magnitude = exact != null ? Math.abs(exact)
      : operation === '*' ? left.magnitude * right.magnitude : left.magnitude + right.magnitude;
    // Every child is already within Int range. Arithmetic that can be accepted is therefore
    // exact in JS; products outside that range are rejected before they can be used again.
    return bounded(magnitude, exact, left.hasDice || right.hasDice);
  };
  const nested = run => {
    requireFormula(++depth <= DICE_EXPRESSION_LIMITS.nesting, 'nesting exceeds 32 levels');
    try { return run(); } finally { depth--; }
  };
  function term() {
    requireFormula(++termCount <= DICE_EXPRESSION_LIMITS.terms, 'expression exceeds 1000 terms');
    const countText = peek() === 'number' ? tokens[cursor++].text : null;
    if (peek() !== 'die') {
      requireFormula(countText != null, 'a number or die is required');
      const value = integer(countText);
      return bounded(value, value, false);
    }
    cursor++;
    requireFormula(peek() === 'number', 'die sides are required');
    const sides = integer(tokens[cursor++].text);
    const count = countText == null ? 1 : integer(countText);
    requireFormula(count >= 1 && count <= DICE_EXPRESSION_LIMITS.dicePerTerm, 'dice count must be between 1 and 100');
    requireFormula(SUPPORTED_SIDES.has(sides), 'unsupported die d' + sides);
    diceCount += count;
    requireFormula(diceCount <= DICE_EXPRESSION_LIMITS.totalDice, 'expression exceeds 500 dice');
    return bounded(count * sides, null, true);
  }
  function primary() {
    if (peek() === '(') return nested(() => {
      cursor++;
      const inner = sum();
      requireFormula(peek() === ')', 'missing closing parenthesis');
      cursor++;
      return inner;
    });
    requireFormula(peek() === 'number' || peek() === 'die', 'expected a number or die');
    return term();
  }
  function unary() {
    if (peek() !== '+' && peek() !== '-') return primary();
    return nested(() => {
      const negative = tokens[cursor++].type === '-';
      const child = unary();
      return negative ? { ...child, exact: child.exact == null ? null : -child.exact } : child;
    });
  }
  function product() {
    let result = unary();
    while (peek() === '*') { cursor++; result = combine(result, unary(), '*'); }
    return result;
  }
  function sum() {
    let result = product();
    while (peek() === '+' || peek() === '-') {
      const operation = tokens[cursor++].type;
      result = combine(result, product(), operation);
    }
    return result;
  }
  sum();
  requireFormula(cursor === tokens.length, 'separate numbers with an operator');
  return { source: tokens.map(token => token.text).join(''), diceCount, termCount };
}

function variableMap(level, modifiers) {
  requireFormula(Number.isInteger(level) && level >= 1 && level <= DICE_EXPRESSION_LIMITS.integer, 'invalid character level');
  const variables = new Map([['level', level]]);
  for (const modifier of modifiers) {
    requireFormula(typeof modifier.name === 'string', 'invalid modifier name');
    const key = trimNative(modifier.name).toLowerCase();
    requireFormula(key && !variables.has(key), 'duplicate or reserved modifier name');
    requireFormula(Number.isInteger(modifier.value) && modifier.value >= -2147483648 &&
      modifier.value <= DICE_EXPRESSION_LIMITS.integer, 'invalid modifier value');
    variables.set(key, modifier.value);
  }
  return variables;
}
function resolveTemplate(raw, variables) {
  requireFormula(typeof raw === 'string' && raw.length <= DICE_EXPRESSION_LIMITS.length, 'use at most 4096 characters');
  let resolved = raw.replace(/([0-9)}])(?=\{)/g, '$1x').replace(/\{([^{}]+)\}/g, (_, name) => {
    const key = trimNative(name).toLowerCase();
    if (variables.has(key)) return String(variables.get(key));
    if (key.startsWith('levelstep:')) {
      const steps = key.slice('levelstep:'.length).split(':');
      requireFormula(steps.length >= 1 && steps.length <= 20 && steps.every((step, index) =>
        /^[1-9][0-9]*$/.test(step) && Number(step) >= 2 && Number(step) <= 9999 &&
        (index === 0 || Number(step) > Number(steps[index - 1]))), 'invalid level milestones');
      return String(steps.filter(step => variables.get('level') >= Number(step)).length);
    }
    throw new Error('Missing variable or invalid level milestone: ' + name);
  });
  while (/\+[-+]|-[+-]/.test(resolved)) {
    resolved = resolved.replaceAll('+-', '-').replaceAll('-+', '-').replaceAll('++', '+').replaceAll('--', '+');
  }
  return parseDiceExpression(resolved);
}

export function resolveDiceTemplate(raw, level, modifiers = []) {
  return resolveTemplate(raw, variableMap(level, modifiers));
}

/** Validate the actual current-level expansion, without replacing persisted templates or Parts. */
export function resolveRollFormula(roll, level, modifiers = []) {
  const variables = variableMap(level, modifiers);
  const parts = roll.subgroups || [];
  const active = parts.filter(part => part.includeInNormalRoll !== false);
  requireFormula(parts.length === 0 || active.length > 0, 'at least one initial Part is required');
  const base = parts.length === 0 ? resolveTemplate(roll.expression, variables)
    : parseDiceExpression(active.map((part, index) => {
      const expression = '(' + resolveTemplate(part.expression, variables).source + ')';
      return index === 0 && part.operator !== 'SUBTRACT' ? expression
        : (part.operator === 'SUBTRACT' ? '-' : '+') + expression;
    }).join(''));
  const pieces = [{ source: base.source, count: 1 }];
  let diceCount = base.diceCount, termCount = base.termCount, length = base.source.length;
  for (const rule of roll.levelRules || []) {
    requireFormula(Number.isInteger(rule.trigger) && rule.trigger >= 1, 'invalid level rule trigger');
    requireFormula(rule.kind === 'FROM_LEVEL' || rule.kind === 'EVERY_LEVELS', 'invalid level rule kind');
    const count = rule.kind === 'FROM_LEVEL' ? Number(level >= rule.trigger)
      : Math.min(rule.maxRepeats ?? Infinity, rule.startLevel == null ? Math.floor(level / rule.trigger)
        : level < rule.startLevel ? 0 : 1 + Math.floor((level - rule.startLevel) / rule.trigger));
    if (count <= 0) continue;
    const piece = resolveTemplate(rule.expression, variables);
    diceCount += piece.diceCount * count;
    termCount += piece.termCount * count;
    length += (piece.source.length + 1) * count;
    requireFormula(diceCount <= DICE_EXPRESSION_LIMITS.totalDice, 'level rules add more than 500 dice');
    requireFormula(termCount <= DICE_EXPRESSION_LIMITS.terms && length <= DICE_EXPRESSION_LIMITS.length,
      'level rules exceed the expression size budget');
    pieces.push({ source: piece.source, count });
  }
  if (pieces.length === 1) return base;
  let expression = '';
  for (const piece of pieces) for (let repetition = 0; repetition < piece.count; repetition++) {
    if (expression && !piece.source.startsWith('-')) expression += '+';
    expression += piece.source;
  }
  return parseDiceExpression(expression);
}
