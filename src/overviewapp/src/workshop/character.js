import pako from 'pako';
import { SYSTEM_STYLES, validateGuidedRoll } from './guided.js';

/**
 * Browser data stays in IndexedDB. A share link contains a compressed, versioned
 * snapshot in its fragment; GitHub Pages never receives the fragment in HTTP.
 */
export const FORMAT = 'dice-thrower-character';
export const FORMAT_VERSION = 1;
export const MAX_LINK_LENGTH = 2600;
const MAX_RAW_LENGTH = 128 * 1024;
const DB_NAME = 'dicethrower-workshop';
const STORE = 'characters';
const uid = () => crypto.randomUUID();

export function newCharacter(name = 'New character') {
  return {
    character: {
      id: uid(), name, level: 1, tag: '', order: 0, image: null, imageUri: null, diceTableImage: null,
      defaultDiceStyleId: null, secondaryDiceStyleId: null, diceTableTheme: 'ARCANE',
    },
    modifiers: [], groups: [], rolls: [], diceStyles: [],
  };
}

export function newModifier(characterId) {
  return { id: uid(), characterId, name: 'Modifier', value: 0, order: 0 };
}
export function newGroup(characterId) {
  return { id: uid(), characterId, name: 'New group', order: 0 };
}
export function newPart() {
  return { id: uid(), name: 'Part', expression: '1d20', operator: 'ADD',
    includeInNormalRoll: true, includeInDoubleRoll: false };
}
export function normalFormula(parts) {
  const active = parts.filter(part => part.includeInNormalRoll);
  return active.map((part, index) => {
    const term = '(' + part.expression.trim() + ')';
    return (index === 0 && part.operator === 'ADD') ? term
      : (part.operator === 'SUBTRACT' ? '-' : '+') + term;
  }).join('');
}
export function newRoll(characterId, groupId = null) {
  return { id: uid(), characterId, groupId, name: 'New roll', expression: '1d20',
    enabled: true, order: 0, minimumLevel: 1, doubleRollEnabled: false,
    subgroups: [], levelRules: [], effects: [], diceAppearance: { mode: 'CHARACTER_DEFAULT' } };
}
export function newLevelRule() {
  return { id: uid(), kind: 'FROM_LEVEL', trigger: 2, expression: '1d6' };
}
export function newStyle() {
  return { id: uid(), characterId: null, name: 'Custom dice', material: 'GLOSSY_RESIN',
    primaryColorArgb: -14384101, secondaryColorArgb: -11751600, order: 0 };
}

const isRecord = x => !!x && typeof x === 'object' && !Array.isArray(x);
const validName = x => typeof x === 'string' && x.trim().length > 0 && x.length <= 160;
const validId = x => typeof x === 'string' && x.length > 0 && x.length <= 180;
const exprPattern = /^[\d\s()+\-*\/xXdD.%{}\p{L}\p{M}_:]+$/u;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

export function validateDocument(doc) {
  assert(isRecord(doc) && isRecord(doc.character), 'Invalid character document');
  const c = doc.character;
  assert(validId(c.id) && validName(c.name), 'Invalid character name or ID');
  assert(Number.isInteger(c.level) && c.level >= 1 && c.level <= 9999, 'Invalid character level');
  for (const field of ['modifiers', 'groups', 'rolls', 'diceStyles']) {
    assert(Array.isArray(doc[field]) && doc[field].length <= 250, 'Invalid ' + field);
  }
  assert(doc.rolls.length <= 150, 'Too many rolls');
  const ids = new Set();
  const unique = (id) => { assert(validId(id) && !ids.has(id), 'Duplicate or invalid ID'); ids.add(id); };
  unique(c.id);
  for (const item of [...doc.modifiers, ...doc.groups, ...doc.rolls, ...doc.diceStyles]) {
    assert(isRecord(item), 'Invalid item');
    unique(item.id);
    if ('characterId' in item) assert(item.characterId === c.id || (doc.diceStyles.includes(item) && item.characterId == null), 'Foreign character reference');
    assert(validName(item.name), 'Invalid name');
  }
  const groupIds = new Set(doc.groups.map(g => g.id));
  const styleIds = new Set(doc.diceStyles.map(s => s.id));
  const allStyles = new Set([...styleIds, ...SYSTEM_STYLES.map(s => s.id)]);
  const checkStyle = id => assert(id == null || allStyles.has(id), 'Unknown dice style');
  checkStyle(c.defaultDiceStyleId); checkStyle(c.secondaryDiceStyleId);
  const names = new Set(['level']);
  for (const m of doc.modifiers) {
    assert(Number.isInteger(m.value) && Math.abs(m.value) <= 100000, 'Invalid modifier');
    const key = m.name.trim().toLowerCase();
    assert(!names.has(key), 'Duplicate modifier: ' + m.name);
    names.add(key);
  }
  const formula = text => {
    assert(typeof text === 'string' && text.length > 0 && text.length <= 1500 && exprPattern.test(text), 'Invalid dice expression');
    for (const match of text.matchAll(/\{([^{}]+)\}/g)) {
      const token = match[1].trim().toLowerCase();
      const segments = token.startsWith('levelstep:') ? token.slice('levelstep:'.length).split(':') : null;
      const milestoneValid = segments && segments.length >= 1 && segments.length <= 20 &&
        segments.every((value, i) => /^([2-9]|[1-9][0-9]{1,3})$/.test(value) &&
          Number(value) <= 9999 && (i === 0 || Number(value) > Number(segments[i-1])));
      assert(names.has(token) || token.startsWith('partId:') || milestoneValid,
        'Missing variable or invalid level milestone: ' + match[1]);
    }
  };
  const nestedIds = new Set();
  for (const roll of doc.rolls) {
    assert(roll.groupId == null || groupIds.has(roll.groupId), 'Unknown roll group');
    assert(Number.isInteger(roll.minimumLevel ?? 1) && (roll.minimumLevel ?? 1) >= 1 && (roll.minimumLevel ?? 1) <= 9999, 'Invalid roll minimum level');
    formula(roll.expression);
    assert(Array.isArray(roll.subgroups) && roll.subgroups.length <= 30, 'Invalid roll parts');
    assert(Array.isArray(roll.levelRules) && roll.levelRules.length <= 40, 'Invalid level rules');
    assert(Array.isArray(roll.effects) && roll.effects.length <= 40, 'Invalid effects');
    if (roll.subgroups.length) {
      assert(roll.subgroups.some(s => s.includeInNormalRoll !== false), 'At least one normal Part needed');
      assert(normalFormula(roll.subgroups) === roll.expression, 'Roll parts and formula do not match');
    }
    for (const part of roll.subgroups) {
      assert(validId(part.id) && !nestedIds.has(part.id), 'Invalid/duplicate Part ID'); nestedIds.add(part.id);
      assert(validName(part.name), 'Invalid Part name');
      formula(part.expression);
    }
    for (const rule of roll.levelRules) {
      assert(validId(rule.id) && !nestedIds.has(rule.id), 'Invalid/duplicate rule ID'); nestedIds.add(rule.id);
      assert(Number.isInteger(rule.trigger) && rule.trigger > 0, 'Invalid level rule');
      formula(rule.expression);
    }
    // Preserve the complete native effect/appearance schema, even if advanced
    // editing is performed in Android instead of the browser.
    const appearance = roll.diceAppearance || {};
    checkStyle(appearance.styleId);
    for (const item of appearance.subgroupStyleIds || []) {
      assert(roll.subgroups.some(part => part.id === item.subgroupId), 'Unknown appearance Part');
      checkStyle(item.styleId);
    }
    for (const item of appearance.perDieStyleIds || []) checkStyle(item.styleId);
    for (const id of appearance.randomStyleIds || []) checkStyle(id);
    validateGuidedRoll(roll, doc.modifiers.map(m => m.name), allStyles);
  }
  assert(new TextEncoder().encode(JSON.stringify(doc)).length <= MAX_RAW_LENGTH, 'Character too large');
  return doc;
}

