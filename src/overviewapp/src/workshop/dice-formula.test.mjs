import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DICE_EXPRESSION_LIMITS, parseDiceExpression, resolveDiceTemplate, resolveRollFormula } from './dice-formula.js';
import { DiceFormulaError, DICE_FORMULA_ERROR_REASONS } from './formula-errors.js';
import { newCharacter, newRoll, newPart, newLevelRule, normalFormula, validateDocument,
  envelope, documentFromEnvelope, androidHandoff, withImportPreferences, instantiateTemplate } from './character.js';

const corpus = JSON.parse(readFileSync(new URL('../../../app/src/test/resources/dice-expression-parity.json', import.meta.url), 'utf8'));
const expression = record => '('.repeat(record.wrap || 0) + '-'.repeat(record.unary || 0) +
  Array(record.repeat || 1).fill(record.expression).join(record.separator || '') +
  ')'.repeat(record.wrap || 0) + ' '.repeat(record.padding || 0);
function check(record, parse) {
  if (!record.valid) {
    assert.throws(parse, error => error instanceof DiceFormulaError &&
      error.code === 'dice-formula-invalid' && DICE_FORMULA_ERROR_REASONS.includes(error.reason));
    return;
  }
  const parsed = parse();
  for (const key of ['source', 'diceCount', 'termCount']) {
    if (key in record) assert.equal(parsed[key], record[key], record.name);
  }
}

test('validation budgets are shared with the Android corpus', () => {
  assert.equal(corpus.version, 1);
  assert.deepEqual(DICE_EXPRESSION_LIMITS, corpus.limits);
});
for (const record of corpus.expressions) test('native numeric parity: ' + record.name,
  () => check(record, () => parseDiceExpression(expression(record))));
for (const record of corpus.templates) test('native template parity: ' + record.name,
  () => check(record, () => resolveDiceTemplate(expression(record), record.level || 1, record.modifiers || [])));
for (const record of corpus.rolls) test('native expanded-roll parity: ' + record.name, () => {
  const roll = { ...record, expression: expression(record),
    subgroups: (record.subgroups || []).map(part => ({ ...part, expression: expression(part) })),
    levelRules: (record.levelRules || []).map(rule => ({ ...rule, expression: expression(rule) })),
  };
  check(record, () => resolveRollFormula(roll, record.level || 1, record.modifiers || []));
});

function document(expression = '1d6') {
  const doc = newCharacter('Formula parity');
  doc.rolls.push({ ...newRoll(doc.character.id), expression });
  return doc;
}

test('invalid roll syntax is rejected before sharing, decoding or app handoff without mutating drafts', () => {
  for (const expression of ['1d7', '1d6/2', '1d6+', '1d6*65536*65536', ' '.repeat(5)]) {
    const doc = document(expression);
    const before = structuredClone(doc);
    assert.throws(() => validateDocument(doc), /Invalid dice expression/);
    assert.throws(() => envelope(doc), /Invalid dice expression/);
    const packet = envelope(document());
    packet.data.rolls[0].expression = expression;
    assert.throws(() => documentFromEnvelope(packet), /Invalid dice expression/);
    assert.equal(androidHandoff(doc).reason, 'invalid');
    assert.deepEqual(doc, before);
  }
});

test('native-length formulas and multiplication glyphs can be transferred unchanged', () => {
  for (const expression of ['2×1d6', '1d6' + ' '.repeat(4093)]) {
    const doc = document(expression);
    assert.deepEqual(documentFromEnvelope(envelope(doc)), doc);
  }
});

test('dynamic counts and combined periodic rules are checked at the recipient level', () => {
  const doc = document('{level}d6');
  doc.character.level = 100;
  validateDocument(doc);
  assert.throws(() => withImportPreferences(doc, 'Higher level', 101), /dice count/);
  assert.equal(doc.character.level, 100);
  const periodic = document();
  periodic.character.level = 9999;
  periodic.rolls[0].levelRules.push({ ...newLevelRule(), kind: 'EVERY_LEVELS', trigger: 1 });
  assert.throws(() => validateDocument(periodic), /500 dice/);
  periodic.rolls[0].levelRules[0].maxRepeats = 40;
  validateDocument(periodic);
});

test('dormant Parts and inactive rules still need valid templates; omitted initial flags stay compatible', () => {
  const doc = document();
  const roll = doc.rolls[0];
  const part = newPart();
  delete part.includeInNormalRoll;
  roll.subgroups = [part];
  roll.expression = normalFormula(roll.subgroups);
  assert.equal(roll.expression, '(1d20)');
  validateDocument(doc);
  const support = { ...newPart(), includeInNormalRoll: false, expression: '101d6' };
  roll.subgroups.push(support);
  assert.throws(() => validateDocument(doc), /dice count/);
  support.expression = '1d6';
  roll.levelRules.push({ ...newLevelRule(), trigger: 9999, expression: '1d7' });
  assert.throws(() => validateDocument(doc), /unsupported die/);
});

test('validation never samples dice and leaves effect arithmetic grammar separate', () => {
  const random = Math.random;
  Math.random = () => { throw new Error('Validation must not sample dice'); };
  try {
    assert.equal(parseDiceExpression('2x(1d6+3)').diceCount, 1);
    const doc = instantiateTemplate(0);
    const effect = doc.rolls.find(roll => roll.name === 'Longsword').effects[0];
    effect.activationGroups[0].conditions[0].threshold = 'f({level}/2)';
    effect.actions[0].kind = 'ADD';
    effect.actions[0].expression = 'c({level}/2)';
    validateDocument(doc);
  } finally { Math.random = random; }
});
