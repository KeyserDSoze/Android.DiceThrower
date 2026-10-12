import {
  ACTION_KINDS, APPEARANCE_MODES, COMPARISONS, EFFECT_TYPES, SCOPES, SOURCES,
  SYSTEM_STYLES, newAction, newActivationGroup, newCondition, newEffect,
  setActionKind, setDieStyle, setPartStyle,
} from './guided.js';

function Choice({ label, value, options, onChange }) {
  return <label className="workshop-field"><span>{label}</span>
    <select value={value ?? ''} onChange={e => onChange(e.target.value)}>
      {options.map(item => {
        const option = typeof item === 'string' ? { id: item, name: item.replaceAll('_', ' ').toLowerCase() } : item;
        return <option key={option.id} value={option.id}>{option.name}</option>;
      })}
    </select>
  </label>;
}
function Input({ label, value, onChange }) {
  return <label className="workshop-field"><span>{label}</span>
    <input value={value ?? ''} onChange={e => onChange(e.target.value)} /></label>;
}
function Check({ label, checked, onChange }) {
  return <label className="workshop-check"><input type="checkbox" checked={!!checked}
    onChange={e => onChange(e.target.checked)} /> {label}</label>;
}
const aria = (t, key) => t[key];
const scopeOptions = (t) => SCOPES.map((id, i) => ({ id, name: [t.scopeDice, t.scopeModifiers, t.scopeTotal][i] }));
const styleChoice = (doc, t) => [{ id: '', name: t.styleAuto }, ...SYSTEM_STYLES, ...doc.diceStyles.map(s => ({ id: s.id, name: s.name }))];