export function envelope(doc) {
  validateDocument(doc);
  const { character, modifiers, groups, rolls, diceStyles } = doc;
  return {
    format: FORMAT, formatVersion: FORMAT_VERSION,
    data: { version: 16, characters: [{ ...character, image: null, imageUri: null, diceTableImage: null }],
      modifiers, groups, rolls, diceStyles, logs: [], characterSyncMetadata: [] },
  };
}
export function documentFromEnvelope(packet) {
  assert(isRecord(packet) && packet.format === FORMAT && packet.formatVersion === FORMAT_VERSION, 'Unsupported character link');
  assert(isRecord(packet.data) && packet.data.characters?.length === 1, 'Link must contain exactly one character');
  assert(!packet.data.logs?.length && !packet.data.characterSyncMetadata?.length, 'Share contains private history or sync data');
  const doc = { character: packet.data.characters[0], modifiers: packet.data.modifiers || [],
    groups: packet.data.groups || [], rolls: packet.data.rolls || [], diceStyles: packet.data.diceStyles || [] };
  assert(doc.character.image == null && doc.character.diceTableImage == null && doc.character.imageUri == null, 'Images are not supported in links');
  return validateDocument(doc);
}
function toBase64(bytes) {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000)
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function fromBase64(string) {
  assert(/^[A-Za-z0-9_-]+$/.test(string) && string.length <= 120000, 'Invalid link payload');
  const base = string.replace(/-/g, '+').replace(/_/g, '/');
  const decoded = atob(base + '='.repeat((4 - base.length % 4) % 4));
  return Uint8Array.from(decoded, value => value.charCodeAt(0));
}
export function encodeToken(doc) {
  const compressed = pako.deflate(new TextEncoder().encode(JSON.stringify(envelope(doc))), { level: 9 });
  return 'v1.' + toBase64(compressed);
}
export function decodeToken(token) {
  assert(typeof token === 'string' && token.startsWith('v1.'), 'Unsupported link');
  // Inflate in bounded chunks to reject malicious highly-compressible zip bombs.
  const inflater = new pako.Inflate({ chunkSize: 4096 });
  const chunks = [];
  let total = 0;
  inflater.onData = (chunk) => {
    total += chunk.length;
    assert(total <= MAX_RAW_LENGTH, 'Character too large');
    chunks.push(chunk);
  };
  inflater.push(fromBase64(token.slice(3)), true);
  assert(!inflater.err, 'Invalid compressed character');
  const bytes = new Uint8Array(total);
  let position = 0;
  for (const chunk of chunks) { bytes.set(chunk, position); position += chunk.length; }
  return documentFromEnvelope(JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)));
}
/**
 * Canonical shape fingerprint for version-pinned, unmodified local starter
 * presets. All IDs (including nested references) are normalized by definition
 * order. Any changed rule/style/variable/description prevents short-template
 * sharing, never silently drops edits from a receiver's payload.
 */
