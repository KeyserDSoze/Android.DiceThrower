import test from 'node:test';
import assert from 'node:assert/strict';
import { instantiateTemplate, newPart, normalFormula, validateDocument, envelope, documentFromEnvelope,
  encodeToken, decodeToken, importAsCopy, newStyle } from './character.js';
import {
  ACTION_KINDS, APPEARANCE_MODES, newAction, newActivationGroup, newEffect,
  isPartUsed, removePartSafely, setPartStyle, setDieStyle, setActionKind, SYSTEM_STYLES,
} from './guided.js';

function configuredCharacter() {
  const doc = instantiateTemplate(0);
  const attack = doc.rolls[0];
  const a = newPart(); a.name = 'Attack'; a.expression = '1d20+{Strength}';
  const b = newPart(); b.name = 'Damage'; b.expression = '1d8+{Strength}'; b.includeInDoubleRoll = true;
  attack.subgroups = [a, b]; attack.expression = normalFormula(attack.subgroups);
  attack.doubleRollEnabled = true;
  return { doc, attack, a, b };
}

test('guided effects use native JSON schema and round-trip condition OR/AND, all action types', () => {
  const { doc, attack, a, b } = configuredCharacter();
  const effect = newEffect('BONUS', attack.subgroups);
  effect.name = 'Critical follow-up';
  effect.activationGroups[0].conditions[0].comparison = 'EQUAL';
  effect.activationGroups[0].conditions[0].threshold = '20';
  effect.activationGroups[0].conditions.push({
    id:crypto.randomUUID(), source:'VARIABLE', partId:null, variableName:'Strength', scope:'TOTAL',
    comparison:'GREATER_OR_EQUAL', threshold:'2',
  });
  effect.activationGroups.push(newActivationGroup(attack.subgroups));
  effect.activationGroups[1].conditions[0].partId = b.id;
  effect.actions = ACTION_KINDS.map(kind => ({
    ...newAction(attack.subgroups), kind, targetPartId:b.id,
    expression:kind === 'REROLL' || kind === 'ROLL_AFTER' ? '' : '2',
  }));
  attack.effects.push(effect);
  const snapshot = structuredClone(doc);
  validateDocument(doc);
  assert.deepEqual(documentFromEnvelope(envelope(doc)), doc);
  assert.deepEqual(decodeToken(encodeToken(doc)), snapshot);
  const copy = importAsCopy(doc);
  assert.notEqual(copy.rolls[0].effects[0].id, effect.id);
  assert.equal(copy.rolls[0].effects[0].activationGroups[0].conditions[0].partId, copy.rolls[0].subgroups[0].id);
  assert.equal(copy.rolls[0].effects[0].actions[0].targetPartId, copy.rolls[0].subgroups[1].id);
  assert.deepEqual(copy.rolls[0].effects[0].actions.map(x => x.kind), ACTION_KINDS);
  assert.equal(copy.rolls[0].subgroups[1].includeInDoubleRoll, true);
});

test('the five appearance modes and system/custom styles survive transfer', () => {
  const {doc,attack,a} = configuredCharacter();
  const style = newStyle(); doc.diceStyles.push(style);
  doc.character.defaultDiceStyleId = SYSTEM_STYLES[0].id;
  doc.character.secondaryDiceStyleId = style.id;
  for (const mode of APPEARANCE_MODES) {
    attack.diceAppearance = {
      mode, styleId:style.id, subgroupStyleIds:[{subgroupId:a.id,styleId:style.id}],
      perDieStyleIds:[{slot:'0:0',styleId:SYSTEM_STYLES[1].id}],
      randomStyleIds:[style.id,SYSTEM_STYLES[0].id],
    };
    assert.equal(decodeToken(encodeToken(doc)).rolls[0].diceAppearance.mode, mode);
  }
  const copy = importAsCopy(doc);
  assert.notEqual(copy.character.secondaryDiceStyleId, style.id);
  assert.equal(copy.rolls[0].diceAppearance.subgroupStyleIds[0].subgroupId,copy.rolls[0].subgroups[0].id);
  assert.equal(copy.rolls[0].diceAppearance.subgroupStyleIds[0].styleId,copy.diceStyles[0].id);
  assert.equal(copy.rolls[0].diceAppearance.perDieStyleIds[0].styleId,SYSTEM_STYLES[1].id);
});

test('the guided style setters preserve unknown existing fields without mutation', () => {
  const original = {mode:'PER_DIE', styleId:'preset:arcane-blue',
    perDieStyleIds:[{slot:'1:0',styleId:'preset:lunar-frost'}], subgroupStyleIds:[], futureSetting:123};
  const a = setPartStyle(original,'part-one','preset:inferno-forged');
  const b = setDieStyle(a,'0:0','preset:emerald-aether');
  const c = setPartStyle(b,'part-one',null);
  assert.equal(original.subgroupStyleIds.length,0);
  assert.equal(c.subgroupStyleIds.length,0);
  assert.equal(c.perDieStyleIds.length,2);
  assert.equal(c.futureSetting,123);
});

test('referenced Parts cannot be removed; unrelated appearance styles are cleaned', () => {
  const { doc, attack, a, b } = configuredCharacter();
  attack.effects = [newEffect('BONUS', attack.subgroups)];
  attack.diceAppearance = {mode:'PER_DIE',subgroupStyleIds:[
    {subgroupId:a.id,styleId:SYSTEM_STYLES[0].id},
    {subgroupId:b.id,styleId:SYSTEM_STYLES[1].id},
  ]};
  assert.equal(isPartUsed(attack,a.id),true);
  assert.throws(() => removePartSafely(attack,a.id,normalFormula), /referenced/);
  assert.equal(attack.subgroups.length,2);
  removePartSafely(attack,b.id,normalFormula);
  assert.equal(attack.subgroups.length,1);
  assert.equal(attack.diceAppearance.subgroupStyleIds.length,1);
  validateDocument(doc);
});

test('broken Part references, variable names, style slots, and action enum are rejected', () => {
  const {doc,attack} = configuredCharacter();
  const effect = newEffect('MALUS',attack.subgroups);
  attack.effects.push(effect);
  const copy = () => structuredClone(doc);
  let broken = copy();
  broken.rolls[0].effects[0].actions[0].targetPartId = 'missing';
  assert.throws(() => validateDocument(broken),/missing Part/);
  broken = copy();
  broken.rolls[0].effects[0].activationGroups[0].conditions[0].source = 'VARIABLE';
  broken.rolls[0].effects[0].activationGroups[0].conditions[0].variableName = 'missing';
  assert.throws(() => validateDocument(broken),/missing variable/);
  broken = copy(); broken.rolls[0].effects[0].actions[0].kind = 'NOT_SUPPORTED';
  assert.throws(() => validateDocument(broken),/Unsupported action/);
  broken = copy(); broken.rolls[0].diceAppearance = {mode:'PER_DIE', perDieStyleIds:[{slot:'oops',styleId:SYSTEM_STYLES[0].id}]};
  assert.throws(() => validateDocument(broken),/die slot/);
});

test('switching between numeric and sampling effects uses the same semantics as Android', () => {
  const action = newAction([{id:'a'}]);
  const reroll = setActionKind(action,'REROLL');
  assert.equal(reroll.expression,'');
  const add = setActionKind(reroll,'ADD');
  assert.equal(add.expression,'1');
  assert.equal(action.kind,'ADD');
});
