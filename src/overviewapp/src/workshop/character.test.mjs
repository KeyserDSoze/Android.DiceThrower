import test from 'node:test';
import assert from 'node:assert/strict';
import {
  templates, instantiateTemplate, validateDocument, envelope, documentFromEnvelope,
  encodeToken, decodeToken, importAsCopy, normalFormula, newPart, newRoll, shareUrl,
  canUpgradeLegacyStarterWeaponRolls, upgradeLegacyStarterWeaponRolls,
} from './character.js';

test('all ten starter templates validate and survive the versioned compressed link', () => {
  assert.equal(templates.length, 10);
  for (const { index } of templates) {
    const original = instantiateTemplate(index);
    validateDocument(original);
    const decoded = decodeToken(encodeToken(original));
    assert.deepEqual(decoded, original);
  }
});

test('level-one fighter has core rolls, no premature level-two feature', () => {
  const fighter = instantiateTemplate(0);
  assert.equal(fighter.character.level, 1);
  assert.equal(fighter.modifiers.find(m => m.name === 'Strength').value, 3);
  assert.ok(fighter.rolls.some(r => r.name.includes('Longsword') && r.expression.includes('Proficiency')));
  assert.ok(fighter.rolls.some(r => r.name.includes('Second Wind')));
  assert.ok(!fighter.rolls.some(r => r.name.includes('Action Surge')));
  assert.match(shareUrl(fighter, 'https://example.com/Android.DiceThrower/workshop/'), /^https:\/\/example.com/);
});

test('importing twice makes independent copies with fresh local IDs', () => {
  const original = instantiateTemplate(0);
  const first = importAsCopy(original);
  const second = importAsCopy(original);
  assert.notEqual(first.character.id, original.character.id);
  assert.notEqual(first.character.id, second.character.id);
  assert.deepEqual(first.rolls.map(x => x.name), original.rolls.map(x => x.name));
  assert.notEqual(first.groups[0].id, original.groups[0].id);
  assert.equal(first.rolls[0].groupId, first.groups[0].id);
});

test('subgroup formula and style IDs are remapped during browser import', () => {
  const x = instantiateTemplate(0);
  const part = newPart();
  // Use a simple starter Roll, not Longsword: its critical effect refers
  // to the original Attack and Damage Part IDs and must stay intact.
  const roll = x.rolls.find(r => r.name === 'Initiative');
  roll.subgroups = [part];
  roll.expression = normalFormula([part]);
  const decoded = documentFromEnvelope(envelope(x));
  const copy = importAsCopy(decoded);
  const imported = copy.rolls.find(r => r.name === 'Initiative');
  assert.notEqual(imported.subgroups[0].id, part.id);
  assert.equal(imported.expression, roll.expression);
  // Longsword's native D&D 5e rule still points to its remapped Parts.
  const longsword = copy.rolls.find(r => r.name === 'Longsword');
  assert.equal(longsword.effects[0].actions[0].targetPartId, longsword.subgroups[1].id);
});

test('invalid links, format versions and private history are rejected', () => {
  const x = instantiateTemplate(0);
  assert.throws(() => decodeToken('v2.deadbeef'), /Unsupported/);
  assert.throws(() => decodeToken('v1.!'), /Invalid/);
  const badVersion = envelope(x); badVersion.formatVersion = 99;
  assert.throws(() => documentFromEnvelope(badVersion), /Unsupported/);
  const withHistory = envelope(x); withHistory.data.logs = [{ id:'secret' }];
  assert.throws(() => documentFromEnvelope(withHistory), /private/);
});


test('fighter Longsword is a single Roll with independent Attack and Damage Parts', () => {
  const fighter = instantiateTemplate(0);
  const weapons = fighter.rolls.filter(r => /longsword/i.test(r.name));
  assert.equal(weapons.length, 1);
  const [roll] = weapons;
  assert.equal(roll.name, 'Longsword');
  assert.deepEqual(roll.subgroups.map(p => p.name), ['Attack', 'Damage']);
  assert.equal(roll.subgroups[0].expression, '1d20+{Strength}+{Proficiency}');
  assert.equal(roll.subgroups[1].expression, '1d8+{Strength}');
  assert.equal(roll.expression, '(1d20+{Strength}+{Proficiency})+(1d8+{Strength})');
  assert.equal(roll.effects.length, 1);
  assert.equal(roll.doubleRollEnabled, false);
});

test('D&D critical checks the natural d20 and samples one extra independent damage die', () => {
  const fighter = instantiateTemplate(0);
  const roll = fighter.rolls.find(r => r.name === 'Longsword');
  const [attack, damage] = roll.subgroups;
  const [effect] = roll.effects;
  assert.equal(effect.type, 'BONUS');
  assert.equal(effect.activationGroups.length, 1);
  const condition = effect.activationGroups[0].conditions[0];
  assert.equal(condition.source, 'PART');
  assert.equal(condition.partId, attack.id);
  assert.equal(condition.scope, 'DICE_ONLY'); // attack total 15 + 3 + 2 = 20 is NOT critical
  assert.equal(condition.comparison, 'EQUAL');
  assert.equal(condition.threshold, '20');
  const action = effect.actions[0];
  assert.equal(action.kind, 'ROLL_AFTER'); // not MULTIPLY or REPLACE
  assert.equal(action.targetPartId, damage.id);
  assert.equal(action.scope, 'DICE_ONLY'); // never reroll/add Strength
  assert.equal(action.expression, '1d8'); // new RNG sample -> (1d8 + 1d8) + 3
  assert.equal(fighter.modifiers.find(m => m.name === 'Strength').value, 3);
  assert.deepEqual(decodeToken(encodeToken(fighter)), fighter);
  const copied = importAsCopy(fighter);
  const newRoll = copied.rolls.find(r => r.name === 'Longsword');
  assert.equal(newRoll.effects[0].activationGroups[0].conditions[0].partId, newRoll.subgroups[0].id);
  assert.equal(newRoll.effects[0].actions[0].targetPartId, newRoll.subgroups[1].id);
  assert.notEqual(newRoll.effects[0].actions[0].targetPartId, damage.id);
  assert.deepEqual(documentFromEnvelope(envelope(copied)), copied);
});

