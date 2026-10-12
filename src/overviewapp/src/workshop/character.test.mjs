import test from 'node:test';
import assert from 'node:assert/strict';
import {
  templates, instantiateTemplate, validateDocument, envelope, documentFromEnvelope,
  encodeToken, decodeToken, importAsCopy, normalFormula, newPart, newRoll, shareUrl,
  canUpgradeLegacyStarterWeaponRolls, upgradeLegacyStarterWeaponRolls, withImportPreferences, androidUrl,
  FIGHTER_2014_LEVELS, FIGHTER_2014_PROFICIENCY_STEPS,
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
  const weapons = fighter.rolls.filter(r => r.name === 'Longsword');
  assert.equal(weapons.length, 1);
  const [roll] = weapons;
  assert.equal(roll.name, 'Longsword');
  assert.deepEqual(roll.subgroups.map(p => p.name), ['Attack', 'Damage']);
  assert.equal(roll.subgroups[0].expression, '1d20+{Strength}+{Proficiency}+{levelstep:5:9:13:17}');
  assert.equal(roll.subgroups[1].expression, '1d8+{Strength}');
  assert.equal(roll.expression, '(1d20+{Strength}+{Proficiency}+{levelstep:5:9:13:17})+(1d8+{Strength})');
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
  // Recreate the exact pre-progression legacy expression for the opt-in upgrade.
  grouped.expression = attack.expression.replace('+{levelstep:5:9:13:17}', '');
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
  attack.effects = [];
  attack.visualEffects = { animationSpeed: 'custom' };
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

test('QR recipient can independently rename/relevel shared Fighter without changing source or rolls', () => {
  const source = instantiateTemplate(0);
  // A future gated roll must survive recipient name/level edits.
  source.rolls.find(r => r.name === 'Longsword').minimumLevel = 15;
  const payload = decodeToken(encodeToken(source));
  const adjusted = withImportPreferences(payload, '  New party fighter  ', 8);
  assert.equal(adjusted.character.name, 'New party fighter');
  assert.equal(adjusted.character.level, 8);
  assert.equal(source.character.level, 1);
  assert.equal(payload.character.name, source.character.name);
  assert.deepEqual(adjusted.rolls, source.rolls);
  assert.ok(adjusted.rolls.some(r => r.minimumLevel > adjusted.character.level));
  const appUri = androidUrl(adjusted);
  assert.match(appUri, /^dicethrower:\/\/import\?data=v1\./);
  const decoded = decodeToken(decodeURIComponent(appUri.split('data=')[1]));
  assert.deepEqual(decoded, adjusted);
  const copied = importAsCopy(adjusted);
  assert.notEqual(copied.character.id, adjusted.character.id);
  assert.equal(copied.character.level, 8);
});

test('recipient overrides are validated before import, preserving the shared original', () => {
  const source = instantiateTemplate(0);
  for (const [name, level] of [['', 1], [' '.repeat(5), 1],
    ['A'.repeat(161), 1], ['Valid', 0], ['Valid', -1],
    ['Valid', 10000], ['Valid', 1.5], ['Valid', 'abc']]) {
    assert.throws(() => withImportPreferences(source, name, level));
  }
  assert.equal(source.character.level, 1);
  assert.equal(source.character.name, 'Aric · Level 1 Fighter');
});

test('2014 Fighter class table has twenty levels and correct proficiency milestones', () => {
  assert.equal(FIGHTER_2014_LEVELS.length,20);
  assert.equal(FIGHTER_2014_PROFICIENCY_STEPS,'levelstep:5:9:13:17');
  for (let level=1;level<=20;level++) {
    const expected = 2 + [5,9,13,17].filter(v => level >= v).length;
    assert.equal(FIGHTER_2014_LEVELS[level-1].proficiency,expected);
    assert.equal(FIGHTER_2014_LEVELS[level-1].level,level);
  }
  assert.match(FIGHTER_2014_LEVELS[1].features,/Action Surge/);
  assert.match(FIGHTER_2014_LEVELS[2].features,/Martial Archetype/);
  assert.match(FIGHTER_2014_LEVELS[8].features,/Indomitable/);
  assert.match(FIGHTER_2014_LEVELS[16].features,/two uses|2 uses/);
  assert.match(FIGHTER_2014_LEVELS[19].features,/4 attacks/);
});

test('Fighter future attack-action Rolls unlock at levels 5, 11 and 20 with an independent crit per attack', () => {
  const doc = instantiateTemplate(0);
  const actionRolls = doc.rolls.filter(r => /^Longsword · \d attacks/.test(r.name));
  assert.deepEqual(actionRolls.map(r => r.minimumLevel), [5,11,20]);
  assert.deepEqual(actionRolls.map(r => r.subgroups.length), [4,6,8]);
  assert.deepEqual(actionRolls.map(r => r.effects.length), [2,3,4]);
  for (const [i, roll] of actionRolls.entries()) {
    const count = i+2;
    for(let a=0;a<count;a++) {
      const attack=roll.subgroups[a*2], damage=roll.subgroups[a*2+1],crit=roll.effects[a];
      assert.equal(attack.name,'Attack '+(a+1));
      assert.equal(damage.name,'Damage '+(a+1));
      assert.equal(crit.activationGroups[0].conditions[0].partId, attack.id);
      assert.equal(crit.activationGroups[0].conditions[0].scope,'DICE_ONLY');
      assert.equal(crit.activationGroups[0].conditions[0].threshold,'20');
      assert.equal(crit.actions[0].kind,'ROLL_AFTER');
      assert.equal(crit.actions[0].targetPartId,damage.id);
      assert.equal(crit.actions[0].expression,'1d8');
    }
  }
  for (let level=1;level<=20;level++) {
    const available=actionRolls.filter(r => r.minimumLevel<=level);
    assert.equal(available.length,[5,11,20].filter(l=>l<=level).length);
  }
  assert.deepEqual(decodeToken(encodeToken(doc)),doc);
  const copy=importAsCopy(doc);
  const copied=copy.rolls.find(r=>r.name==='Longsword · 4 attacks');
  assert.notEqual(copied.subgroups[0].id, actionRolls[2].subgroups[0].id);
  assert.equal(copied.effects[3].actions[0].targetPartId,copied.subgroups[7].id);
});

test('2014 Fighter automatically scales proficient roll formulas but does not invent ASI or subclass boosts', () => {
  const fighter=instantiateTemplate(0);
  const sources=['Longsword','Athletics','Strength save','Constitution save'];
  for(const name of sources) {
    const roll=fighter.rolls.find(r=>r.name===name);
    assert.ok(roll,name);
    const formula = roll.subgroups.length ? roll.subgroups[0].expression : roll.expression;
    assert.match(formula,/\{Proficiency\}\+\{levelstep:5:9:13:17\}/);
  }
  assert.equal(fighter.modifiers.find(v=>v.name==='Strength').value,3);
  assert.equal(fighter.modifiers.find(v=>v.name==='Proficiency').value,2);
  const healing=fighter.rolls.find(r=>r.name==='Second Wind · heal');
  assert.equal(healing.expression,'1d10+{level}');
  assert.ok(!fighter.rolls.some(r=>/subclass|ability score improvement/i.test(r.name)));
});
