import test from 'node:test';
import assert from 'node:assert/strict';
import { instantiateTemplate, envelope, validateDocument } from './character.js';
import { resolveLevelTokens, rollLevelPreview } from './roll-level-preview.js';

test('Wizard v3 Magic Missile displays base plus capped extra darts at the chosen level', () => {
  const wizard = instantiateTemplate(2);
  const roll = wizard.rolls.find(r => r.name === 'Magic Missile · highest unlocked slot (optional)');
  assert.ok(roll);
  const originalJson = JSON.stringify(wizard);
  const levels = [
    [1, 0], [2, 0], [3, 1], [4, 1], [5, 2], [7, 3],
    [9, 4], [11, 5], [13, 6], [15, 7], [17, 8], [20, 8], [30, 8],
  ];
  for (const [level, added] of levels) {
    const preview = rollLevelPreview(wizard, roll, level);
    assert.equal(preview.level, level);
    assert.equal(preview.base, '3d4+3');
    assert.equal(preview.additions.length, 1);
    assert.equal(preview.additions[0].count, added);
    assert.equal(preview.dynamic, true);
    assert.equal(preview.available, true);
    assert.equal(preview.formula,
      '3d4+3' + (added === 0 ? '' : added === 1
        ? ' + (1d4+1)' : ' + ' + added + ' × (1d4+1)'),
      'current level ' + level);
  }
  // The preview must not rewrite the native graph, previous v1/v2 QR, or backups.
  assert.equal(JSON.stringify(wizard), originalJson);
  validateDocument(wizard);
  assert.equal(envelope(wizard).formatVersion, 2);
});

test('Wizard highest-slot Fireball progression respects starting character level and cap', () => {
  const wizard = instantiateTemplate(2);
  const fireball = wizard.rolls.find(r=>r.name.startsWith('Fireball · highest unlocked slot'));
  assert.ok(fireball);
  assert.equal(rollLevelPreview(wizard, fireball, 4).available, false);
  assert.equal(rollLevelPreview(wizard, fireball, 5).formula, '8d6');
  assert.equal(rollLevelPreview(wizard, fireball, 7).formula, '8d6 + (1d6)');
  assert.equal(rollLevelPreview(wizard, fireball, 17).formula, '8d6 + 6 × (1d6)');
  assert.equal(rollLevelPreview(wizard, fireball, 20).formula, '8d6 + 6 × (1d6)');
  // A normal third-level Fireball never automatically upcasts.
  const base = wizard.rolls.find(r=>r.name==='Fireball (target Dex save)');
  assert.equal(rollLevelPreview(wizard, base, 20).formula, '8d6');
  assert.equal(rollLevelPreview(wizard, base, 20).dynamic, false);
});

test('Legacy every-N and from-level rules are rendered with Android repeat semantics', () => {
  const doc = instantiateTemplate(2);
  const roll = {
    expression: '1d6',
    minimumLevel: 1,
    levelRules: [
      {id:'start',kind:'FROM_LEVEL',trigger:5,expression:'1d4'},
      {id:'legacy',kind:'EVERY_LEVELS',trigger:3,expression:'1d8'},
    ],
  };
  assert.equal(rollLevelPreview(doc,roll,1).formula,'1d6');
  assert.equal(rollLevelPreview(doc,roll,3).formula,'1d6 + (1d8)');
  assert.equal(rollLevelPreview(doc,roll,5).formula,'1d6 + (1d4) + (1d8)');
  assert.equal(rollLevelPreview(doc,roll,12).formula,'1d6 + (1d4) + 4 × (1d8)');
  assert.equal(rollLevelPreview(doc,roll,12).additions[0].count,1);
  assert.equal(rollLevelPreview(doc,roll,12).additions[1].count,4);
});

test('Read-only preview substitutes level and levelstep variables without renaming modifiers', () => {
  const wizard=instantiateTemplate(2);
  const levelDependent='1d10+{level}';
  assert.equal(resolveLevelTokens(levelDependent,wizard,1),'1d10+1');
  assert.equal(resolveLevelTokens(levelDependent,wizard,20),'1d10+20');
  assert.equal(resolveLevelTokens('2{level}+{Intelligence}',wizard,7),
    '2x7+' + wizard.modifiers.find(m=>m.name==='Intelligence').value);
  assert.equal(resolveLevelTokens('{level}d6',wizard,17),'17d6');
  assert.equal(resolveLevelTokens('1d20+{Proficiency}+{levelstep:5:9:13:17}',wizard,1),
    '1d20+' + wizard.modifiers.find(m=>m.name==='Proficiency').value + '+0');
  assert.equal(resolveLevelTokens('1d20+{Proficiency}+{levelstep:5:9:13:17}',wizard,17),
    '1d20+' + wizard.modifiers.find(m=>m.name==='Proficiency').value + '+4');
  assert.equal(resolveLevelTokens('1d6+{partId:some-id}',wizard,7),'1d6+{partId:some-id}');
  assert.equal(rollLevelPreview(wizard,{expression:levelDependent,levelRules:[]},7).formula,'1d10+7');
});

test('Native Roll Parts remain separate and changing import preview level is non-mutating', () => {
  const wizard=instantiateTemplate(2);
  const fireBolt=wizard.rolls.find(r=>r.name==='Fire Bolt · 2d10');
  assert.ok(fireBolt);
  const before=JSON.stringify(fireBolt);
  assert.equal(rollLevelPreview(wizard,fireBolt,1).available,false);
  const active=rollLevelPreview(wizard,fireBolt,7);
  assert.equal(active.available,true);
  assert.equal(active.dynamic,true);
  assert.match(active.formula,/1d20\+.*\+1\)/);
  assert.match(active.formula,/2d10/);
  assert.equal(JSON.stringify(fireBolt),before);
});
