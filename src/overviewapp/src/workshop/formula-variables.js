/**
 * Built-in character-level token is reserved by both the Android resolver and
 * the portable Workshop validator. It should be offered in the editor rather
 * than mistaken for a modifier users must create themselves.
 */
export function formulaVariables(modifiers = []) {
  return ['level', ...modifiers.map(item => item.name).filter(
    name => typeof name === 'string' && name.trim().toLowerCase() !== 'level',
  )];
}

/**
 * Adding a variable from the picker is explicitly additive; anyone wanting
 * a dice count such as {level}d6 can still type it directly in the field.
 */
export function appendFormulaVariable(expression, name) {
  if (!formulaVariableName(name)) throw new Error('Invalid formula variable');
  const raw = String(expression ?? '').trim();
  const token = '{' + name + '}';
  if (!raw) return token;
  return /[+\-*/xX(]$/.test(raw) ? raw + token : raw + '+' + token;
}

function formulaVariableName(name) {
  return typeof name === 'string' && /^[\p{L}\p{M}\w ]+$/u.test(name) &&
    name.trim() === name;
}
