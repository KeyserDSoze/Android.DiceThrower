import test from 'node:test';
import assert from 'node:assert/strict';
import { DiceFormulaError, DICE_FORMULA_ERROR_REASONS, formatDiceFormulaError } from './formula-errors.js';
import { parseDiceExpression, resolveDiceTemplate, resolveRollFormula } from './dice-formula.js';
import { newCharacter, newRoll, envelope, documentFromEnvelope, androidHandoff, saveDocument } from './character.js';

for (const reason of DICE_FORMULA_ERROR_REASONS) test('formula error has EN/IT text: ' + reason, () => {
  const error = new DiceFormulaError(reason, { limit: 500, position: 2, sides: 7, name: 'Strength' });
  assert.ok(error instanceof Error);
  assert.equal(error.code, 'dice-formula-invalid');
  assert.equal(error.reason, reason);
  const english = formatDiceFormulaError(error, 'en');
  const italian = formatDiceFormulaError(error, 'it');
  assert.match(english, /^Invalid dice expression: /);
  assert.match(italian, /^Formula dei dadi non valida: /);
  assert.notEqual(english, italian);
  assert.ok(!/undefined|NaN|\?/.test(english + italian), reason);
  assert.equal(error.message, english, 'non-UI callers retain a readable English message');
});

const roll = (expression = '1d6', levelRules = [], subgroups = []) => ({ expression, levelRules, subgroups });
const cases = [
  ['length', () => parseDiceExpression('1'.repeat(4097)), { limit: 4096 }],
  ['required', () => parseDiceExpression('   '), {}],
  ['character', () => parseDiceExpression('٢d6'), { position: 0 }],
  ['integer', () => parseDiceExpression('1d6*65536*65536'), {}],
  ['dice-product', () => parseDiceExpression('1d6*1d4'), {}],
  ['nesting', () => parseDiceExpression('('.repeat(33) + '1' + ')'.repeat(33)), { limit: 32 }],
  ['terms', () => parseDiceExpression(Array(1001).fill('1').join('+')), { limit: 1000 }],
  ['dice-count', () => parseDiceExpression('101d6'), { limit: 100 }],
  ['sides', () => parseDiceExpression('1d7'), { sides: 7 }],
  ['total-dice', () => parseDiceExpression('100d2+100d3+100d4+100d6+100d8+1d20'), { limit: 500 }],
  ['parenthesis', () => parseDiceExpression('(1d6'), {}],
  ['syntax', () => parseDiceExpression('1d6+'), {}],
  ['separated-numbers', () => parseDiceExpression('2 4'), {}],
  ['level', () => resolveDiceTemplate('1d6', 0), {}],
  ['modifier-name', () => resolveDiceTemplate('1d6', 1, [{ name: 7, value: 1 }]), {}],
  ['duplicate-variable', () => resolveDiceTemplate('1d6', 1, [{ name: 'LEVEL', value: 1 }]), {}],
  ['modifier-value', () => resolveDiceTemplate('1d6', 1, [{ name: 'Strength', value: 1.5 }]), {}],
  ['milestones', () => resolveDiceTemplate('{levelstep:05}', 1), {}],
  ['variable', () => resolveDiceTemplate('1d6+{Missing}', 1), { name: 'Missing' }],
  ['initial-part', () => resolveRollFormula(roll('1d6', [], [{ expression: '1d6', includeInNormalRoll: false }]), 1), {}],
  ['rule-trigger', () => resolveRollFormula(roll('1d6', [{ kind: 'FROM_LEVEL', trigger: 0, expression: '1' }]), 1), {}],
  ['rule-kind', () => resolveRollFormula(roll('1d6', [{ kind: 'SOMETIMES', trigger: 1, expression: '1' }]), 1), {}],
  ['rule-dice', () => resolveRollFormula(roll('1d6', [{ kind: 'EVERY_LEVELS', trigger: 1, expression: '1d6' }]), 9999), { limit: 500 }],
  ['rule-size', () => resolveRollFormula(roll(Array(1000).fill('1').join('+'), [{ kind: 'FROM_LEVEL', trigger: 1, expression: '1' }]), 1), {}],
];
for (const [reason, validate, details] of cases) test('parser emits stable reason: ' + reason, () => {
  assert.throws(validate, error => {
    assert.ok(error instanceof DiceFormulaError);
    assert.equal(error.reason, reason);
    assert.deepEqual(error.details, details);
    assert.ok(Object.isFrozen(error.details));
    return true;
  });
});

test('all supported diagnostic reasons are exercised by actual validation paths', () => {
  assert.deepEqual(new Set(cases.map(([reason]) => reason)), new Set(DICE_FORMULA_ERROR_REASONS));
});

test('localization is selected from reason/parameters, never by reparsing an English message', () => {
  const error = new DiceFormulaError('sides', { sides: 7 });
  error.message = 'This wording may change independently';
  assert.match(formatDiceFormulaError(error, 'it-IT'), /d7 non è supportato/);
  assert.equal(formatDiceFormulaError(error, 'IT-it'), formatDiceFormulaError(error, 'it'));
  assert.equal(formatDiceFormulaError(error, 'fr'), formatDiceFormulaError(error, 'en'));
});

test('unrelated errors keep their existing handling and unknown reasons have a safe fallback', () => {
  assert.equal(formatDiceFormulaError(new Error('Other problem'), 'it'), null);
  assert.equal(formatDiceFormulaError({ code: 'storage-quota' }, 'it'), null);
  assert.equal(formatDiceFormulaError(null, 'it'), null);
  for (const reason of ['future-reason', '__proto__', 'prefix']) {
    assert.equal(formatDiceFormulaError({ code: 'dice-formula-invalid', reason }, 'it'),
      'Formula dei dadi non valida: controlla la formula e le sue variabili');
  }
});

test('parameters are bounded primitives and never retain the complete source or document', () => {
  const parameters = { name: 'x'.repeat(1000), limit: 4096, position: 0, sides: 7,
    source: 'private expression', document: { private: true } };
  const error = new DiceFormulaError('variable', parameters);
  parameters.name = 'changed';
  assert.deepEqual(error.details, { limit: 4096, position: 0, sides: 7, name: 'x'.repeat(160) });
  assert.ok(!error.message.includes('private expression'));
  assert.deepEqual(new DiceFormulaError('length', { limit: Infinity, name: {} }).details, {});
  assert.deepEqual(new DiceFormulaError('length', { limit: Number.MAX_SAFE_INTEGER + 1 }).details, {});
});

test('Save, share, decoded imports and Android handoff preserve coded formula diagnostics', () => {
  const doc = newCharacter('Coded error QA');
  doc.rolls.push({ ...newRoll(doc.character.id), expression: '1d7' });
  const before = structuredClone(doc);
  const invalid = error => error instanceof DiceFormulaError && error.reason === 'sides';
  assert.throws(() => saveDocument(doc), invalid);
  assert.throws(() => envelope(doc), invalid);
  const handoff = androidHandoff(doc);
  assert.equal(handoff.reason, 'invalid');
  assert.ok(invalid(handoff.error));
  doc.rolls[0].expression = '1d6';
  const packet = envelope(doc);
  doc.rolls[0].expression = '1d7';
  packet.data.rolls[0].expression = '1d7';
  assert.throws(() => documentFromEnvelope(packet), invalid);
  assert.deepEqual(doc, before);
});
