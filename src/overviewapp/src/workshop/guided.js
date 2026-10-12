/**
 * Pure, native-schema-compatible operations for the guided web workshop.
 * The browser never evaluates effects: Android's roll engine owns execution.
 */
const id = () => crypto.randomUUID();
export const EFFECT_TYPES = ['BONUS', 'MALUS'];
export const SOURCES = ['PART', 'ROLL', 'VARIABLE'];
export const SCOPES = ['DICE_ONLY', 'MODIFIERS_ONLY', 'TOTAL'];
export const COMPARISONS = ['GREATER_OR_EQUAL', 'LESS_OR_EQUAL', 'GREATER', 'LESS', 'EQUAL', 'NOT_EQUAL'];
export const ACTION_KINDS = ['ADD', 'SUBTRACT', 'MULTIPLY', 'REPLACE', 'REROLL', 'ROLL_AFTER'];
export const APPEARANCE_MODES = ['CHARACTER_DEFAULT', 'UNIFORM', 'PER_DIE', 'RANDOM_UNIFORM', 'RANDOM_PER_DIE'];
export const SYSTEM_STYLES = [
  'arcane-blue','inferno-forged','emerald-aether','violet-star','obsidian-rune',
  'lunar-frost','sunlit-brass','celestial-pearl','deep-ocean','ancient-copper',
].map(name => ({ id: 'preset:' + name, name: name.split('-').map(x => x[0].toUpperCase() + x.slice(1)).join(' ') }));

export const newCondition = (parts = []) => ({
  id: id(), source: parts.length ? 'PART' : 'ROLL',
  partId: parts[0]?.id || null, variableName: null, scope: 'DICE_ONLY',
  comparison: 'GREATER_OR_EQUAL', threshold: '20',
});
export const newActivationGroup = parts => ({ id: id(), conditions: [newCondition(parts)] });
export const newAction = parts => ({
  id: id(), kind: 'ADD', targetPartId: parts[0]?.id || null, scope: 'TOTAL', expression: '1',
});
export const newEffect = (type, parts, order = 0) => ({
  id: id(), name: type === 'MALUS' ? 'New malus' : 'New bonus', type, order,
  enabled: true, stopFollowingEffects: false,
  activationGroups: [newActivationGroup(parts)],
  actions: [{ ...newAction(parts), kind: type === 'MALUS' ? 'SUBTRACT' : 'ADD' }],
});
export function setActionKind(action, kind) {
  if (!ACTION_KINDS.includes(kind)) throw new Error('Unknown effect action');
  if (action.kind === kind) return action;
  const dice = kind === 'REROLL' || kind === 'ROLL_AFTER';
  return { ...action, kind, expression: dice ? '' : '1',
    scope: dice && action.scope === 'MODIFIERS_ONLY' ? 'DICE_ONLY' : action.scope };
}
export const isPartUsed = (roll, partId) =>
  (roll.effects || []).some(effect =>
    (effect.activationGroups || []).some(group => (group.conditions || []).some(c =>
      c.partId === partId || (c.threshold || '').includes('{partId:' + partId + '}'))) ||
    (effect.actions || []).some(a =>
      a.targetPartId === partId || (a.expression || '').includes('{partId:' + partId + '}')));
