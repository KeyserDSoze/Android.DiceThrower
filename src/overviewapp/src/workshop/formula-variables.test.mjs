import test from 'node:test';
import assert from 'node:assert/strict';
import { instantiateTemplate, normalFormula, validateDocument, envelope, documentFromEnvelope } from './character.js';
import { newEffect } from './guided.js';
import { formulaVariables, appendFormulaVariable } from './formula-variables.js';
import { rollLevelPreview } from './roll-level-preview.js';

test('built-in level is selectable alongside character modifiers, without persisting a fake modifier', () => {
  const wizard = instantiateTemplate(2);
  assert.equal(formulaVariables(wizard.modifiers)[0], 'level');
  assert.ok(formulaVariables(wizard.modifiers).includes('Intelligence'));
  assert.ok(formulaVariables(wizard.modifiers).includes('Proficiency'));
  assert.equal(wizard.modifiers.some(m=>m.name.toLowerCase()==='level'), false);
  assert.equal(formulaVariables([{name:'level'}, {name:'Strength'}]).filter(n=>n==='level').length, 1);
});

test('picker appends a genuine formula token, not an already resolved constant', () => {
  assert.equal(appendFormulaVariable('1d6', 'level'), '1d6+{level}');
  assert.equal(appendFormulaVariable('1d8 + ', 'level'), '1d8 +{level}');
  assert.equal(appendFormulaVariable('', 'level'), '{level}');
  assert.equal(appendFormulaVariable('1d20+', 'Intelligence'), '1d20+{Intelligence}');
  assert.throws(() => appendFormulaVariable('1d6', 'bad{field}'), /Invalid formula variable/);
  const wizard = instantiateTemplate(2);
  const rolled = wizard.rolls.find(r=>r.name==='Fire Bolt · 1d10');
  assert.ok(rolled);
  const before = structuredClone(wizard);
  const attack = rolled.subgroups[0];
  attack.expression = appendFormulaVariable(attack.expression, 'level');
  rolled.expression = normalFormula(rolled.subgroups);
  validateDocument(wizard);
  const lv5 = rollLevelPreview(wizard, rolled, 5);
  const lv17 = rollLevelPreview(wizard, rolled, 17);
  assert.match(lv5.formula, /\+5\)/);
  assert.match(lv17.formula, /\+17\)/);
  assert.equal(before.rolls.find(r=>r.id===rolled.id).subgroups[0].expression.includes('{level}'),false);
  assert.deepEqual(documentFromEnvelope(envelope(wizard)),wizard);
});

test('web guided effect VARIABLE conditions accept reserved character level, like native Android', () => {
  const wizard = instantiateTemplate(2);
  const roll = wizard.rolls.find(r=>r.name==='Fire Bolt · 1d10');
  assert.ok(roll);
  const effect = newEffect('BONUS',roll.subgroups);
  const condition = effect.activationGroups[0].conditions[0];
  condition.source = 'VARIABLE';
  condition.variableName = 'level';
  condition.partId = null;
  condition.scope = 'TOTAL';
  condition.comparison = 'GREATER_OR_EQUAL';
  condition.threshold = '5';
  roll.effects.push(effect);
  validateDocument(wizard);
  assert.equal(documentFromEnvelope(envelope(wizard)).rolls.find(r=>r.id===roll.id)
    .effects.at(-1).activationGroups[0].conditions[0].variableName,'level');
  const broken = structuredClone(wizard);
  broken.rolls.find(r=>r.id===roll.id).effects.at(-1)
    .activationGroups[0].conditions[0].variableName='unknown';
  assert.throws(()=>validateDocument(broken),/missing variable/);
});