function StylePicker({ label, value, doc, t, onChange }) {
  return <Choice label={label} value={value || ''} options={styleChoice(doc, t)} onChange={v => onChange(v || null)} />;
}
function ConditionEditor({ condition, roll, doc, t, onUpdate, onRemove, disabledRemove = false }) {
  const parts = roll.subgroups || [];
  return <div className="workshop-guided-clause">
    <div className="workshop-fields">
      <Choice label={t.effectSource} value={condition.source} options={[
        { id:'PART', name:t.sourcePart },{ id:'ROLL', name:t.sourceRoll },{ id:'VARIABLE', name:t.sourceVariable },
      ]} onChange={source => onUpdate(c => {
        c.source = source;
        c.partId = source === 'PART' ? parts[0]?.id || null : null;
        c.variableName = source === 'VARIABLE' ? doc.modifiers[0]?.name || null : null;
      })} />
      {condition.source === 'PART' && <Choice label={t.targetPart} value={condition.partId || ''}
        options={[{ id:'', name:t.choosePart }, ...parts.map(p => ({ id:p.id, name:p.name || p.expression }))]}
        onChange={v => onUpdate(c => c.partId = v || null)} />}
      {condition.source === 'VARIABLE' && <Choice label={t.variable} value={condition.variableName || ''}
        options={[{ id:'', name:t.chooseVariable }, ...doc.modifiers.map(m => ({ id:m.name, name:m.name }))]}
        onChange={v => onUpdate(c => c.variableName = v || null)} />}
      <Choice label={t.effectScope} value={condition.scope} options={scopeOptions(t)}
        onChange={v => onUpdate(c => c.scope = v)} />
      <Choice label={t.comparison} value={condition.comparison}
        options={COMPARISONS.map((id,i) => ({ id, name:['≥','≤','>','<','=','≠'][i] }))}
        onChange={v => onUpdate(c => c.comparison = v)} />
      <Input label={t.threshold} value={condition.threshold}
        onChange={v => onUpdate(c => c.threshold = v)} />
    </div>
    <button type="button" className="workshop-remove" disabled={disabledRemove} onClick={onRemove} aria-label={t.removeCondition}>{t.removeCondition}</button>
  </div>;
}
function EffectEditor({ effect, index, roll, doc, t, onUpdate, onRemove }) {
  const mutate = f => onUpdate(e => f(e));
  const parts = roll.subgroups || [];
  return <details className="workshop-effect" key={effect.id}>
    <summary><strong>{effect.type === 'BONUS' ? '✦' : '−'} {effect.name}</strong>
      <small>{effect.activationGroups.length} {t.orGroups} · {effect.actions.length} {t.actions}</small>
    </summary>
    <div className="workshop-fields">
      <Input label={t.effectName} value={effect.name} onChange={v => mutate(e => e.name = v)} />
      <Choice label={t.effectType} value={effect.type} options={EFFECT_TYPES.map(id => ({
        id, name:id === 'BONUS' ? t.bonus : t.malus,
      }))} onChange={v => mutate(e => e.type = v)} />
    </div>
    <div className="workshop-inline">
      <Check label={t.enabled} checked={effect.enabled !== false}
        onChange={v => mutate(e => e.enabled = v)} />
      <Check label={t.stopFollowing} checked={effect.stopFollowingEffects}
        onChange={v => mutate(e => e.stopFollowingEffects = v)} />
      <button type="button" onClick={onRemove}>{t.remove}</button>
    </div>
    <div className="workshop-row-head"><h4>{t.conditions}</h4>
      <button type="button" onClick={() => mutate(e => e.activationGroups.push(newActivationGroup(parts)))}>
        {t.addOrGroup}</button>
    </div>
    <p className="workshop-hint">{t.conditionHelp}</p>
    {!effect.activationGroups.length && <p className="workshop-hint">{t.unconditional}</p>}
    {effect.activationGroups.map((group, groupIndex) => <div className="workshop-rule-group" key={group.id}>
      <div className="workshop-row-head"><strong>{t.orGroup} {groupIndex + 1}</strong>
        <button type="button" disabled={effect.activationGroups.length <= 1} onClick={() => mutate(e => e.activationGroups.splice(groupIndex, 1))}>
          {t.removeGroup}</button>
      </div>
      {group.conditions.map((condition, conditionIndex) => <div key={condition.id}>
        {conditionIndex > 0 && <small className="workshop-logic">AND</small>}
        <ConditionEditor condition={condition} roll={roll} doc={doc} t={t}
          disabledRemove={effect.activationGroups.length === 1 && group.conditions.length === 1}
          onUpdate={f => mutate(e => f(e.activationGroups[groupIndex].conditions[conditionIndex]))}
          onRemove={() => mutate(e => {
            if (e.activationGroups.length === 1 && e.activationGroups[0].conditions.length === 1) return;
            const g = e.activationGroups[groupIndex];
            g.conditions.splice(conditionIndex, 1);
            if (!g.conditions.length) e.activationGroups.splice(groupIndex, 1);
          })} />
      </div>)}
      <button type="button" onClick={() => mutate(e => e.activationGroups[groupIndex].conditions.push(newCondition(parts)))}>
        {t.addAndCondition}</button>
    </div>)}
    <div className="workshop-row-head"><h4>{t.actions}</h4>
      <button type="button" onClick={() => mutate(e => e.actions.push(newAction(parts)))}>{t.addAction}</button>
    </div>
    {effect.actions.map((action, actionIndex) => <div className="workshop-guided-clause" key={action.id}>
      <div className="workshop-fields">
        <Choice label={t.actionType} value={action.kind}
          options={ACTION_KINDS.map(id => ({ id, name: ({ REROLL:t.reroll, ROLL_AFTER:t.rollAfter, REPLACE:t.replace })[id] ||
            id.replaceAll('_', ' ').toLowerCase() }))}
          onChange={kind => mutate(e => { e.actions[actionIndex] = setActionKind(e.actions[actionIndex], kind); })} />
        <Choice label={t.targetPart} value={action.targetPartId || ''}
          options={parts.map(p => ({ id:p.id, name:p.name || p.expression }))}
          onChange={v => mutate(e => e.actions[actionIndex].targetPartId = v || null)} />
        <Choice label={t.effectScope} value={action.scope} options={scopeOptions(t)}
          onChange={v => mutate(e => e.actions[actionIndex].scope = v)} />
        <Input label={action.kind === 'REROLL' || action.kind === 'ROLL_AFTER' ? t.optionalFormula : t.formula}
          value={action.expression} onChange={v => mutate(e => e.actions[actionIndex].expression = v)} />
      </div>
      {(action.kind === 'REROLL' || action.kind === 'ROLL_AFTER') &&
        <small className="workshop-hint">{t.rerollHelp}</small>}
      <button type="button" className="workshop-remove" disabled={effect.actions.length <= 1}
        aria-label={t.removeAction} onClick={() => mutate(e => e.actions.splice(actionIndex, 1))}>{t.removeAction}</button>
    </div>)}
  </details>;
}
function dieSlots(roll) {
  // Display slots for literal dice terms. More complex expressions can still
  // use advanced JSON without losing any persisted slot overrides.
  const terms = (roll.expression || '').matchAll(/(?:^|[^\w])(\d*)[dD](\d+)(?!\w)/g);
  const slots = []; let component = 0;
  for (const term of terms) {
    const count = Math.min(40, Number(term[1] || 1));
    for (let i = 0; i < count && slots.length < 80; i++) slots.push({ slot: component + ':' + i, name:'d' + term[2] + ' #' + (i + 1) });
    component++;
  }
  return slots;
}
export function GuidedAppearance({ roll, doc, t, onChange }) {
  const appearance = roll.diceAppearance || { mode:'CHARACTER_DEFAULT' };
  const mode = appearance.mode || 'CHARACTER_DEFAULT';
  const parts = roll.subgroups || [];
  const slots = dieSlots(roll);
  const random = appearance.randomStyleIds || [];
  return <section className="workshop-guided-section">
    <div className="workshop-row-head"><h3>{t.appearance}</h3></div>
    <Choice label={t.appearanceMode} value={mode}
      options={APPEARANCE_MODES.map((id,i)=>({ id, name:[
        t.appearanceDefault,t.appearanceUniform,t.appearancePerDie,t.appearanceRandomOne,t.appearanceRandomEach,
      ][i] }))} onChange={v => onChange(a => a.mode = v)} />
    {mode === 'UNIFORM' && <StylePicker label={t.style} value={appearance.styleId} doc={doc} t={t}
      onChange={v => onChange(a => a.styleId = v)} />}
    {mode === 'PER_DIE' && <>
      <p className="workshop-hint">{t.appearancePartHelp}</p>
      {parts.map(p => <div key={p.id} className="workshop-inline">
        <span className="workshop-subtle">{p.name || p.expression}</span>
        <StylePicker label={t.style} value={(appearance.subgroupStyleIds || []).find(x => x.subgroupId === p.id)?.styleId}
          doc={doc} t={t} onChange={v => onChange(a => {
            a.subgroupStyleIds = setPartStyle(a, p.id, v).subgroupStyleIds;
          })} />
      </div>)}
      {!!slots.length && <details className="workshop-die-details"><summary>{t.dieOverrides} · {slots.length}</summary>
        <p className="workshop-hint">{t.dieOverridesHelp}</p>
        {slots.map(item => <div className="workshop-inline" key={item.slot}>
          <span className="workshop-subtle">{item.name} ({item.slot})</span>
          <StylePicker label={t.style} value={(appearance.perDieStyleIds || []).find(x => x.slot === item.slot)?.styleId}
            doc={doc} t={t} onChange={v => onChange(a => {
              a.perDieStyleIds = setDieStyle(a, item.slot, v).perDieStyleIds;
            })}/>
        </div>)}
      </details>}
    </>}
    {(mode === 'RANDOM_UNIFORM' || mode === 'RANDOM_PER_DIE') && <>
      <p className="workshop-hint">{t.randomHelp}</p>
      <div className="workshop-style-pool">
        {styleChoice(doc, t).filter(s => s.id).map(s => <Check key={s.id} label={s.name}
          checked={random.includes(s.id)} onChange={checked => onChange(a => {
            a.randomStyleIds = checked ? [...new Set([...(a.randomStyleIds || []), s.id])]
              : (a.randomStyleIds || []).filter(id => id !== s.id);
          })} />)}
      </div>
    </>}
  </section>;
}

export default function GuidedRollEditor({ roll, doc, t, onEdit }) {
  return <>
    <div className="workshop-guided-section">
      <div className="workshop-row-head"><h3>{t.effects}</h3>
        <div className="workshop-inline">
          <button type="button" onClick={() => onEdit(r => r.effects.push(newEffect('BONUS', r.subgroups || [], r.effects.length)))}>
            + {t.bonus}</button>
          <button type="button" onClick={() => onEdit(r => r.effects.push(newEffect('MALUS', r.subgroups || [], r.effects.length)))}>
            + {t.malus}</button>
        </div>
      </div>
      {!roll.effects.length && <p className="workshop-hint">{t.noEffects}</p>}
      {roll.effects.map((effect,index) => <EffectEditor key={effect.id} effect={effect} index={index}
        roll={roll} doc={doc} t={t}
        onUpdate={f => onEdit(r => f(r.effects[index]))}
        onRemove={() => onEdit(r => r.effects.splice(index, 1))} />)}
    </div>
    <GuidedAppearance roll={roll} doc={doc} t={t}
      onChange={f => onEdit(r => {
        if (!r.diceAppearance) r.diceAppearance = { mode:'CHARACTER_DEFAULT' };
        f(r.diceAppearance);
      })} />
  </>;
}