export function removePartSafely(roll, partId, normalFormula) {
  if (isPartUsed(roll, partId)) throw new Error('This Part is referenced by an effect. Remove or edit the effect first.');
  roll.subgroups = (roll.subgroups || []).filter(part => part.id !== partId);
  const appearance = roll.diceAppearance || { mode: 'CHARACTER_DEFAULT' };
  roll.diceAppearance = {
    ...appearance,
    subgroupStyleIds: (appearance.subgroupStyleIds || []).filter(item => item.subgroupId !== partId),
  };
  if (roll.subgroups.length && !roll.subgroups.some(part => part.includeInNormalRoll !== false))
    roll.subgroups[0].includeInNormalRoll = true;
  roll.expression = roll.subgroups.length ? normalFormula(roll.subgroups) : '1d20';
}
export function setPartStyle(appearance, partId, styleId) {
  return { ...appearance,
    subgroupStyleIds: [...(appearance.subgroupStyleIds || []).filter(x => x.subgroupId !== partId),
      ...(styleId ? [{ subgroupId: partId, styleId }] : [])] };
}
export function setDieStyle(appearance, slot, styleId) {
  return { ...appearance,
    perDieStyleIds: [...(appearance.perDieStyleIds || []).filter(x => x.slot !== slot),
      ...(styleId ? [{ slot, styleId }] : [])] };
}
const validRecord = x => x != null && typeof x === 'object' && !Array.isArray(x);
const requireValid = (condition, message) => { if (!condition) throw new Error(message); };
const validRefExpr = (value, partIds) => {
  requireValid(typeof value === 'string' && value.length <= 1500, 'Invalid effect expression');
  for (const match of value.matchAll(/\{partId:([^{}]+)\}/g))
    requireValid(partIds.has(match[1]), 'Effect references a missing Part');
};
export function validateGuidedRoll(roll, modifierNames, styleIds) {
  const parts = roll.subgroups || [];
  const partIds = new Set(parts.map(x => x.id));
  const modifierKeys = new Set(modifierNames.map(x => x.trim().toLowerCase()));
  const a = roll.diceAppearance || { mode: 'CHARACTER_DEFAULT' };
  requireValid(APPEARANCE_MODES.includes(a.mode || 'CHARACTER_DEFAULT'), 'Invalid dice appearance mode');
  const checkStyle = style => requireValid(style == null || styleIds.has(style), 'Unknown dice appearance style');
  checkStyle(a.styleId);
  for (const entry of a.subgroupStyleIds || []) {
    requireValid(validRecord(entry) && partIds.has(entry.subgroupId), 'Dice style refers to a missing Part');
    checkStyle(entry.styleId);
  }
  for (const entry of a.perDieStyleIds || []) {
    requireValid(validRecord(entry) && /^\d+:\d+$/.test(entry.slot), 'Invalid die slot');
    checkStyle(entry.styleId);
  }
  for (const style of a.randomStyleIds || []) checkStyle(style);
  const nestedIds = new Set();
  const unique = (object, label) => {
    requireValid(validRecord(object) && typeof object.id === 'string' && object.id.length > 0 &&
      !nestedIds.has(object.id), 'Duplicate or invalid ' + label);
    nestedIds.add(object.id);
  };
  for (const effect of roll.effects || []) {
    unique(effect, 'effect');
    requireValid(typeof effect.name === 'string' && effect.name.trim(), 'Effect needs a name');
    requireValid(EFFECT_TYPES.includes(effect.type), 'Unknown bonus/malus type');
    requireValid(Array.isArray(effect.activationGroups) && Array.isArray(effect.actions) &&
      effect.activationGroups.length >= 1 && effect.activationGroups.length <= 30 &&
      effect.actions.length >= 1 && effect.actions.length <= 30,
      'Invalid effect groups or actions');
    for (const group of effect.activationGroups) {
      unique(group, 'condition group');
      requireValid(Array.isArray(group.conditions) && group.conditions.length > 0 &&
        group.conditions.length <= 30, 'Each OR group needs an AND condition');
      for (const condition of group.conditions) {
        unique(condition, 'condition');
        requireValid(SOURCES.includes(condition.source) && SCOPES.includes(condition.scope) &&
          COMPARISONS.includes(condition.comparison), 'Unsupported condition selection');
        requireValid(condition.source !== 'PART' || partIds.has(condition.partId),
          'Trigger refers to a missing Part');
        requireValid(condition.source !== 'VARIABLE' ||
          modifierKeys.has((condition.variableName || '').trim().toLowerCase()),
          'Trigger refers to a missing variable');
        validRefExpr(condition.threshold, partIds);
        requireValid(condition.threshold.trim().length > 0, 'Condition threshold required');
      }
    }
    for (const action of effect.actions) {
      unique(action, 'action');
      requireValid(ACTION_KINDS.includes(action.kind) && SCOPES.includes(action.scope), 'Unsupported action');
      requireValid(partIds.has(action.targetPartId), 'Action targets a missing Part');
      requireValid(action.kind === 'REROLL' || action.kind === 'ROLL_AFTER' ||
        (typeof action.expression === 'string' && action.expression.trim().length > 0),
        'Action expression required');
      validRefExpr(action.expression, partIds);
    }
  }
  return roll;
}
