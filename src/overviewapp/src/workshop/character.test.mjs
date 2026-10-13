import test from 'node:test';
import assert from 'node:assert/strict';
import {
  templates, instantiateTemplate, validateDocument, envelope, documentFromEnvelope,
  encodeToken, decodeToken, importAsCopy, normalFormula, newPart, newRoll, newCharacter, newGroup, newModifier, shareUrl,
  canUpgradeLegacyStarterWeaponRolls, upgradeLegacyStarterWeaponRolls, withImportPreferences, androidUrl,
  FIGHTER_2014_LEVELS, FIGHTER_2014_PROFICIENCY_STEPS, ROGUE_2014_LEVELS, WIZARD_2014_LEVELS, RANGER_2014_LEVELS, CLERIC_2014_LEVELS, PALADIN_2014_LEVELS, BARD_2014_LEVELS, readShareHash,
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

test('saved game-agnostic classes are not replaced when loading modern D&D presets', () => {
  for (const index of [6,7,8]) {
    const doc = instantiateTemplate(index);
    assert.ok(!doc.character.tag.startsWith('D&D 5e'));
    assert.ok(doc.rolls.every(roll => roll.effects.length===0));
    assert.deepEqual(decodeToken(encodeToken(doc)),doc);
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

test('saved legacy generic Ranger and Paladin weapon pairs can still upgrade without D&D-only crit', () => {
  for (const [tag,name,atk,dmg,stat,value] of [
    ['A woodland explorer','Bow','Bow shot','Bow damage','Agility',2],
    ['A steadfast champion','Sword','Sword attack','Sword damage','Might',3]]) {
    const doc = newCharacter('Legacy character');
    doc.character.tag = tag;
    doc.modifiers = [{...newModifier(doc.character.id), name:stat, value}];
    const group = {...newGroup(doc.character.id), name:'Combat'};
    doc.groups.push(group);
    doc.rolls.push(
      {...newRoll(doc.character.id,group.id), name:atk, expression:'1d20+{'+stat+'}'},
      {...newRoll(doc.character.id,group.id), name:dmg, expression:'1d8+{'+stat+'}'},
    );
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

test('unmodified 2014 Fighter with future attacks fits a QR through a versioned offline preset link', () => {
  const fighter=instantiateTemplate(0);
  fighter.character.name='Arianna · Fighter';
  fighter.character.level=18;
  const url=shareUrl(fighter,'https://example.com/Android.DiceThrower/workshop/');
  assert.match(url,/#template=fighter2014v1&name=/);
  assert.ok(url.length<=2600);
  const recipient=readShareHash(new URL(url).hash);
  assert.equal(recipient.character.name,fighter.character.name);
  assert.equal(recipient.character.level,18);
  assert.equal(recipient.rolls.length,fighter.rolls.length);
  assert.deepEqual(recipient.rolls.map(r=>r.minimumLevel),fighter.rolls.map(r=>r.minimumLevel));
  assert.equal(recipient.rolls.find(r=>r.name==='Longsword · 4 attacks').effects.length,4);
  // Site -> installed Android still transfers complete versioned JSON after preview.
  const app=androidUrl(recipient);
  assert.ok(app.length<=8000,'Android app handoff too long');
  assert.deepEqual(decodeToken(decodeURIComponent(app.split('data=')[1])),recipient);
});

test('short preset URL cannot silently discard a customized roll, condition, style, or stat', () => {
  const original=instantiateTemplate(0);
  for(const change of [
    doc=>doc.modifiers.find(x=>x.name==='Strength').value=4,
    doc=>doc.rolls.find(x=>x.name==='Longsword').effects[0].actions[0].expression='2d8',
    doc=>doc.rolls.find(x=>x.name==='Longsword').name='Magic sword',
    doc=>doc.character.tag='A different campaign',
  ]) {
    const custom=structuredClone(original);
    change(custom);
    validateDocument(custom);
    assert.throws(()=>shareUrl(custom,'https://example.com/Android.DiceThrower/workshop/'),/Link too long/);
  }
  assert.throws(()=>readShareHash('#template=fighter2014v1&name=Good&level=0'),/Invalid/);
  assert.equal(readShareHash('#template=not-supported&name=Someone&level=1'),null);
});

test('2014 Rogue class reference covers every level and never adds 2024 features', () => {
  assert.equal(ROGUE_2014_LEVELS.length, 20);
  for (let level = 1; level <= 20; level++) {
    const tier = ROGUE_2014_LEVELS[level-1];
    assert.equal(tier.level, level);
    assert.equal(tier.proficiency, 2 + [5,9,13,17].filter(start => level >= start).length);
    assert.equal(tier.sneakAttackDice, Math.ceil(level/2));
    assert.ok(!/Cunning Strike|Weapon Mastery|Epic Boon/.test(tier.features));
  }
  assert.match(ROGUE_2014_LEVELS[10].features, /Reliable Talent/);
  assert.match(ROGUE_2014_LEVELS[14].features, /Slippery Mind/);
  assert.match(ROGUE_2014_LEVELS[19].features, /Stroke of Luck/);
});

test('2014 Rogue dagger pairs Attack and Damage with independent d4 natural-20 critical', () => {
  const rogue = instantiateTemplate(1);
  assert.equal(rogue.character.level, 1);
  assert.equal(templates[1].category, 'D&D 5e (2014) · Rogue');
  const dagger = rogue.rolls.find(roll => roll.name === 'Dagger');
  assert.ok(dagger);
  assert.deepEqual(dagger.subgroups.map(part => part.name), ['Attack','Damage']);
  assert.equal(dagger.subgroups[0].expression, '1d20+{Dexterity}+{Proficiency}+{levelstep:5:9:13:17}');
  assert.equal(dagger.subgroups[1].expression, '1d4+{Dexterity}');
  assert.equal(dagger.effects.length, 1);
  const effect = dagger.effects[0];
  assert.equal(effect.activationGroups[0].conditions[0].partId, dagger.subgroups[0].id);
  assert.equal(effect.activationGroups[0].conditions[0].scope, 'DICE_ONLY');
  assert.equal(effect.activationGroups[0].conditions[0].threshold, '20');
  assert.equal(effect.actions[0].kind, 'ROLL_AFTER');
  assert.equal(effect.actions[0].scope, 'DICE_ONLY');
  assert.equal(effect.actions[0].targetPartId, dagger.subgroups[1].id);
  assert.equal(effect.actions[0].expression, '1d4');
  assert.equal(rogue.modifiers.find(stat => stat.name === 'Dexterity').value, 3);
  assert.equal(rogue.modifiers.find(stat => stat.name === 'Proficiency').value, 2);
});

test('Sneak Attack is a player-triggered damage roll and doubles ONLY its dice on critical', () => {
  const rogue = instantiateTemplate(1);
  const sneak = rogue.rolls.find(roll => roll.name === 'Sneak Attack · if eligible');
  const critical = rogue.rolls.find(roll => roll.name === 'Sneak Attack · critical (if eligible)');
  assert.equal(sneak.expression, '1d6');
  assert.equal(critical.expression, '2d6');
  assert.deepEqual(sneak.levelRules.map(rule => rule.trigger), [3,5,7,9,11,13,15,17,19]);
  assert.deepEqual(critical.levelRules.map(rule => rule.trigger), [3,5,7,9,11,13,15,17,19]);
  assert.ok(sneak.levelRules.every(rule => rule.kind === 'FROM_LEVEL' && rule.expression === '1d6'));
  assert.ok(critical.levelRules.every(rule => rule.kind === 'FROM_LEVEL' && rule.expression === '2d6'));
  assert.equal(sneak.effects.length, 0);
  for (let level = 1; level <= 20; level++) {
    const active = sneak.levelRules.filter(rule => rule.trigger <= level);
    const criticalActive = critical.levelRules.filter(rule => rule.trigger <= level);
    const expectedDice = Math.ceil(level / 2);
    assert.equal(1 + active.length, expectedDice, 'Sneak Attack level ' + level);
    assert.equal(2 + 2 * criticalActive.length, 2 * expectedDice, 'Critical at level ' + level);
  }
  const extraWisdom = rogue.rolls.find(roll => roll.name === 'Wisdom save (Slippery Mind)');
  assert.equal(extraWisdom.minimumLevel, 15);
  assert.equal(extraWisdom.expression, '1d20+{Wisdom}+{Proficiency}+{levelstep:5:9:13:17}');
  for (const name of ['Stealth','Acrobatics','Sleight of Hand','Perception',"Thieves' tools",'Dexterity save','Intelligence save']) {
    const roll = rogue.rolls.find(r => r.name === name);
    assert.match(roll.expression, /\{Proficiency\}\+\{levelstep:5:9:13:17\}/);
  }
  assert.ok(!rogue.rolls.some(r => /Reliable Talent|Cunning Action|Roguish Archetype|ASI|Expertise/.test(r.name)));
});

test('Rogue 2014 preset roundtrips with level rules and valid import remapped effect IDs', () => {
  const rogue = instantiateTemplate(1);
  validateDocument(rogue);
  assert.deepEqual(decodeToken(encodeToken(rogue)), rogue);
  const clone = importAsCopy(rogue);
  const attack = clone.rolls.find(r => r.name === 'Dagger');
  assert.equal(attack.effects[0].activationGroups[0].conditions[0].partId, attack.subgroups[0].id);
  assert.equal(attack.effects[0].actions[0].targetPartId, attack.subgroups[1].id);
  assert.notEqual(attack.subgroups[1].id, rogue.rolls.find(r => r.name === 'Dagger').subgroups[1].id);
  assert.deepEqual(clone.rolls.find(r => r.name === 'Sneak Attack · if eligible').levelRules.map(r => r.trigger),
    [3,5,7,9,11,13,15,17,19]);
  assert.deepEqual(documentFromEnvelope(envelope(clone)), clone);
  const recipient = withImportPreferences(rogue, 'Nim the Swift', 20);
  const link = shareUrl(recipient,'https://example.com/Android.DiceThrower/workshop/');
  assert.ok(link.length <= 2600, 'Preset QR should fit');
  const shared = readShareHash(new URL(link).hash);
  assert.equal(shared.character.level, 20);
  assert.equal(shared.character.name, 'Nim the Swift');
  assert.equal(shared.rolls.length, rogue.rolls.length);
  assert.ok(androidUrl(shared).length <= 8000, 'App handoff must fit');
  const custom = structuredClone(rogue);
  custom.rolls.find(r => r.name === 'Sneak Attack · if eligible').levelRules[0].expression = '2d6';
  validateDocument(custom);
  const customLink = (() => { try { return shareUrl(custom,'https://example.com/workshop/'); } catch { return null; } })();
  assert.ok(!customLink || !customLink.includes('#template=rogue2014v1'),
    'Edited Sneak Attack must never produce a lossy preset QR');
});

test('Wizard 2014 class slots and cantrip known cover levels 1 through 20', () => {
  assert.equal(templates[2].category, 'D&D 5e (2014) · Wizard');
  assert.equal(WIZARD_2014_LEVELS.length, 20);
  for (let level=1; level<=20; level++) {
    const row=WIZARD_2014_LEVELS[level-1];
    assert.equal(row.level, level);
    assert.equal(row.proficiency, 2 + [5,9,13,17].filter(v=>level>=v).length);
    assert.equal(row.cantripsKnown, level>=10 ? 5 : level>=4 ? 4 : 3);
    assert.equal(row.slots.length,9);
    assert.ok(row.slots.every(value => Number.isInteger(value) && value>=0));
    assert.equal(row.slots.filter(v=>v>0).length, Math.min(9,Math.floor((level+1)/2)));
  }
  assert.deepEqual(WIZARD_2014_LEVELS[0].slots,[2,0,0,0,0,0,0,0,0]);
  assert.deepEqual(WIZARD_2014_LEVELS[4].slots,[4,3,2,0,0,0,0,0,0]);
  assert.deepEqual(WIZARD_2014_LEVELS[16].slots,[4,3,3,3,2,1,1,1,1]);
  assert.deepEqual(WIZARD_2014_LEVELS[19].slots,[4,3,3,3,3,2,2,1,1]);
  assert.match(WIZARD_2014_LEVELS[17].features,/Spell Mastery/);
  assert.match(WIZARD_2014_LEVELS[19].features,/Signature Spells/);
});

test('Wizard Fire Bolt scaling and natural-20 critical use correct 2014 dice at each tier', () => {
  const wizard=instantiateTemplate(2);
  assert.equal(wizard.character.level,1);
  const bolts=wizard.rolls.filter(r=>r.name.startsWith('Fire Bolt · '));
  assert.deepEqual(bolts.map(r=>r.minimumLevel),[1,5,11,17]);
  for(const [i,roll] of bolts.entries()){
    const dice=i+1;
    assert.equal(roll.subgroups.length,2);
    assert.equal(roll.subgroups[0].expression,'1d20+{Intelligence}+{Proficiency}+{levelstep:5:9:13:17}');
    assert.equal(roll.subgroups[1].expression,dice+'d10');
    assert.equal(roll.effects.length,1);
    assert.equal(roll.effects[0].activationGroups[0].conditions[0].partId,roll.subgroups[0].id);
    assert.equal(roll.effects[0].activationGroups[0].conditions[0].scope,'DICE_ONLY');
    assert.equal(roll.effects[0].activationGroups[0].conditions[0].threshold,'20');
    assert.equal(roll.effects[0].actions[0].kind,'ROLL_AFTER');
    assert.equal(roll.effects[0].actions[0].scope,'DICE_ONLY');
    assert.equal(roll.effects[0].actions[0].targetPartId,roll.subgroups[1].id);
    assert.equal(roll.effects[0].actions[0].expression,dice+'d10');
  }
  for(let level=1;level<=20;level++){
    const selected=bolts.filter(r=>r.minimumLevel<=level).at(-1);
    const expected=level>=17 ? 4 : level>=11 ? 3 : level>=5 ? 2 : 1;
    assert.equal(selected.subgroups[1].expression,expected+'d10','Cantrip level '+level);
  }
  for(const name of ['Arcana','Investigation','Intelligence save','Wisdom save']){
    const roll=wizard.rolls.find(r=>r.name===name);
    assert.match(roll.expression,/\{Proficiency\}\+\{levelstep:5:9:13:17\}/);
  }
});

test('Wizard Magic Missile has independent darts, no attack or critical and correct spell slot gates', () => {
  const wizard=instantiateTemplate(2);
  const spells=wizard.rolls.filter(r=>r.name.startsWith('Magic Missile · slot '));
  assert.equal(spells.length,9);
  for(let slot=1;slot<=9;slot++){
    const roll=spells[slot-1],darts=slot+2;
    assert.equal(roll.minimumLevel,2*slot-1);
    assert.equal(roll.effects.length,0);
    assert.equal(roll.subgroups.length,darts);
    assert.deepEqual(roll.subgroups.map(p=>p.name),
      Array.from({length:darts},(_,i)=>'Dart '+(i+1)));
    assert.ok(roll.subgroups.every(p=>p.expression==='1d4+1'));
    assert.equal(roll.expression,normalFormula(roll.subgroups));
  }
  for(let level=1;level<=20;level++){
    const available=spells.filter(r=>r.minimumLevel<=level);
    assert.equal(available.length,Math.min(9,Math.floor((level+1)/2)));
  }
  assert.ok(!wizard.rolls.some(r=>/Arcane Recovery|Tradition|Spell Mastery|Signature Spell|ASI/.test(r.name)));
});

test('Wizard 2014 QR recipe survives import; custom modifications cannot be lost', () => {
  const wizard=instantiateTemplate(2);
  validateDocument(wizard);
  assert.deepEqual(decodeToken(encodeToken(wizard)),wizard);
  const imported=importAsCopy(wizard);
  assert.notEqual(imported.character.id,wizard.character.id);
  const bolt=imported.rolls.find(r=>r.name==='Fire Bolt · 4d10');
  assert.equal(bolt.effects[0].actions[0].targetPartId,bolt.subgroups[1].id);
  assert.notEqual(bolt.subgroups[1].id,wizard.rolls.find(r=>r.name==='Fire Bolt · 4d10').subgroups[1].id);
  assert.deepEqual(documentFromEnvelope(envelope(imported)),imported);
  const recipient=withImportPreferences(wizard,'Lyra Master Wizard',20);
  const url=shareUrl(recipient,'https://example.com/Android.DiceThrower/workshop/');
  assert.ok(url.length<=2600,'Wizard template QR fits');
  assert.match(url,/#template=wizard2014v1&/);
  const decoded=readShareHash(new URL(url).hash);
  assert.equal(decoded.character.name,'Lyra Master Wizard');
  assert.equal(decoded.character.level,20);
  assert.deepEqual(decoded.rolls.map(r=>r.minimumLevel),wizard.rolls.map(r=>r.minimumLevel));
  assert.ok(androidUrl(decoded).length<=8000,'Android app handoff supported');
  const custom=structuredClone(wizard);
  custom.rolls.find(r=>r.name==='Fire Bolt · 4d10').effects[0].actions[0].expression='3d10';
  validateDocument(custom);
  let customUrl=null;
  try { customUrl=shareUrl(custom,'https://example.com/workshop/'); }catch{}
  assert.ok(!customUrl || !customUrl.includes('#template=wizard2014v1'),
    'Modified wizard must not use a lossy QR recipe');
});

test('Ranger 2014 class table covers 20 levels and correct spell slot unlocks', () => {
  assert.equal(templates[3].category,'D&D 5e (2014) · Ranger');
  assert.equal(RANGER_2014_LEVELS.length,20);
  for(let level=1;level<=20;level++){
    const row=RANGER_2014_LEVELS[level-1];
    assert.equal(row.level,level);
    assert.equal(row.proficiency,2+[5,9,13,17].filter(start=>level>=start).length);
    assert.equal(row.slots.length,5);
    assert.ok(row.slots.every(count=>Number.isInteger(count)&&count>=0));
    const maxSlot=level===1 ? 0 : level>=17 ? 5 : level>=13 ? 4 : level>=9 ? 3 : level>=5 ? 2 : 1;
    assert.equal(row.slots.filter(x=>x>0).length,maxSlot);
    assert.ok(!/Weapon Mastery|Epic Boon|Favored Foe/.test(row.features));
  }
  assert.deepEqual(RANGER_2014_LEVELS[0].slots,[0,0,0,0,0]);
  assert.deepEqual(RANGER_2014_LEVELS[1].slots,[2,0,0,0,0]);
  assert.deepEqual(RANGER_2014_LEVELS[4].slots,[4,2,0,0,0]);
  assert.deepEqual(RANGER_2014_LEVELS[19].slots,[4,3,3,3,2]);
  assert.match(RANGER_2014_LEVELS[4].features,/Extra Attack/);
  assert.match(RANGER_2014_LEVELS[19].features,/Foe Slayer/);
});

test('2014 Ranger bow and level-5 Extra Attack have correct independent criticals', () => {
  const ranger=instantiateTemplate(3);
  assert.equal(ranger.character.level,1);
  const bow=ranger.rolls.find(r=>r.name==='Bow');
  assert.equal(bow.subgroups.length,2);
  assert.equal(bow.subgroups[0].expression,'1d20+{Dexterity}+{Proficiency}+{levelstep:5:9:13:17}');
  assert.equal(bow.subgroups[1].expression,'1d8+{Dexterity}');
  assert.equal(bow.effects[0].activationGroups[0].conditions[0].partId,bow.subgroups[0].id);
  assert.equal(bow.effects[0].activationGroups[0].conditions[0].scope,'DICE_ONLY');
  assert.equal(bow.effects[0].actions[0].expression,'1d8');
  assert.equal(bow.effects[0].actions[0].scope,'DICE_ONLY');
  const multi=ranger.rolls.find(r=>r.name==='Bow · 2 attacks');
  assert.equal(multi.minimumLevel,5);
  assert.equal(multi.subgroups.length,4);
  assert.equal(multi.effects.length,2);
  for(let i=0;i<2;i++){
    assert.equal(multi.subgroups[2*i].name,'Attack '+(i+1));
    assert.equal(multi.effects[i].activationGroups[0].conditions[0].partId,multi.subgroups[2*i].id);
    assert.equal(multi.effects[i].actions[0].targetPartId,multi.subgroups[2*i+1].id);
    assert.equal(multi.effects[i].actions[0].expression,'1d8');
  }
  for(let level=1;level<=20;level++)
    assert.equal(multi.minimumLevel<=level,level>=5);
});

test('Ranger spell and marked-hit dice are optional, gated and never secretly applied', () => {
  const ranger=instantiateTemplate(3);
  const mark=ranger.rolls.find(r=>r.name==="Hunter's Mark · on eligible hit");
  const crit=ranger.rolls.find(r=>r.name==="Hunter's Mark · on critical");
  assert.equal(mark.expression,'1d6');
  assert.equal(crit.expression,'2d6');
  assert.equal(mark.minimumLevel,2);
  assert.equal(crit.minimumLevel,2);
  assert.equal(mark.effects.length,0);
  assert.equal(crit.effects.length,0);
  assert.equal(ranger.rolls.find(r=>r.name==='Spell attack (if applicable)').minimumLevel,2);
  for(const name of ['Survival','Perception','Stealth','Strength save','Dexterity save'])
    assert.match(ranger.rolls.find(r=>r.name===name).expression,
      /\{Proficiency\}\+\{levelstep:5:9:13:17\}/);
  for(const [slot,min] of [[1,2],[2,5],[3,9],[4,13],[5,17]]){
    const roll=ranger.rolls.find(r=>r.name==='Cure Wounds · slot '+slot+' (if known)');
    assert.equal(roll.minimumLevel,min);
    assert.equal(roll.expression,slot+'d8+{Wisdom}');
  }
  assert.ok(!ranger.rolls.some(r=>/Natural Explorer|Favored Enemy|Foe Slayer|Ranger Archetype/.test(r.name)));
});

test('Ranger 2014 share, critical references, old saves and customization roundtrip safely', () => {
  const ranger=instantiateTemplate(3);
  validateDocument(ranger);
  assert.deepEqual(decodeToken(encodeToken(ranger)),ranger);
  const clone=importAsCopy(ranger);
  const mult=clone.rolls.find(r=>r.name==='Bow · 2 attacks');
  assert.equal(mult.effects[1].activationGroups[0].conditions[0].partId,mult.subgroups[2].id);
  assert.equal(mult.effects[1].actions[0].targetPartId,mult.subgroups[3].id);
  assert.deepEqual(documentFromEnvelope(envelope(clone)),clone);
  const recipient=withImportPreferences(ranger,'Bryn Veteran Ranger',20);
  const url=shareUrl(recipient,'https://example.com/Android.DiceThrower/workshop/');
  assert.ok(url.length<=2600);
  const shared=readShareHash(new URL(url).hash);
  assert.equal(shared.character.name,'Bryn Veteran Ranger');
  assert.equal(shared.character.level,20);
  assert.equal(shared.rolls.length,ranger.rolls.length);
  assert.ok(androidUrl(shared).length<=8000);
  const edited=structuredClone(ranger);
  edited.rolls.find(r=>r.name==='Bow · 2 attacks').effects[1].actions[0].expression='2d8';
  validateDocument(edited);
  let link=null;
  try{ link=shareUrl(edited,'https://example.com/workshop/'); }catch{}
  assert.ok(!link || !link.includes('#template=ranger2014v1'),
    'A customized Ranger must never be silently reconstructed from a stock QR');
});

test('Cleric 2014 class timeline covers levels 1–20, full caster slot counts and cantrips', () => {
  assert.equal(templates[4].category,'D&D 5e (2014) · Cleric');
  assert.equal(CLERIC_2014_LEVELS.length,20);
  for(let level=1;level<=20;level++){
    const row=CLERIC_2014_LEVELS[level-1];
    assert.equal(row.level,level);
    assert.equal(row.proficiency,2+[5,9,13,17].filter(v=>level>=v).length);
    assert.equal(row.cantripsKnown,level>=10 ? 5 : level>=4 ? 4 : 3);
    assert.deepEqual(row.slots,WIZARD_2014_LEVELS[level-1].slots);
    assert.ok(!/Weapon Mastery|Epic Boon/.test(row.features));
  }
  assert.match(CLERIC_2014_LEVELS[0].features,/Divine Domain/);
  assert.match(CLERIC_2014_LEVELS[9].features,/Divine Intervention/);
  assert.match(CLERIC_2014_LEVELS[19].features,/Divine Intervention Improvement/);
});

test('Cleric 2014 Sacred Flame scales at levels 5/11/17 without attack or crit', () => {
  const cleric=instantiateTemplate(4);
  assert.equal(cleric.character.level,1);
  const sacred=cleric.rolls.filter(r=>r.name.startsWith('Sacred Flame · '));
  assert.deepEqual(sacred.map(r=>r.minimumLevel),[1,5,11,17]);
  for(let i=0;i<sacred.length;i++){
    const roll=sacred[i];
    assert.equal(roll.expression,(i+1)+'d8');
    assert.equal(roll.subgroups.length,0);
    assert.equal(roll.effects.length,0);
    assert.ok(!/1d20/.test(roll.expression));
  }
  for(let level=1;level<=20;level++){
    const dice=level>=17?4:level>=11?3:level>=5?2:1;
    assert.equal(sacred.filter(r=>r.minimumLevel<=level).at(-1).expression,dice+'d8');
  }
});

test('Cleric 2014 prepared-healing examples upcast with Wisdom exactly once', () => {
  const cleric=instantiateTemplate(4);
  const tiers=[[1,1],[2,3],[3,5],[4,7],[5,9],[6,11],[7,13],[8,15],[9,17]];
  for(const [slot,min] of tiers)for(const [name,die] of [['Healing Word','d4'],['Cure Wounds','d8']]){
    const roll=cleric.rolls.find(r=>r.name===name+' · slot '+slot+' (if prepared)');
    assert.ok(roll);
    assert.equal(roll.expression,slot+die+'+{Wisdom}');
    assert.equal(roll.minimumLevel,min);
    assert.equal(roll.effects.length,0,'Healing is not a natural20 critical');
  }
  for(let level=1;level<=20;level++){
    const slots=tiers.filter(([,min])=>min<=level).length;
    assert.equal(cleric.rolls.filter(r=>r.name.startsWith('Healing Word · ')&&r.minimumLevel<=level).length,slots);
    assert.equal(cleric.rolls.filter(r=>r.name.startsWith('Cure Wounds · ')&&r.minimumLevel<=level).length,slots);
  }
  assert.equal(cleric.rolls.find(r=>r.name==='Bless · optional d4 on an eligible d20').expression,'1d4');
  assert.equal(cleric.rolls.find(r=>r.name==='Guidance · optional d4 for ability check').expression,'1d4');
  for(const name of ['Insight','Religion','Medicine','Wisdom save','Charisma save']){
    assert.match(cleric.rolls.find(r=>r.name===name).expression,
      /\{Proficiency\}\+\{levelstep:5:9:13:17\}/);
  }
  assert.equal(cleric.rolls.find(r=>r.name==='Religion').expression,
    '1d20+{Intelligence}+{Proficiency}+{levelstep:5:9:13:17}');
  assert.ok(!cleric.rolls.some(r=>/Divine Intervention|Channel Divinity|ASI|Domain feature/.test(r.name)));
});

test('Cleric 2014 weapon crit uses its d6; import/remap and QR preserve rules', () => {
  const cleric=instantiateTemplate(4);
  const weapon=cleric.rolls.find(r=>r.name==='Mace');
  assert.deepEqual(weapon.subgroups.map(s=>s.name),['Attack','Damage']);
  assert.equal(weapon.subgroups[0].expression,'1d20+{Strength}+{Proficiency}+{levelstep:5:9:13:17}');
  assert.equal(weapon.subgroups[1].expression,'1d6+{Strength}');
  assert.equal(weapon.effects[0].activationGroups[0].conditions[0].partId,weapon.subgroups[0].id);
  assert.equal(weapon.effects[0].activationGroups[0].conditions[0].scope,'DICE_ONLY');
  assert.equal(weapon.effects[0].actions[0].targetPartId,weapon.subgroups[1].id);
  assert.equal(weapon.effects[0].actions[0].scope,'DICE_ONLY');
  assert.equal(weapon.effects[0].actions[0].expression,'1d6');
  assert.deepEqual(decodeToken(encodeToken(cleric)),cleric);
  const copied=importAsCopy(cleric);
  const mace=copied.rolls.find(r=>r.name==='Mace');
  assert.equal(mace.effects[0].actions[0].targetPartId,mace.subgroups[1].id);
  assert.notEqual(mace.subgroups[1].id,weapon.subgroups[1].id);
  assert.deepEqual(documentFromEnvelope(envelope(copied)),copied);
  const recipient=withImportPreferences(cleric,'Sol the Cleric',20);
  const link=shareUrl(recipient,'https://example.com/Android.DiceThrower/workshop/');
  assert.ok(link.length<=2600);
  const shared=readShareHash(new URL(link).hash);
  assert.equal(shared.character.level,20);
  assert.equal(shared.character.name,'Sol the Cleric');
  assert.ok(androidUrl(shared).length<=8000);
  const custom=structuredClone(cleric);
  custom.rolls.find(r=>r.name==='Sacred Flame · 4d8 (target Dex save)').expression='8d8';
  validateDocument(custom);
  let customUrl=null;
  try {customUrl=shareUrl(custom,'https://example.com/workshop/');}catch{}
  assert.ok(!customUrl||!customUrl.includes('#template=cleric2014v1'));
});

test('2014 Paladin class table gives 20 level features, official slots and lay on hands pool', () => {
  assert.equal(templates[5].category,'D&D 5e (2014) · Paladin');
  assert.equal(PALADIN_2014_LEVELS.length,20);
  const slots=[[0,0,0,0,0],[2,0,0,0,0],[3,0,0,0,0],[3,0,0,0,0],
    [4,2,0,0,0],[4,2,0,0,0],[4,3,0,0,0],[4,3,0,0,0],[4,3,2,0,0],
    [4,3,2,0,0],[4,3,3,0,0],[4,3,3,0,0],[4,3,3,1,0],[4,3,3,1,0],
    [4,3,3,2,0],[4,3,3,2,0],[4,3,3,3,1],[4,3,3,3,1],[4,3,3,3,2],[4,3,3,3,2]];
  for(let level=1;level<=20;level++){
    const row=PALADIN_2014_LEVELS[level-1];
    assert.equal(row.level,level);
    assert.equal(row.proficiency,2+[5,9,13,17].filter(th=>level>=th).length);
    assert.deepEqual(row.slots,slots[level-1]);
    assert.equal(row.layOnHandsPool,level*5);
    assert.ok(!/Weapon Mastery|Epic Boon/.test(row.features));
  }
  assert.match(PALADIN_2014_LEVELS[1].features,/Divine Smite/);
  assert.match(PALADIN_2014_LEVELS[4].features,/Extra Attack/);
  assert.match(PALADIN_2014_LEVELS[10].features,/Improved Divine Smite/);
  assert.match(PALADIN_2014_LEVELS[19].features,/Sacred Oath/);
});

test('Paladin longsword supports both independent attacks and level-11 Improved Smite crits', () => {
  const paladin=instantiateTemplate(5);
  assert.equal(paladin.character.level,1);
  const simple=paladin.rolls.find(r=>r.name==='Longsword');
  assert.equal(simple.subgroups[0].expression,'1d20+{Strength}+{Proficiency}+{levelstep:5:9:13:17}');
  assert.equal(simple.subgroups[1].expression,'1d8+{Strength}');
  assert.equal(simple.effects[0].actions[0].expression,'1d8');
  const options=paladin.rolls.filter(r=>r.name.startsWith('Longsword · '));
  assert.deepEqual(options.map(x=>x.minimumLevel),[5,11,11]);
  for(const roll of options) {
    assert.equal(roll.effects.length,roll.subgroups.length/2);
    for(let i=0;i<roll.effects.length;i++){
      const crit=roll.effects[i],attack=roll.subgroups[i*2],damage=roll.subgroups[i*2+1];
      assert.equal(crit.activationGroups[0].conditions[0].partId,attack.id);
      assert.equal(crit.activationGroups[0].conditions[0].scope,'DICE_ONLY');
      assert.equal(crit.activationGroups[0].conditions[0].threshold,'20');
      assert.equal(crit.actions[0].kind,'ROLL_AFTER');
      assert.equal(crit.actions[0].scope,'DICE_ONLY');
      assert.equal(crit.actions[0].targetPartId,damage.id);
      assert.equal(crit.actions[0].expression,(roll.minimumLevel===11?'2d8':'1d8'));
      assert.equal(damage.expression,(roll.minimumLevel===11?'2d8':'1d8')+'+{Strength}');
    }
  }
  for(let level=1;level<=20;level++)
    assert.deepEqual(options.filter(x=>x.minimumLevel<=level).map(x=>x.minimumLevel),
      level>=11?[5,11,11]:level>=5?[5]:[]);
});

test('Divine Smite 2014 is an optional choice after melee hit: correct slot, enemy, crit dice', () => {
  const doc=instantiateTemplate(5);
  const variants=doc.rolls.filter(r=>r.name.startsWith('Divine Smite ·'));
  assert.equal(variants.length,20);
  for(const [slot,min] of [[1,2],[2,5],[3,9],[4,13],[5,17]]){
    const count=Math.min(5,slot+1);
    for(const [suffix,dice] of [
      ['normal',count],['critical',count*2],
      ['undead or fiend',count+1],['undead or fiend critical',(count+1)*2],
    ]){
      const roll=variants.find(r=>r.name==='Divine Smite · slot '+slot+' · '+suffix);
      assert.ok(roll);
      assert.equal(roll.minimumLevel,min);
      assert.equal(roll.expression,dice+'d8');
      assert.equal(roll.effects.length,0,'Smite must not be unconditionally applied to longsword');
      assert.equal(roll.subgroups.length,0);
    }
  }
  assert.equal(doc.rolls.find(r=>r.name==='Spell attack (if applicable)').minimumLevel,2);
  for(const name of ['Athletics','Persuasion','Wisdom save','Charisma save'])
    assert.match(doc.rolls.find(r=>r.name===name).expression,/\{Proficiency\}\+\{levelstep:5:9:13:17\}/);
  assert.ok(!doc.rolls.some(r=>/Aura of Protection|Sacred Oath|ASI|Lay on Hands/.test(r.name)));
});

test('Paladin 2014 full v1 transfer, import references and stock-only QR', () => {
  const doc=instantiateTemplate(5);
  validateDocument(doc);
  assert.deepEqual(decodeToken(encodeToken(doc)),doc);
  const clone=importAsCopy(doc),two=clone.rolls.find(r=>r.name==='Longsword · 2 attacks + Improved Smite');
  assert.equal(two.effects[1].activationGroups[0].conditions[0].partId,two.subgroups[2].id);
  assert.equal(two.effects[1].actions[0].targetPartId,two.subgroups[3].id);
  assert.deepEqual(documentFromEnvelope(envelope(clone)),clone);
  const recipient=withImportPreferences(doc,'Thane Champion',20);
  const link=shareUrl(recipient,'https://example.com/Android.DiceThrower/workshop/');
  assert.ok(link.length<=2600);
  const shared=readShareHash(new URL(link).hash);
  assert.equal(shared.character.level,20);
  assert.equal(shared.character.name,'Thane Champion');
  assert.ok(androidUrl(shared).length<=8000);
  const edited=structuredClone(doc);
  edited.rolls.find(r=>r.name==='Divine Smite · slot 5 · critical').expression='9d8';
  validateDocument(edited);
  let editedUrl=null;
  try {editedUrl=shareUrl(edited,'https://example.com/workshop/');}catch{}
  assert.ok(!editedUrl||!editedUrl.includes('#template=paladin2014v1'));
});

test('2014 Bard class table has all 20 official slots, known spells and milestones', () => {
  assert.equal(templates[9].category,'D&D 5e (2014) · Bard');
  assert.equal(BARD_2014_LEVELS.length,20);
  const expectedKnown=[4,5,6,7,8,9,10,11,12,14,15,15,16,18,19,19,20,22,22,22];
  for(let level=1;level<=20;level++){
    const row=BARD_2014_LEVELS[level-1];
    assert.equal(row.level,level);
    assert.equal(row.proficiency,2+[5,9,13,17].filter(l=>level>=l).length);
    assert.equal(row.cantripsKnown,level>=10?4:level>=4?3:2);
    assert.equal(row.spellsKnown,expectedKnown[level-1]);
    assert.deepEqual(row.slots,WIZARD_2014_LEVELS[level-1].slots);
    assert.equal(row.inspirationDie,level>=15?12:level>=10?10:level>=5?8:6);
    assert.equal(row.songOfRestDie,level>=17?12:level>=13?10:level>=9?8:level>=2?6:0);
    assert.ok(!/Weapon Mastery|Epic Boon/.test(row.features));
  }
  assert.match(BARD_2014_LEVELS[9].features,/Magical Secrets/);
  assert.match(BARD_2014_LEVELS[19].features,/Superior Inspiration/);
});

test('Bard 2014 Vicious Mockery scales on failed Wisdom save and never generates critical', () => {
  const doc=instantiateTemplate(9);
  assert.equal(doc.character.level,1);
  const options=doc.rolls.filter(r=>r.name.startsWith('Vicious Mockery ·'));
  assert.deepEqual(options.map(x=>x.minimumLevel),[1,5,11,17]);
  for(let i=0;i<options.length;i++){
    assert.equal(options[i].expression,(i+1)+'d4');
    assert.equal(options[i].subgroups.length,0);
    assert.equal(options[i].effects.length,0);
    assert.ok(!options[i].expression.includes('1d20'));
  }
  for(let level=1;level<=20;level++){
    const expected=level>=17?4:level>=11?3:level>=5?2:1;
    assert.equal(options.filter(r=>r.minimumLevel<=level).at(-1).expression,expected+'d4');
  }
  const rapier=doc.rolls.find(r=>r.name==='Rapier');
  assert.equal(rapier.subgroups[0].expression,'1d20+{Dexterity}+{Proficiency}+{levelstep:5:9:13:17}');
  assert.equal(rapier.subgroups[1].expression,'1d8+{Dexterity}');
  assert.equal(rapier.effects[0].activationGroups[0].conditions[0].partId,rapier.subgroups[0].id);
  assert.equal(rapier.effects[0].actions[0].targetPartId,rapier.subgroups[1].id);
  assert.equal(rapier.effects[0].actions[0].expression,'1d8');
});

test('Bardic Inspiration, Song of Rest and Jack of All Trades never apply indiscriminately', () => {
  const doc=instantiateTemplate(9);
  for(const [min,die] of [[1,6],[5,8],[10,10],[15,12]]){
    const roll=doc.rolls.find(r=>r.name==='Bardic Inspiration · d'+die+' (ally only)');
    assert.ok(roll);
    assert.equal(roll.minimumLevel,min);
    assert.equal(roll.expression,'1d'+die);
    assert.equal(roll.effects.length,0);
  }
  for(const [min,die] of [[2,6],[9,8],[13,10],[17,12]]){
    const roll=doc.rolls.find(r=>r.name==='Song of Rest · bonus d'+die+' (if eligible)');
    assert.ok(roll);
    assert.equal(roll.minimumLevel,min);
    assert.equal(roll.expression,'1d'+die);
    assert.equal(roll.effects.length,0);
  }
  const jack=doc.rolls.find(r=>r.name==='Jack of All Trades · unproficient Wisdom check');
  assert.equal(jack.minimumLevel,2);
  assert.equal(jack.expression,'1d20+{Wisdom}+{levelstep:2:9:17}');
  for(let level=1;level<=20;level++)
    assert.equal([2,9,17].filter(v=>v<=level).length,
      level<2?0:level<9?1:level<17?2:3);
  for(const name of ['Performance','Persuasion','Deception','Dexterity save','Charisma save']){
    const roll=doc.rolls.find(r=>r.name===name);
    assert.match(roll.expression,/\{Proficiency\}\+\{levelstep:5:9:13:17\}/);
    assert.ok(!roll.expression.includes('levelstep:2:9:17'),'Jack must not apply to proficient checks');
  }
});

test('Bard 2014 upcast Healing Word is conditional and gated for 1st–9th spell slots', () => {
  const doc=instantiateTemplate(9);
  for(const [slot,min] of [[1,1],[2,3],[3,5],[4,7],[5,9],[6,11],[7,13],[8,15],[9,17]]){
    const r=doc.rolls.find(r=>r.name==='Healing Word · slot '+slot+' (if known)');
    assert.ok(r);
    assert.equal(r.minimumLevel,min);
    assert.equal(r.expression,slot+'d4+{Charisma}');
    assert.equal(r.effects.length,0);
  }
  assert.ok(!doc.rolls.some(r=>/Expertise|Magical Secrets|Superior Inspiration|Bard College/.test(r.name)));
});

test('Bard 2014 exact QR preset and complete Android transfer preserve styles, rolls and ID references', () => {
  const doc=instantiateTemplate(9);
  validateDocument(doc);
  assert.deepEqual(decodeToken(encodeToken(doc)),doc);
  const clone=importAsCopy(doc), rapier=clone.rolls.find(r=>r.name==='Rapier');
  assert.equal(rapier.effects[0].activationGroups[0].conditions[0].partId,rapier.subgroups[0].id);
  assert.equal(rapier.effects[0].actions[0].targetPartId,rapier.subgroups[1].id);
  assert.notEqual(rapier.subgroups[1].id,doc.rolls.find(r=>r.name==='Rapier').subgroups[1].id);
  assert.deepEqual(documentFromEnvelope(envelope(clone)),clone);
  const recipient=withImportPreferences(doc,'Orin Songweaver',20);
  const link=shareUrl(recipient,'https://example.com/Android.DiceThrower/workshop/');
  assert.ok(link.length<=2600);
  const shared=readShareHash(new URL(link).hash);
  assert.equal(shared.character.name,'Orin Songweaver');
  assert.equal(shared.character.level,20);
  assert.equal(shared.rolls.length,doc.rolls.length);
  assert.ok(androidUrl(shared).length<=8000);
  const altered=structuredClone(doc);
  altered.rolls.find(r=>r.name==='Vicious Mockery · 4d4 (target Wisdom save)').expression='5d4';
  validateDocument(altered);
  let url=null;
  try {url=shareUrl(altered,'https://example.com/workshop/');}catch{}
  assert.ok(!url||!url.includes('#template=bard2014v1'),'Customized Bard cannot use lossy recipe');
});