test('other explicitly paired weapon templates use one Roll without assuming D&D rules', () => {
  for (const [templateIndex, name] of [[3, 'Bow'], [5, 'Sword']]) {
    const doc = instantiateTemplate(templateIndex);
    const roll = doc.rolls.find(r => r.name === name);
    assert.ok(roll, name);
    assert.deepEqual(roll.subgroups.map(p => p.name), ['Attack', 'Damage']);
    assert.equal(roll.effects.length, 0, 'generic fantasy must not silently use D&D critical hit rules');
    assert.deepEqual(decodeToken(encodeToken(doc)), doc);
  }
});

function oldFighterTemplate() {
  const doc = instantiateTemplate(0);
  const grouped = doc.rolls.find(r => r.name === 'Longsword');
  const [attack, damage] = grouped.subgroups;
  grouped.name = 'Longsword · attack';
  grouped.expression = attack.expression;
  grouped.subgroups = [];
  grouped.effects = [];
  const oldDamage = { ...newRoll(doc.character.id, grouped.groupId),
    name: 'Longsword · damage', expression: damage.expression, order: grouped.order + 1 };
  doc.rolls.splice(doc.rolls.indexOf(grouped) + 1, 0, oldDamage);
  return { doc, originalAttackId: grouped.id, originalDamageId: oldDamage.id };
}

test('explicit legacy Fighter upgrade keeps character/roll IDs and unrelated data', () => {
  const { doc, originalAttackId, originalDamageId } = oldFighterTemplate();
  doc.character.name = 'My own Aric'; // harmless personalization should be kept
  doc.character.level = 3;
  doc.rolls.push({ ...newRoll(doc.character.id), name: 'My homebrew Roll', expression: '3d6' });
  const original = structuredClone(doc);
  assert.equal(canUpgradeLegacyStarterWeaponRolls(doc), true);
  const upgraded = upgradeLegacyStarterWeaponRolls(doc);
  const sword = upgraded.rolls.find(r => r.name === 'Longsword');
  assert.equal(sword.id, originalAttackId);
  assert.ok(!upgraded.rolls.some(r => r.id === originalDamageId));
  assert.equal(upgraded.character.id, doc.character.id);
  assert.equal(upgraded.character.name, 'My own Aric');
  assert.equal(upgraded.character.level, 3);
  assert.ok(upgraded.rolls.some(r => r.name === 'My homebrew Roll'));
  assert.equal(sword.effects[0].actions[0].kind, 'ROLL_AFTER');
  assert.deepEqual(doc, original, 'upgrading must not mutate the original IndexedDB record');
  assert.equal(canUpgradeLegacyStarterWeaponRolls(upgraded), false);
  validateDocument(upgraded);
});

test('legacy starter upgrade must not overwrite already customized rules', () => {
  const { doc } = oldFighterTemplate();
  const attack = doc.rolls.find(r => r.name === 'Longsword · attack');
  attack.expression = '2d20+{Strength}+{Proficiency}';
  assert.equal(canUpgradeLegacyStarterWeaponRolls(doc), false);
  assert.equal(upgradeLegacyStarterWeaponRolls(doc), null);
  attack.expression = '1d20+{Strength}+{Proficiency}';
  attack.effects = [{ id: 'custom-effect' }];
  assert.equal(canUpgradeLegacyStarterWeaponRolls(doc), false);
  assert.equal(upgradeLegacyStarterWeaponRolls(doc), null);
});

test('legacy Ranger and Paladin weapon pairs can be upgraded without D&D-only crit', () => {
  for (const [index,name,atk,dmg] of [
    [3,'Bow','Bow shot','Bow damage'], [5,'Sword','Sword attack','Sword damage']]) {
    const doc = instantiateTemplate(index);
    const roll = doc.rolls.find(r => r.name === name);
    const [a,b] = roll.subgroups;
    roll.name = atk; roll.expression = a.expression; roll.subgroups = [];
    const second = { ...newRoll(doc.character.id, roll.groupId), name:dmg, expression:b.expression };
    doc.rolls.splice(doc.rolls.indexOf(roll) + 1, 0, second);
    assert.equal(canUpgradeLegacyStarterWeaponRolls(doc), true);
    const migrated = upgradeLegacyStarterWeaponRolls(doc);
    const weapon = migrated.rolls.find(r => r.name === name);
    assert.ok(weapon);
    assert.equal(weapon.effects.length, 0);
    assert.deepEqual(weapon.subgroups.map(x => x.name), ['Attack','Damage']);
  }
});