function canonicalPresetShape(doc) {
  const map = new Map();
  const put = (object, key) => { if (object?.id) map.set(object.id, key); };
  put(doc.character, '$character');
  for (const field of ['modifiers','groups','rolls','diceStyles']) {
    (doc[field] || []).forEach((item, i) => put(item, '
export function androidUrl(doc) { return 'dicethrower://import?data=' + encodeURIComponent(encodeToken(doc)); }

/** Recipients can rename/relevel a copy without altering a sender's document. */
export function withImportPreferences(source, name, level) {
  validateDocument(source);
  const copy = structuredClone(source);
  copy.character.name = typeof name === 'string' ? name.trim() : '';
  copy.character.level = Number(level);
  return validateDocument(copy);
}


function db() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains(STORE))
      request.result.createObjectStore(STORE, { keyPath: 'character.id' }); };
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
}
async function transaction(mode, run) {
  const database = await db();
  try {
    return await new Promise((resolve, reject) => {
      const tx = database.transaction(STORE, mode);
      const request = run(tx.objectStore(STORE));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally { database.close(); }
}
export const listSaved = () => transaction('readonly', store => store.getAll());
export const saveDocument = doc => { validateDocument(doc); return transaction('readwrite', store => store.put(doc)); };
export const removeDocument = id => transaction('readwrite', store => store.delete(id));

export function importAsCopy(document) {
  // The browser uses fresh IDs for all owned definitions to avoid overwriting an
  // existing saved character (including when opening the same QR twice).
  validateDocument(document);
  const doc = structuredClone(document);
  const cId = uid();
  const map = new Map([[doc.character.id, cId]]);
  for (const list of [doc.modifiers, doc.groups, doc.rolls, doc.diceStyles]) {
    for (const item of list) map.set(item.id, uid());
  }
  const remap = (id) => map.get(id) || id;
  doc.character.id = cId;
  doc.character.defaultDiceStyleId = doc.character.defaultDiceStyleId && remap(doc.character.defaultDiceStyleId);
  doc.character.secondaryDiceStyleId = doc.character.secondaryDiceStyleId && remap(doc.character.secondaryDiceStyleId);
  for (const list of [doc.modifiers, doc.groups, doc.rolls, doc.diceStyles]) for (const item of list) {
    item.id = remap(item.id);
    if (item.characterId) item.characterId = cId;
  }
  for (const roll of doc.rolls) {
    roll.groupId = roll.groupId && remap(roll.groupId);
    for (const part of roll.subgroups) { map.set(part.id, uid()); part.id = remap(part.id); }
    for (const rule of roll.levelRules) rule.id = uid();
    if (roll.diceAppearance) {
      const a = roll.diceAppearance;
      if (a.styleId) a.styleId = remap(a.styleId);
      if (a.subgroupStyleIds) a.subgroupStyleIds = a.subgroupStyleIds.map(x => ({ subgroupId: remap(x.subgroupId), styleId: remap(x.styleId) }));
      if (a.perDieStyleIds) a.perDieStyleIds = a.perDieStyleIds.map(x => ({ ...x, styleId: remap(x.styleId) }));
      if (a.randomStyleIds) a.randomStyleIds = a.randomStyleIds.map(remap);
    }
    // Advanced effects can contain Part IDs in expressions and conditions.
    const deepRemap = obj => {
      if (typeof obj === 'string') return obj.replace(/\{partId:([^{}]+)\}/g, (_, id) => '{partId:' + remap(id) + '}');
      if (Array.isArray(obj)) return obj.map(deepRemap);
      if (!isRecord(obj)) return obj;
      return Object.fromEntries(Object.entries(obj).map(([k, v]) => [k,
        (['partId', 'targetPartId'].includes(k) && typeof v === 'string') ? remap(v) :
        (k === 'id' && typeof v === 'string') ? uid() : deepRemap(v)]));
    };
    roll.effects = deepRemap(roll.effects);
  }
  return validateDocument(doc);
}

const starterSpecs = [
  ['D&D 5e (2014) · Fighter', 'Aric · Level 1 Fighter',
    'D&D 5e sample, Strength +3 and proficiency +2', 1,
    [['Strength', 3], ['Dexterity', 1], ['Constitution', 2], ['Proficiency', 2]],
    [
      ['Combat', [
        ['Longsword', [
          ['Attack', '1d20+{Strength}+{Proficiency}'],
          ['Damage', '1d8+{Strength}'],
        ], 'DND5E_CRITICAL'],
        ['Initiative', '1d20+{Dexterity}'],
      ]],
      ['Skills & saves', [
        ['Athletics', '1d20+{Strength}+{Proficiency}'],
        ['Strength save', '1d20+{Strength}+{Proficiency}'],
        ['Constitution save', '1d20+{Constitution}+{Proficiency}'],
      ]],
      ['Recovery', [['Second Wind · heal', '1d10+{level}']]],
    ],
  ],
  ['Fantasy · Rogue', 'Nim · Shadow Rogue', 'A nimble adventurer', 1, [['Agility', 3], ['Skill', 2]], [['Combat', [['Quick strike', '1d20+{Agility}+{Skill}'], ['Sneak damage', '1d6+{Agility}']]], ['Skills', [['Stealth', '1d20+{Agility}+{Skill}']]]]],
  ['Fantasy · Mage', 'Lyra · Spellcaster', 'An elemental mage', 1, [['Magic', 3]], [['Spells', [['Arcane bolt', '1d20+{Magic}'], ['Fire spark', '2d6+{Magic}']]], ['Checks', [['Arcana', '1d20+{Magic}']]]]],
  ['Fantasy · Ranger', 'Bryn · Ranger', 'A woodland explorer', 1,
    [['Agility', 2], ['Tracking', 3]],
    [
      ['Combat', [
        ['Bow', [
          ['Attack', '1d20+{Agility}'],
          ['Damage', '1d8+{Agility}'],
        ]],
      ]],
      ['Exploration', [['Track', '1d20+{Tracking}']]],
    ],
  ],
  ['Fantasy · Cleric', 'Sol · Healer', 'A protective spellcaster', 1, [['Faith', 3]], [['Magic', [['Healing word', '1d4+{Faith}'], ['Radiant strike', '1d8+{Faith}']]], ['Checks', [['Insight', '1d20+{Faith}']]]]],
  ['Fantasy · Paladin', 'Thane · Guardian', 'A steadfast champion', 1,
    [['Might', 3]],
    [
      ['Combat', [
        ['Sword', [
          ['Attack', '1d20+{Might}'],
          ['Damage', '1d8+{Might}'],
        ]],
      ]],
      ['Defense', [['Resolve', '1d20+{Might}']]],
    ],
  ],
  ['Sci-fi · Pilot', 'Nova · Pilot', 'Spacefaring character template', 1, [['Piloting', 4], ['Tech', 2]], [['Flight', [['Hard maneuver', '1d20+{Piloting}'], ['Evasive action', '2d6+{Piloting}']]], ['Systems', [['Repair', '1d20+{Tech}']]]]],
  ['Sci-fi · Engineer', 'Echo · Engineer', 'Systems specialist', 1, [['Tech', 4]], [['Engineering', [['Diagnose', '1d20+{Tech}'], ['Field repair', '2d6+{Tech}']]]]],
  ['Adventure · Explorer', 'Iris · Explorer', 'General purpose exploration', 1, [['Perception', 3], ['Agility', 2]], [['Exploration', [['Notice', '1d20+{Perception}'], ['Climb', '1d20+{Agility}']]]]],
  ['Adventure · Bard', 'Orin · Performer', 'Creative social adventurer', 1, [['Charm', 3], ['Art', 2]], [['Social', [['Persuade', '1d20+{Charm}'], ['Performance', '1d20+{Art}']]], ['Support', [['Encourage', '1d6+{Charm}']]]]],
];
/**
 * Dice Thrower is game-agnostic: only the explicitly labelled D&D 5e fighter
 * gets this game-specific critical rule. Attack is checked on its unmodified
 * d20 face (DICE_ONLY), not on 1d20 + Strength + Proficiency.
 *
 * A 5e critical rolls another damage die; it never multiplies the already
 * rolled value or the Strength modifier. ROLL_AFTER samples independently in
 * Android's logical engine and adds only the sampled dice to Damage.
 */
export function createDnd5eCriticalEffect(attackPartId, damagePartId, damageDieExpression = '1d8') {
  return {
    id: uid(), name: 'Critical hit (natural 20)', type: 'BONUS',
    order: 0, enabled: true, stopFollowingEffects: false,
    activationGroups: [{
      id: uid(),
      conditions: [{
        id: uid(), source: 'PART', partId: attackPartId, variableName: null,
        scope: 'DICE_ONLY', comparison: 'EQUAL', threshold: '20',
      }],
    }],
    actions: [{
      id: uid(), kind: 'ROLL_AFTER', targetPartId: damagePartId,
      scope: 'DICE_ONLY', expression: damageDieExpression,
    }],
  };
}

function createStarterRoll(characterId, groupId, name, formulaOrParts, rule, order) {
  const roll = { ...newRoll(characterId, groupId), name, order };
  if (typeof formulaOrParts === 'string') {
    roll.expression = formulaOrParts;
    return roll;
  }
  const parts = formulaOrParts.map(([partName, expression]) => ({
    ...newPart(), name: partName, expression,
  }));
  roll.subgroups = parts;
  roll.expression = normalFormula(parts);
  if (rule === 'DND5E_CRITICAL') {
    const damageDice = '1d8'; // one-handed level-one longsword
    roll.effects = [createDnd5eCriticalEffect(parts[0].id, parts[1].id, damageDice)];
  }
  return roll;
}

/**
 * Conservative, opt-in migration for pre-fix starter characters saved in
 * IndexedDB. Only the exact original pair with no customized Part, effect,
 * appearance, or level rules is eligible. Never change data just by loading it.
 * Other character fields, group IDs and unrelated Rolls are left untouched.
 */
const legacyWeaponPairs = [
  { tag: 'D&D 5e sample, Strength +3 and proficiency +2',
    attack: 'Longsword · attack', damage: 'Longsword · damage', name: 'Longsword',
    attackExpr: '1d20+{Strength}+{Proficiency}', damageExpr: '1d8+{Strength}', rule: 'DND5E_CRITICAL' },
  { tag: 'A woodland explorer',
    attack: 'Bow shot', damage: 'Bow damage', name: 'Bow',
    attackExpr: '1d20+{Agility}', damageExpr: '1d8+{Agility}' },
  { tag: 'A steadfast champion',
    attack: 'Sword attack', damage: 'Sword damage', name: 'Sword',
    attackExpr: '1d20+{Might}', damageExpr: '1d8+{Might}' },
];
function matchingLegacyPair(doc) {
  if (!doc?.character || !Array.isArray(doc.rolls)) return null;
  for (const definition of legacyWeaponPairs) {
    if (doc.character.tag !== definition.tag) continue;
    const attack = doc.rolls.filter(r => r.name === definition.attack);
    const damage = doc.rolls.filter(r => r.name === definition.damage);
    if (attack.length !== 1 || damage.length !== 1) continue;
    const [a] = attack, [d] = damage;
    if (a.characterId !== doc.character.id || d.characterId !== doc.character.id ||
        a.groupId == null || a.groupId !== d.groupId ||
        a.expression !== definition.attackExpr || d.expression !== definition.damageExpr) continue;
    // Reject unknown/native-only Roll fields rather than discarding settings
    // from an imported or hand-edited legacy Roll during the upgrade.
    const legacyFields = new Set([
      'id', 'characterId', 'groupId', 'name', 'expression', 'enabled', 'order',
      'minimumLevel', 'doubleRollEnabled', 'subgroups', 'levelRules', 'effects',
      'diceAppearance',
    ]);
    const appearanceFields = new Set([
      'mode', 'styleId', 'subgroupStyleIds', 'perDieStyleIds', 'randomStyleIds',
    ]);
    const unmodified = r => Object.keys(r).every(key => legacyFields.has(key)) &&
      (!r.diceAppearance || Object.keys(r.diceAppearance)
        .every(key => appearanceFields.has(key))) &&
      r.enabled !== false && !r.doubleRollEnabled &&
      (r.minimumLevel ?? 1) === 1 &&
      (!r.subgroups || r.subgroups.length === 0) &&
      (!r.levelRules || r.levelRules.length === 0) &&
      (!r.effects || r.effects.length === 0) &&
      (!r.diceAppearance || (
        r.diceAppearance.mode === 'CHARACTER_DEFAULT' &&
        !r.diceAppearance.styleId &&
        !r.diceAppearance.subgroupStyleIds?.length &&
        !r.diceAppearance.perDieStyleIds?.length &&
        !r.diceAppearance.randomStyleIds?.length
      ));
    if (unmodified(a) && unmodified(d)) return { definition, attackId:a.id, damageId:d.id };
  }
  return null;
}
export const canUpgradeLegacyStarterWeaponRolls = doc => matchingLegacyPair(doc) !== null;
export function upgradeLegacyStarterWeaponRolls(doc) {
  const match = matchingLegacyPair(doc);
  if (!match) return null;
  const upgraded = structuredClone(doc);
  const attackIndex = upgraded.rolls.findIndex(r => r.id === match.attackId);
  const oldAttack = upgraded.rolls[attackIndex];
  const fresh = createStarterRoll(upgraded.character.id, oldAttack.groupId,
    match.definition.name,
    [['Attack', match.definition.attackExpr], ['Damage', match.definition.damageExpr]],
    match.definition.rule, oldAttack.order);
  // Keep the original attack Roll ID and all unrelated character data.
  upgraded.rolls[attackIndex] = { ...oldAttack, ...fresh, id: oldAttack.id };
  upgraded.rolls = upgraded.rolls.filter(r => r.id !== match.damageId);
  return validateDocument(upgraded);
}

/**
 * Explicit D&D Fifth Edition (2014) class progression only. These class-feature
 * descriptions are editorial reference data for the website, never dice RNG rules.
 * Player-selected subclass/ASI and Fighting Style choices are intentionally not
 * predetermined, since all Fighters need to make those decisions themselves.
 */
export const FIGHTER_2014_PROFICIENCY_STEPS = 'levelstep:5:9:13:17';
export const FIGHTER_2014_LEVELS = [
  ['Fighting Style, Second Wind'],
  ['Action Surge (1 use)'],
  ['Martial Archetype (choose a subclass)'],
  ['Ability Score Improvement (player chooses)'],
  ['Extra Attack (2 attacks per Attack action)'],
  ['Ability Score Improvement (player chooses)'],
  ['Martial Archetype feature (subclass choice)'],
  ['Ability Score Improvement (player chooses)'],
  ['Indomitable (1 use)'],
  ['Martial Archetype feature (subclass choice)'],
  ['Extra Attack (3 attacks per Attack action)'],
  ['Ability Score Improvement (player chooses)'],
  ['Indomitable (2 uses)'],
  ['Ability Score Improvement (player chooses)'],
  ['Martial Archetype feature (subclass choice)'],
  ['Ability Score Improvement (player chooses)'],
  ['Action Surge (2 uses), Indomitable (3 uses)'],
  ['Martial Archetype feature (subclass choice)'],
  ['Ability Score Improvement (player chooses)'],
  ['Extra Attack (4 attacks per Attack action)'],
].map(([features], index) => ({
  level: index + 1,
  proficiency: 2 + [5, 9, 13, 17].filter(start => index + 1 >= start).length,
  features,
}));
export function buildFighter2014Progression(doc) {
  const fighter = structuredClone(doc);
  const longsword = fighter.rolls.find(r => r.name === 'Longsword');
  if (!longsword || longsword.subgroups.length !== 2 || !longsword.effects.length)
    throw new Error('Not a supported level-one Fighter template');
  const marker = '{' + FIGHTER_2014_PROFICIENCY_STEPS + '}';
  const enhance = expression => expression.replaceAll('{Proficiency}', '{Proficiency}+' + marker);
  longsword.subgroups[0].expression = enhance(longsword.subgroups[0].expression);
  longsword.expression = normalFormula(longsword.subgroups);
  for (const roll of fighter.rolls.filter(r => ['Athletics','Strength save','Constitution save'].includes(r.name))) {
    roll.expression = enhance(roll.expression);
  }
  // Four attacks at level 20: each attack/damage pair has its own independent
  // natural-20 effect, so no dice/result is ever multiplied incorrectly.
  for (const [minimumLevel, count] of [[5, 2], [11, 3], [20, 4]]) {
    const roll = {
      ...newRoll(fighter.character.id, longsword.groupId),
      name: 'Longsword · ' + count + ' attacks',
      minimumLevel, order: fighter.rolls.length,
    };
    for (let index = 1; index <= count; index++) {
      const attack = { ...newPart(), name: 'Attack ' + index,
        expression: longsword.subgroups[0].expression };
      const damage = { ...newPart(), name: 'Damage ' + index,
        expression: longsword.subgroups[1].expression };
      roll.subgroups.push(attack, damage);
      const critical = createDnd5eCriticalEffect(attack.id, damage.id, '1d8');
      critical.order = index - 1;
      critical.name = 'Critical hit · Attack ' + index;
      roll.effects.push(critical);
    }
    roll.expression = normalFormula(roll.subgroups);
    fighter.rolls.push(roll);
  }
  return validateDocument(fighter);
}

/** New templates have all future gated Rolls prepared; existing saves are never rewritten. */
export const templates = starterSpecs.map(([category, name, description], index) => ({ category, name, description, index }));
export function instantiateTemplate(index) {
  const spec = starterSpecs[index];
  if (!spec) throw new Error('Unknown template');
  const [, name, tag, level, modifiers, sections] = spec;
  const doc = newCharacter(name);
  doc.character.tag = tag; doc.character.level = level;
  doc.modifiers = modifiers.map(([m, v], order) => ({ id: uid(), characterId: doc.character.id, name: m, value: v, order }));
  sections.forEach(([groupName, definitions], order) => {
    const group = { id: uid(), characterId: doc.character.id, name: groupName, order };
    doc.groups.push(group);
    definitions.forEach(([rollName, formulaOrParts, rule]) => {
      doc.rolls.push(createStarterRoll(doc.character.id, group.id, rollName,
        formulaOrParts, rule, doc.rolls.length));
    });
  });
  return index === 0 ? buildFighter2014Progression(doc) : validateDocument(doc);
}
 + field + i));
  }
  (doc.rolls || []).forEach((roll, i) => {
    (roll.subgroups || []).forEach((p,j) => put(p, '$part'+i+'_'+j));
    (roll.levelRules || []).forEach((p,j) => put(p, '$rule'+i+'_'+j));
    (roll.effects || []).forEach((effect,j) => {
      put(effect, '$effect'+i+'_'+j);
      (effect.activationGroups || []).forEach((group,k) => {
        put(group, '$group'+i+'_'+j+'_'+k);
        (group.conditions || []).forEach((c,m) => put(c, '$condition'+i+'_'+j+'_'+k+'_'+m));
      });
      (effect.actions || []).forEach((a,k) => put(a, '$action'+i+'_'+j+'_'+k));
    });
  });
  const normalize = (value) => {
    if (typeof value === 'string') return map.get(value) || value;
    if (Array.isArray(value)) return value.map(normalize);
    if (value && typeof value === 'object') return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [key, normalize(entry)]));
    return value;
  };
  const normalized = structuredClone(doc);
  normalized.character.name = '';
  normalized.character.level = 1;
  return JSON.stringify(normalize(normalized));
}
function isOriginal2014Fighter(doc) {
  if (!doc?.character?.tag?.startsWith('D&D 5e sample')) return false;
  try { return canonicalPresetShape(doc) === canonicalPresetShape(instantiateTemplate(0)); }
  catch { return false; }
}
const FIGHTER_TEMPLATE_LINK_VERSION = 'fighter2014v1';
export function shareUrl(doc, base = window.location.href) {
  validateDocument(doc);
  const url = new URL(base);
  url.hash = 'character=' + encodeToken(doc);
  if (url.href.length <= MAX_LINK_LENGTH) return url.href;
  // A versioned, offline reconstruction recipe is safe only for an untouched
  // official template. All customized documents still need JSON file fallback.
  if (isOriginal2014Fighter(doc)) {
    url.hash = 'template=' + FIGHTER_TEMPLATE_LINK_VERSION +
      '&name=' + encodeURIComponent(doc.character.name) +
      '&level=' + doc.character.level;
    if (url.href.length <= MAX_LINK_LENGTH) return url.href;
  }
  throw new Error('Link too long for a reliable QR code. Export the JSON file instead.');
}
export function readShareHash(hash = window.location.hash) {
  const compact = hash.match(/^#template=fighter2014v1&name=([^&]+)&level=([0-9]+)$/);
  if (compact) return withImportPreferences(
    instantiateTemplate(0),
    decodeURIComponent(compact[1]),
    Number(compact[2]),
  );
  const match = hash.match(/^#character=(v1\.[A-Za-z0-9_-]+)$/);
  if (!match) return null;
  return decodeToken(match[1]);
}
export function androidUrl(doc) { return 'dicethrower://import?data=' + encodeURIComponent(encodeToken(doc)); }

/** Recipients can rename/relevel a copy without altering a sender's document. */
export function withImportPreferences(source, name, level) {
  validateDocument(source);
  const copy = structuredClone(source);
  copy.character.name = typeof name === 'string' ? name.trim() : '';
  copy.character.level = Number(level);
  return validateDocument(copy);
}


function db() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains(STORE))
      request.result.createObjectStore(STORE, { keyPath: 'character.id' }); };
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
}
async function transaction(mode, run) {
  const database = await db();
  try {
    return await new Promise((resolve, reject) => {
      const tx = database.transaction(STORE, mode);
      const request = run(tx.objectStore(STORE));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally { database.close(); }
}
export const listSaved = () => transaction('readonly', store => store.getAll());
export const saveDocument = doc => { validateDocument(doc); return transaction('readwrite', store => store.put(doc)); };
export const removeDocument = id => transaction('readwrite', store => store.delete(id));

export function importAsCopy(document) {
  // The browser uses fresh IDs for all owned definitions to avoid overwriting an
  // existing saved character (including when opening the same QR twice).
  validateDocument(document);
  const doc = structuredClone(document);
  const cId = uid();
  const map = new Map([[doc.character.id, cId]]);
  for (const list of [doc.modifiers, doc.groups, doc.rolls, doc.diceStyles]) {
    for (const item of list) map.set(item.id, uid());
  }
  const remap = (id) => map.get(id) || id;
  doc.character.id = cId;
  doc.character.defaultDiceStyleId = doc.character.defaultDiceStyleId && remap(doc.character.defaultDiceStyleId);
  doc.character.secondaryDiceStyleId = doc.character.secondaryDiceStyleId && remap(doc.character.secondaryDiceStyleId);
  for (const list of [doc.modifiers, doc.groups, doc.rolls, doc.diceStyles]) for (const item of list) {
    item.id = remap(item.id);
    if (item.characterId) item.characterId = cId;
  }
  for (const roll of doc.rolls) {
    roll.groupId = roll.groupId && remap(roll.groupId);
    for (const part of roll.subgroups) { map.set(part.id, uid()); part.id = remap(part.id); }
    for (const rule of roll.levelRules) rule.id = uid();
    if (roll.diceAppearance) {
      const a = roll.diceAppearance;
      if (a.styleId) a.styleId = remap(a.styleId);
      if (a.subgroupStyleIds) a.subgroupStyleIds = a.subgroupStyleIds.map(x => ({ subgroupId: remap(x.subgroupId), styleId: remap(x.styleId) }));
      if (a.perDieStyleIds) a.perDieStyleIds = a.perDieStyleIds.map(x => ({ ...x, styleId: remap(x.styleId) }));
      if (a.randomStyleIds) a.randomStyleIds = a.randomStyleIds.map(remap);
    }
    // Advanced effects can contain Part IDs in expressions and conditions.
    const deepRemap = obj => {
      if (typeof obj === 'string') return obj.replace(/\{partId:([^{}]+)\}/g, (_, id) => '{partId:' + remap(id) + '}');
      if (Array.isArray(obj)) return obj.map(deepRemap);
      if (!isRecord(obj)) return obj;
      return Object.fromEntries(Object.entries(obj).map(([k, v]) => [k,
        (['partId', 'targetPartId'].includes(k) && typeof v === 'string') ? remap(v) :
        (k === 'id' && typeof v === 'string') ? uid() : deepRemap(v)]));
    };
    roll.effects = deepRemap(roll.effects);
  }
  return validateDocument(doc);
}

const starterSpecs = [
  ['D&D 5e (2014) · Fighter', 'Aric · Level 1 Fighter',
    'D&D 5e sample, Strength +3 and proficiency +2', 1,
    [['Strength', 3], ['Dexterity', 1], ['Constitution', 2], ['Proficiency', 2]],
    [
      ['Combat', [
        ['Longsword', [
          ['Attack', '1d20+{Strength}+{Proficiency}'],
          ['Damage', '1d8+{Strength}'],
        ], 'DND5E_CRITICAL'],
        ['Initiative', '1d20+{Dexterity}'],
      ]],
      ['Skills & saves', [
        ['Athletics', '1d20+{Strength}+{Proficiency}'],
        ['Strength save', '1d20+{Strength}+{Proficiency}'],
        ['Constitution save', '1d20+{Constitution}+{Proficiency}'],
      ]],
      ['Recovery', [['Second Wind · heal', '1d10+{level}']]],
    ],
  ],
  ['Fantasy · Rogue', 'Nim · Shadow Rogue', 'A nimble adventurer', 1, [['Agility', 3], ['Skill', 2]], [['Combat', [['Quick strike', '1d20+{Agility}+{Skill}'], ['Sneak damage', '1d6+{Agility}']]], ['Skills', [['Stealth', '1d20+{Agility}+{Skill}']]]]],
  ['Fantasy · Mage', 'Lyra · Spellcaster', 'An elemental mage', 1, [['Magic', 3]], [['Spells', [['Arcane bolt', '1d20+{Magic}'], ['Fire spark', '2d6+{Magic}']]], ['Checks', [['Arcana', '1d20+{Magic}']]]]],
  ['Fantasy · Ranger', 'Bryn · Ranger', 'A woodland explorer', 1,
    [['Agility', 2], ['Tracking', 3]],
    [
      ['Combat', [
        ['Bow', [
          ['Attack', '1d20+{Agility}'],
          ['Damage', '1d8+{Agility}'],
        ]],
      ]],
      ['Exploration', [['Track', '1d20+{Tracking}']]],
    ],
  ],
  ['Fantasy · Cleric', 'Sol · Healer', 'A protective spellcaster', 1, [['Faith', 3]], [['Magic', [['Healing word', '1d4+{Faith}'], ['Radiant strike', '1d8+{Faith}']]], ['Checks', [['Insight', '1d20+{Faith}']]]]],
  ['Fantasy · Paladin', 'Thane · Guardian', 'A steadfast champion', 1,
    [['Might', 3]],
    [
      ['Combat', [
        ['Sword', [
          ['Attack', '1d20+{Might}'],
          ['Damage', '1d8+{Might}'],
        ]],
      ]],
      ['Defense', [['Resolve', '1d20+{Might}']]],
    ],
  ],
  ['Sci-fi · Pilot', 'Nova · Pilot', 'Spacefaring character template', 1, [['Piloting', 4], ['Tech', 2]], [['Flight', [['Hard maneuver', '1d20+{Piloting}'], ['Evasive action', '2d6+{Piloting}']]], ['Systems', [['Repair', '1d20+{Tech}']]]]],
  ['Sci-fi · Engineer', 'Echo · Engineer', 'Systems specialist', 1, [['Tech', 4]], [['Engineering', [['Diagnose', '1d20+{Tech}'], ['Field repair', '2d6+{Tech}']]]]],
  ['Adventure · Explorer', 'Iris · Explorer', 'General purpose exploration', 1, [['Perception', 3], ['Agility', 2]], [['Exploration', [['Notice', '1d20+{Perception}'], ['Climb', '1d20+{Agility}']]]]],
  ['Adventure · Bard', 'Orin · Performer', 'Creative social adventurer', 1, [['Charm', 3], ['Art', 2]], [['Social', [['Persuade', '1d20+{Charm}'], ['Performance', '1d20+{Art}']]], ['Support', [['Encourage', '1d6+{Charm}']]]]],
];
/**
 * Dice Thrower is game-agnostic: only the explicitly labelled D&D 5e fighter
 * gets this game-specific critical rule. Attack is checked on its unmodified
 * d20 face (DICE_ONLY), not on 1d20 + Strength + Proficiency.
 *
 * A 5e critical rolls another damage die; it never multiplies the already
 * rolled value or the Strength modifier. ROLL_AFTER samples independently in
 * Android's logical engine and adds only the sampled dice to Damage.
 */
export function createDnd5eCriticalEffect(attackPartId, damagePartId, damageDieExpression = '1d8') {
  return {
    id: uid(), name: 'Critical hit (natural 20)', type: 'BONUS',
    order: 0, enabled: true, stopFollowingEffects: false,
    activationGroups: [{
      id: uid(),
      conditions: [{
        id: uid(), source: 'PART', partId: attackPartId, variableName: null,
        scope: 'DICE_ONLY', comparison: 'EQUAL', threshold: '20',
      }],
    }],
    actions: [{
      id: uid(), kind: 'ROLL_AFTER', targetPartId: damagePartId,
      scope: 'DICE_ONLY', expression: damageDieExpression,
    }],
  };
}

function createStarterRoll(characterId, groupId, name, formulaOrParts, rule, order) {
  const roll = { ...newRoll(characterId, groupId), name, order };
  if (typeof formulaOrParts === 'string') {
    roll.expression = formulaOrParts;
    return roll;
  }
  const parts = formulaOrParts.map(([partName, expression]) => ({
    ...newPart(), name: partName, expression,
  }));
  roll.subgroups = parts;
  roll.expression = normalFormula(parts);
  if (rule === 'DND5E_CRITICAL') {
    const damageDice = '1d8'; // one-handed level-one longsword
    roll.effects = [createDnd5eCriticalEffect(parts[0].id, parts[1].id, damageDice)];
  }
  return roll;
}

/**
 * Conservative, opt-in migration for pre-fix starter characters saved in
 * IndexedDB. Only the exact original pair with no customized Part, effect,
 * appearance, or level rules is eligible. Never change data just by loading it.
 * Other character fields, group IDs and unrelated Rolls are left untouched.
 */
const legacyWeaponPairs = [
  { tag: 'D&D 5e sample, Strength +3 and proficiency +2',
    attack: 'Longsword · attack', damage: 'Longsword · damage', name: 'Longsword',
    attackExpr: '1d20+{Strength}+{Proficiency}', damageExpr: '1d8+{Strength}', rule: 'DND5E_CRITICAL' },
  { tag: 'A woodland explorer',
    attack: 'Bow shot', damage: 'Bow damage', name: 'Bow',
    attackExpr: '1d20+{Agility}', damageExpr: '1d8+{Agility}' },
  { tag: 'A steadfast champion',
    attack: 'Sword attack', damage: 'Sword damage', name: 'Sword',
    attackExpr: '1d20+{Might}', damageExpr: '1d8+{Might}' },
];
function matchingLegacyPair(doc) {
  if (!doc?.character || !Array.isArray(doc.rolls)) return null;
  for (const definition of legacyWeaponPairs) {
    if (doc.character.tag !== definition.tag) continue;
    const attack = doc.rolls.filter(r => r.name === definition.attack);
    const damage = doc.rolls.filter(r => r.name === definition.damage);
    if (attack.length !== 1 || damage.length !== 1) continue;
    const [a] = attack, [d] = damage;
    if (a.characterId !== doc.character.id || d.characterId !== doc.character.id ||
        a.groupId == null || a.groupId !== d.groupId ||
        a.expression !== definition.attackExpr || d.expression !== definition.damageExpr) continue;
    // Reject unknown/native-only Roll fields rather than discarding settings
    // from an imported or hand-edited legacy Roll during the upgrade.
    const legacyFields = new Set([
      'id', 'characterId', 'groupId', 'name', 'expression', 'enabled', 'order',
      'minimumLevel', 'doubleRollEnabled', 'subgroups', 'levelRules', 'effects',
      'diceAppearance',
    ]);
    const appearanceFields = new Set([
      'mode', 'styleId', 'subgroupStyleIds', 'perDieStyleIds', 'randomStyleIds',
    ]);
    const unmodified = r => Object.keys(r).every(key => legacyFields.has(key)) &&
      (!r.diceAppearance || Object.keys(r.diceAppearance)
        .every(key => appearanceFields.has(key))) &&
      r.enabled !== false && !r.doubleRollEnabled &&
      (r.minimumLevel ?? 1) === 1 &&
      (!r.subgroups || r.subgroups.length === 0) &&
      (!r.levelRules || r.levelRules.length === 0) &&
      (!r.effects || r.effects.length === 0) &&
      (!r.diceAppearance || (
        r.diceAppearance.mode === 'CHARACTER_DEFAULT' &&
        !r.diceAppearance.styleId &&
        !r.diceAppearance.subgroupStyleIds?.length &&
        !r.diceAppearance.perDieStyleIds?.length &&
        !r.diceAppearance.randomStyleIds?.length
      ));
    if (unmodified(a) && unmodified(d)) return { definition, attackId:a.id, damageId:d.id };
  }
  return null;
}
export const canUpgradeLegacyStarterWeaponRolls = doc => matchingLegacyPair(doc) !== null;
export function upgradeLegacyStarterWeaponRolls(doc) {
  const match = matchingLegacyPair(doc);
  if (!match) return null;
  const upgraded = structuredClone(doc);
  const attackIndex = upgraded.rolls.findIndex(r => r.id === match.attackId);
  const oldAttack = upgraded.rolls[attackIndex];
  const fresh = createStarterRoll(upgraded.character.id, oldAttack.groupId,
    match.definition.name,
    [['Attack', match.definition.attackExpr], ['Damage', match.definition.damageExpr]],
    match.definition.rule, oldAttack.order);
  // Keep the original attack Roll ID and all unrelated character data.
  upgraded.rolls[attackIndex] = { ...oldAttack, ...fresh, id: oldAttack.id };
  upgraded.rolls = upgraded.rolls.filter(r => r.id !== match.damageId);
  return validateDocument(upgraded);
}

/**
 * Explicit D&D Fifth Edition (2014) class progression only. These class-feature
 * descriptions are editorial reference data for the website, never dice RNG rules.
 * Player-selected subclass/ASI and Fighting Style choices are intentionally not
 * predetermined, since all Fighters need to make those decisions themselves.
 */
export const FIGHTER_2014_PROFICIENCY_STEPS = 'levelstep:5:9:13:17';
export const FIGHTER_2014_LEVELS = [
  ['Fighting Style, Second Wind'],
  ['Action Surge (1 use)'],
  ['Martial Archetype (choose a subclass)'],
  ['Ability Score Improvement (player chooses)'],
  ['Extra Attack (2 attacks per Attack action)'],
  ['Ability Score Improvement (player chooses)'],
  ['Martial Archetype feature (subclass choice)'],
  ['Ability Score Improvement (player chooses)'],
  ['Indomitable (1 use)'],
  ['Martial Archetype feature (subclass choice)'],
  ['Extra Attack (3 attacks per Attack action)'],
  ['Ability Score Improvement (player chooses)'],
  ['Indomitable (2 uses)'],
  ['Ability Score Improvement (player chooses)'],
  ['Martial Archetype feature (subclass choice)'],
  ['Ability Score Improvement (player chooses)'],
  ['Action Surge (2 uses), Indomitable (3 uses)'],
  ['Martial Archetype feature (subclass choice)'],
  ['Ability Score Improvement (player chooses)'],
  ['Extra Attack (4 attacks per Attack action)'],
].map(([features], index) => ({
  level: index + 1,
  proficiency: 2 + [5, 9, 13, 17].filter(start => index + 1 >= start).length,
  features,
}));
export function buildFighter2014Progression(doc) {
  const fighter = structuredClone(doc);
  const longsword = fighter.rolls.find(r => r.name === 'Longsword');
  if (!longsword || longsword.subgroups.length !== 2 || !longsword.effects.length)
    throw new Error('Not a supported level-one Fighter template');
  const marker = '{' + FIGHTER_2014_PROFICIENCY_STEPS + '}';
  const enhance = expression => expression.replaceAll('{Proficiency}', '{Proficiency}+' + marker);
  longsword.subgroups[0].expression = enhance(longsword.subgroups[0].expression);
  longsword.expression = normalFormula(longsword.subgroups);
  for (const roll of fighter.rolls.filter(r => ['Athletics','Strength save','Constitution save'].includes(r.name))) {
    roll.expression = enhance(roll.expression);
  }
  // Four attacks at level 20: each attack/damage pair has its own independent
  // natural-20 effect, so no dice/result is ever multiplied incorrectly.
  for (const [minimumLevel, count] of [[5, 2], [11, 3], [20, 4]]) {
    const roll = {
      ...newRoll(fighter.character.id, longsword.groupId),
      name: 'Longsword · ' + count + ' attacks',
      minimumLevel, order: fighter.rolls.length,
    };
    for (let index = 1; index <= count; index++) {
      const attack = { ...newPart(), name: 'Attack ' + index,
        expression: longsword.subgroups[0].expression };
      const damage = { ...newPart(), name: 'Damage ' + index,
        expression: longsword.subgroups[1].expression };
      roll.subgroups.push(attack, damage);
      const critical = createDnd5eCriticalEffect(attack.id, damage.id, '1d8');
      critical.order = index - 1;
      critical.name = 'Critical hit · Attack ' + index;
      roll.effects.push(critical);
    }
    roll.expression = normalFormula(roll.subgroups);
    fighter.rolls.push(roll);
  }
  return validateDocument(fighter);
}

/** New templates have all future gated Rolls prepared; existing saves are never rewritten. */
export const templates = starterSpecs.map(([category, name, description], index) => ({ category, name, description, index }));
export function instantiateTemplate(index) {
  const spec = starterSpecs[index];
  if (!spec) throw new Error('Unknown template');
  const [, name, tag, level, modifiers, sections] = spec;
  const doc = newCharacter(name);
  doc.character.tag = tag; doc.character.level = level;
  doc.modifiers = modifiers.map(([m, v], order) => ({ id: uid(), characterId: doc.character.id, name: m, value: v, order }));
  sections.forEach(([groupName, definitions], order) => {
    const group = { id: uid(), characterId: doc.character.id, name: groupName, order };
    doc.groups.push(group);
    definitions.forEach(([rollName, formulaOrParts, rule]) => {
      doc.rolls.push(createStarterRoll(doc.character.id, group.id, rollName,
        formulaOrParts, rule, doc.rolls.length));
    });
  });
  return index === 0 ? buildFighter2014Progression(doc) : validateDocument(doc);
}
