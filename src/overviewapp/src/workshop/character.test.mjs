import test from 'node:test';
import assert from 'node:assert/strict';
import {
  templates, instantiateTemplate, validateDocument, envelope, documentFromEnvelope,
  encodeToken, decodeToken, importAsCopy, normalFormula, newPart, shareUrl,
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
  x.rolls[0].subgroups = [part];
  x.rolls[0].expression = normalFormula([part]);
  const decoded = documentFromEnvelope(envelope(x));
  const copy = importAsCopy(decoded);
  assert.notEqual(copy.rolls[0].subgroups[0].id, part.id);
  assert.equal(copy.rolls[0].expression, x.rolls[0].expression);
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
