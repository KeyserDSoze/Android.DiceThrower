import { useEffect, useMemo, useState } from 'react';
import QRCode from 'qrcode';
import {
  androidUrl, decodeToken, documentFromEnvelope, envelope, importAsCopy, instantiateTemplate,
  listSaved, newCharacter, newGroup, newLevelRule, newModifier, newPart, newRoll, newStyle,
  normalFormula, readShareHash, removeDocument, saveDocument, shareUrl, templates, validateDocument,
} from './character.js';
import GuidedRollEditor from './GuidedRollEditor.jsx';
import { isPartUsed, removePartSafely, SYSTEM_STYLES } from './guided.js';

const dictionary = {
  en: {
    title: 'Character workshop', intro: 'Design a character on the web. Keep it privately in this browser, or share a self-contained character link or QR code.',
    storage: 'Saved on this device · IndexedDB', noCloud: 'Not synced with Google Drive yet. Clearing browser data may delete these characters.',
    library: 'Your characters', starter: 'Starter characters', create: 'New character', load: 'Edit', delete: 'Delete', use: 'Use template',
    save: 'Save in browser', share: 'Share link & QR', export: 'Export JSON', import: 'Import JSON', copy: 'Copy link',
    openApp: 'Open in Android app', preview: 'Character shared with you', add: 'Add to my browser', discard: 'Dismiss',
    details: 'Character details', characterName: 'Character name', level: 'Level', tag: 'Description', modifiers: 'Variables & modifiers',
    groups: 'Roll groups', rolls: 'Rolls', styles: 'Custom dice styles', group: 'Group', formula: 'Formula',
    parts: 'Roll Parts', rules: 'Level rules', effects: 'Bonus & malus effects', appearance: 'Dice appearance',
    enabled: 'Enabled', double: 'Double roll (Best/Worst available)', minimum: 'Minimum level', remove: 'Remove',
    addModifier: '+ Modifier', addGroup: '+ Group', addRoll: '+ Roll', addPart: '+ Part', addRule: '+ Level rule',
    addStyle: '+ Dice style', warning: 'Anyone with this link or QR code can read the character data included. Do not include private information.',
    tooLong: 'The character is too large for a dependable QR. Export JSON instead.', invalid: 'Unable to read character',
    bonus: 'Bonus', malus: 'Malus', effectName: 'Effect name', effectType: 'Type', conditions: 'Activation conditions',
    conditionHelp: 'Condition groups are joined with OR; conditions inside each group are joined with AND.',
    unconditional: 'Always active (no activation conditions).', orGroups: 'OR groups', orGroup: 'OR group',
    addOrGroup: '+ OR group', addAndCondition: '+ AND condition', removeGroup: 'Remove group', removeCondition: 'Remove condition',
    actions: 'actions', addAction: '+ Action', removeAction: 'Remove action', actionType: 'Action',
    effectSource: 'Read value from', sourcePart: 'Roll Part', sourceRoll: 'Whole roll', sourceVariable: 'Variable',
    variable: 'Variable', chooseVariable: 'Select variable', targetPart: 'Target Roll Part', choosePart: 'Select Part',
    wholeRoll: 'Entire roll', comparison: 'Comparison', threshold: 'Compare against',
    effectScope: 'Value includes', scopeDice: 'Dice only', scopeModifiers: 'Modifiers only', scopeTotal: 'Total',
    stopFollowing: 'Stop following effects when activated', reroll: 'Reroll (replace dice)', rollAfter: 'Roll additional dice',
    replace: 'Replace value', optionalFormula: 'New dice expression (blank = target Part)',
    rerollHelp: 'Leave blank to reuse the targeted Part expression. Reroll replaces; Roll additional dice runs after.',
    noEffects: 'No bonus/malus rules yet. Add one and configure its activation and actions.',
    appearanceMode: 'Style distribution', appearanceDefault: 'Character default', appearanceUniform: 'Uniform style',
    appearancePerDie: 'By Roll Part / die', appearanceRandomOne: 'Random: one style',
    appearanceRandomEach: 'Random: each die', appearancePartHelp: 'Choose a style per Roll Part. Optional individual die overrides have priority.',
    dieOverrides: 'Individual dice (advanced)', dieOverridesHelp: 'Slots correspond to dice in the formula, from left to right.',
    randomHelp: 'Select possible styles. An empty selection uses the full catalog.',
    style: 'Dice style', styleAuto: 'Use character default', secondaryStyle: 'Candidate B dice style',
    initialThrow: 'Included in initial throw', doubleCandidate: 'Available in double roll (A/B)',
    formulaFromParts: 'Formula is generated from Roll Parts. Edit the Parts below.',
    partUsed: 'This Part is referenced by an effect. Remove or edit its effect first.',
    createPartFirst: 'Create at least one Roll Part before adding effects.', defaultStyle: 'Default dice style',
    expertJson: 'Advanced native JSON (optional)', expertHelp: 'Only edit this if you know the Android schema. Guided controls preserve existing fields.',
    download: 'Download / keep backup', select: 'Open a character or create one.', saved: 'Saved locally.', ready: 'Link ready.',
  },
  it: {
    title: 'Laboratorio personaggi', intro: 'Crea il personaggio sul sito. Salvalo privatamente nel browser oppure condividilo con un link o un QR che contiene tutti i dati.',
    storage: 'Salvato su questo dispositivo · IndexedDB', noCloud: 'Non ancora sincronizzato con Google Drive. La pulizia dei dati del browser può cancellare i personaggi.',
    library: 'I tuoi personaggi', starter: 'Personaggi preimpostati', create: 'Nuovo personaggio', load: 'Modifica', delete: 'Elimina', use: 'Usa modello',
    save: 'Salva nel browser', share: 'Condividi link e QR', export: 'Esporta JSON', import: 'Importa JSON', copy: 'Copia link',
    openApp: "Apri nell'app Android", preview: 'Personaggio ricevuto', add: 'Aggiungi al browser', discard: 'Chiudi',
    details: 'Dettagli personaggio', characterName: 'Nome personaggio', level: 'Livello', tag: 'Descrizione', modifiers: 'Variabili e modificatori',
    groups: 'Gruppi di tiri', rolls: 'Tiri', styles: 'Stili dadi personalizzati', group: 'Gruppo', formula: 'Formula',
    parts: 'Parti del tiro', rules: 'Regole di livello', effects: 'Effetti bonus e malus', appearance: 'Aspetto dei dadi',
    enabled: 'Abilitato', double: 'Doppio tiro (Best/Worst disponibile)', minimum: 'Livello minimo', remove: 'Rimuovi',
    addModifier: '+ Modificatore', addGroup: '+ Gruppo', addRoll: '+ Tiro', addPart: '+ Parte', addRule: '+ Regola livello',
    addStyle: '+ Stile dado', warning: 'Chiunque abbia il link o il QR può leggere i dati contenuti. Non includere informazioni private.',
    tooLong: 'Personaggio troppo grande per un QR affidabile. Usa il file JSON.', invalid: 'Impossibile leggere il personaggio',
    bonus: 'Bonus', malus: 'Malus', effectName: 'Nome effetto', effectType: 'Tipo', conditions: 'Condizioni di attivazione',
    conditionHelp: 'I gruppi sono collegati da OR; le condizioni nello stesso gruppo da AND.',
    unconditional: 'Sempre attivo (senza condizioni).', orGroups: 'gruppi OR', orGroup: 'Gruppo OR',
    addOrGroup: '+ Gruppo OR', addAndCondition: '+ Condizione AND', removeGroup: 'Rimuovi gruppo', removeCondition: 'Rimuovi condizione',
    actions: 'azioni', addAction: '+ Azione', removeAction: 'Rimuovi azione', actionType: 'Azione',
    effectSource: 'Valore di origine', sourcePart: 'Parte del tiro', sourceRoll: 'Tiro completo', sourceVariable: 'Variabile',
    variable: 'Variabile', chooseVariable: 'Scegli variabile', targetPart: 'Parte interessata', choosePart: 'Scegli parte',
    wholeRoll: 'Tiro intero', comparison: 'Confronto', threshold: 'Confronta con',
    effectScope: 'Valore considerato', scopeDice: 'Solo dadi', scopeModifiers: 'Solo modificatori', scopeTotal: 'Totale',
    stopFollowing: 'Interrompi effetti successivi se attivato', reroll: 'Rilancia (sostituisce i dadi)',
    rollAfter: 'Tira altri dadi', replace: 'Sostituisci valore',
    optionalFormula: 'Nuova formula (vuota = stessa parte)',
    rerollHelp: 'Lascia vuoto per usare la formula della parte. Reroll sostituisce, Tira altri dadi aggiunge un tiro successivo.',
    noEffects: 'Nessun bonus o malus. Aggiungine uno e configura condizioni e azioni.',
    appearanceMode: 'Distribuzione stile', appearanceDefault: 'Default del personaggio', appearanceUniform: 'Stile uniforme',
    appearancePerDie: 'Per parte / dado', appearanceRandomOne: 'Casuale: stesso stile',
    appearanceRandomEach: 'Casuale: ogni dado', appearancePartHelp: 'Seleziona uno stile per parte. Le eccezioni per singolo dado hanno priorità.',
    dieOverrides: 'Singoli dadi (avanzato)', dieOverridesHelp: 'Le posizioni seguono i dadi nella formula da sinistra a destra.',
    randomHelp: 'Seleziona gli stili disponibili. Nessuna selezione usa l’intero catalogo.',
    style: 'Stile dado', styleAuto: 'Default personaggio', secondaryStyle: 'Stile dadi candidato B',
    initialThrow: 'Inclusa nel tiro iniziale', doubleCandidate: 'Disponibile nel doppio tiro (A/B)',
    formulaFromParts: 'La formula deriva dalle parti: modificale qui sotto.',
    partUsed: 'Questa parte è usata da un effetto: modifica o rimuovi prima l’effetto.',
    createPartFirst: 'Crea almeno una parte del tiro prima di aggiungere effetti.', defaultStyle: 'Stile dado predefinito',
    expertJson: 'JSON nativo avanzato (facoltativo)', expertHelp: 'Modificalo solo se conosci il formato Android. I controlli guidati preservano i campi esistenti.',
    download: 'Scarica un backup', select: 'Apri un personaggio o creane uno.', saved: 'Salvato nel browser.', ready: 'Link pronto.',
  },
};
const uid = () => crypto.randomUUID();

function Field({ label, value, onChange, type = 'text', min, max, readOnly = false }) {
  return <label className="workshop-field"><span>{label}</span>
    <input type={type} min={min} max={max} readOnly={readOnly} value={value ?? ''} onChange={e => onChange(type === 'number' ? Number(e.target.value) : e.target.value)} />
  </label>;
}
function JsonField({ label, value, onChange }) {
  const [draft, setDraft] = useState(JSON.stringify(value || [], null, 2));
  const [error, setError] = useState('');
  useEffect(() => { setDraft(JSON.stringify(value || [], null, 2)); }, [value]);
  return <label className="workshop-field"><span>{label}</span>
    <textarea rows="3" spellCheck="false" value={draft} onChange={e => {
      setDraft(e.target.value);
      try { onChange(JSON.parse(e.target.value)); setError(''); } catch { setError('Invalid JSON'); }
    }} />
    {error && <small role="alert">{error}</small>}
  </label>;
}
function downloadDocument(characterDoc) {
  const blob = new Blob([JSON.stringify(envelope(characterDoc), null, 2)], { type: 'application/json' });
  const href = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = href;
  a.download = 'dice-thrower-character.json';
  a.click();
  setTimeout(() => URL.revokeObjectURL(href), 1500);
}
export default function CharacterWorkshop({ locale = 'en' }) {
  const t = dictionary[locale] || dictionary.en;
  const [items, setItems] = useState([]);
  const [doc, setDoc] = useState(null);
  const [preview, setPreview] = useState(null);
  const [url, setUrl] = useState('');
  const [qr, setQr] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function refresh() {
    try { setItems((await listSaved()).sort((a,b) => a.character.name.localeCompare(b.character.name))); }
    catch (e) { setError('IndexedDB: ' + e.message); }
  }
  useEffect(() => {
    refresh();
    if (window.location.hash.startsWith('#character=')) {
      try { setPreview(readShareHash()); } catch (e) { setError(e.message); }
    }
  }, []);

  function edit(mutator) {
    setDoc(previous => {
      if (!previous) return previous;
      const copy = structuredClone(previous);
      mutator(copy);
      return copy;
    });
    setUrl(''); setQr(''); setMessage(''); setError('');
  }
  async function save() {
    try { validateDocument(doc); await saveDocument(doc); await refresh(); setMessage(t.saved); setError(''); }
    catch (e) { setError(e.message); }
  }
  async function deleteSaved(id) {
    if (!window.confirm(t.delete + '?')) return;
    try { await removeDocument(id); if (doc?.character.id === id) setDoc(null); await refresh(); }
    catch (e) { setError(e.message); }
  }
  async function createShare() {
    try {
      const link = shareUrl(doc, window.location.origin + import.meta.env.BASE_URL + 'workshop/');
      const image = await QRCode.toDataURL(link, { errorCorrectionLevel: 'L', width: 340, margin: 2 });
      setUrl(link); setQr(image); setMessage(t.ready); setError('');
    } catch (e) { setQr(''); setUrl(''); setError(t.tooLong + ' ' + e.message); }
  }
  async function addShared() {
    try {
      const copy = importAsCopy(preview);
      await saveDocument(copy); await refresh(); setPreview(null); setDoc(copy);
      history.replaceState(null, '', location.pathname); setMessage(t.saved); setError('');
    } catch (e) { setError(e.message); }
  }
  async function fileImport(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      if (file.size > 128 * 1024) throw new Error('File too large');
      const packet = JSON.parse(await file.text());
      const copy = importAsCopy(documentFromEnvelope(packet));
      await saveDocument(copy); setDoc(copy); await refresh(); setError(''); setMessage(t.saved);
    } catch (e) { setError(e.message); }
    event.target.value = '';
  }

  return <section className="workshop">
    <div className="workshop-heading">
      <span className="eyebrow">Offline-first · No account</span>
      <h1>{t.title}</h1><p>{t.intro}</p>
      <p className="workshop-hint">{t.storage} · {t.noCloud}</p>
    </div>
    {error && <p className="workshop-error" role="alert">{error}</p>}
    {message && <p className="workshop-success" role="status">{message}</p>}
    {preview && <section className="premium-panel workshop-preview">
      <h2>{t.preview}</h2><h3>{preview.character.name} · Lv {preview.character.level}</h3>
      <p>{preview.character.tag}</p>
      <p>{preview.modifiers.length} modifiers · {preview.groups.length} groups · {preview.rolls.length} rolls</p>
      <p className="workshop-hint">{t.warning}</p>
      <div className="workshop-actions">
        <button className="primary" onClick={addShared}>{t.add}</button>
        <a className="secondary" href={androidUrl(preview)}>{t.openApp}</a>
        <button onClick={() => { setPreview(null); history.replaceState(null, '', location.pathname); }}>{t.discard}</button>
      </div>
    </section>}
    <div className="workshop-layout">
      <aside className="workshop-sidebar">
        <section className="premium-panel">
          <h2>{t.library}</h2>
          <button className="primary" onClick={() => { setDoc(newCharacter()); setUrl(''); setQr(''); }}>{t.create}</button>
          <label className="workshop-file-button">{t.import}<input type="file" accept=".json,application/json" onChange={fileImport} /></label>
          <div className="workshop-collection">
            {items.map(saved => <div className="workshop-list-item" key={saved.character.id}>
              <span><strong>{saved.character.name}</strong><small>Lv {saved.character.level} · {saved.rolls.length} rolls</small></span>
              <div><button onClick={() => { setDoc(structuredClone(saved)); setQr(''); setUrl(''); }}>{t.load}</button>
              <button className="workshop-remove" onClick={() => deleteSaved(saved.character.id)} aria-label={t.delete + ' ' + saved.character.name}>×</button></div>
            </div>)}
          </div>
        </section>
        <section className="premium-panel">
          <h2>{t.starter} · {templates.length}</h2>
          <div className="workshop-collection">
            {templates.map(item => <div className="workshop-list-item" key={item.index}>
              <span><strong>{item.name}</strong><small>{item.category}</small></span>
              <button onClick={() => { setDoc(instantiateTemplate(item.index)); setUrl(''); setQr(''); }}>{t.use}</button>
            </div>)}
          </div>
        </section>
      </aside>
      <div className="workshop-editor">
        {!doc && <section className="premium-panel">{t.select}</section>}
        {doc && <>
          <section className="premium-panel">
            <div className="workshop-row-head"><h2>{t.details}</h2><span className="eyebrow">Lv {doc.character.level}</span></div>
            <div className="workshop-fields">
              <Field label={t.characterName} value={doc.character.name} onChange={value => edit(x => x.character.name = value)} />
              <Field label={t.level} type="number" min={1} max={9999} value={doc.character.level} onChange={value => edit(x => x.character.level = value)} />
              <Field label={t.tag} value={doc.character.tag} onChange={value => edit(x => x.character.tag = value)} />
            </div>
            <div className="workshop-actions">
              <button className="primary" onClick={save}>{t.save}</button>
              <button onClick={createShare}>{t.share}</button>
              <button onClick={() => downloadDocument(doc)}>{t.export}</button>
            </div>
            {url && <div className="workshop-share">
              <p className="workshop-hint">{t.warning}</p>
              <img src={qr} alt="QR code: character share URL" width="260" height="260"/>
              <textarea readOnly aria-label="Shareable character link" value={url} rows={3} />
              <div className="workshop-actions"><button onClick={() => navigator.clipboard.writeText(url)}>{t.copy}</button>
                <a className="secondary" href={androidUrl(doc)}>{t.openApp}</a></div>
            </div>}
          </section>
          <section className="premium-panel">
            <div className="workshop-row-head"><h2>{t.modifiers}</h2><button onClick={() => edit(x => x.modifiers.push(newModifier(x.character.id)))}>{t.addModifier}</button></div>
            {doc.modifiers.map((m, index) => <div className="workshop-inline" key={m.id}>
              <Field label={t.modifiers} value={m.name} onChange={v => edit(x => x.modifiers[index].name = v)} />
              <Field label="±" type="number" value={m.value} onChange={v => edit(x => x.modifiers[index].value = v)} />
              <button onClick={() => edit(x => x.modifiers.splice(index, 1))} aria-label={t.remove}>×</button>
            </div>)}
          </section>
          <section className="premium-panel">
            <div className="workshop-row-head"><h2>{t.groups}</h2><button onClick={() => edit(x => x.groups.push(newGroup(x.character.id)))}>{t.addGroup}</button></div>
            {doc.groups.map((g,i) => <div className="workshop-inline" key={g.id}>
              <Field label={t.group} value={g.name} onChange={v => edit(x => x.groups[i].name = v)} />
              <button onClick={() => edit(x => { x.rolls.forEach(r => { if (r.groupId === g.id) r.groupId = null; }); x.groups.splice(i, 1); })} aria-label={t.remove}>×</button>
            </div>)}
          </section>
          <section className="premium-panel">
            <div className="workshop-row-head"><h2>{t.rolls}</h2><button onClick={() => edit(x => x.rolls.push(newRoll(x.character.id)))}>{t.addRoll}</button></div>
            {doc.rolls.map((r,i) => <details className="workshop-roll" key={r.id}>
              <summary><strong>{r.name || t.addRoll}</strong><span>{r.expression}</span></summary>
              <div className="workshop-inline">
                <Field label={t.rolls} value={r.name} onChange={v => edit(x => x.rolls[i].name = v)} />
                <button onClick={() => edit(x => x.rolls.splice(i, 1))}>{t.remove}</button>
              </div>
              <div className="workshop-fields">
                <label className="workshop-field"><span>{t.group}</span>
                  <select value={r.groupId || ''} onChange={e => edit(x => x.rolls[i].groupId = e.target.value || null)}>
                    <option value="">—</option>{doc.groups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
                  </select></label>
                <Field label={t.minimum} type="number" min={1} value={r.minimumLevel || 1} onChange={v => edit(x => x.rolls[i].minimumLevel = v)} />
                <label className="workshop-check"><input type="checkbox" checked={r.enabled !== false} onChange={e => edit(x => x.rolls[i].enabled = e.target.checked)} /> {t.enabled}</label>
                <label className="workshop-check"><input type="checkbox" checked={!!r.doubleRollEnabled} onChange={e => edit(x => x.rolls[i].doubleRollEnabled = e.target.checked)} /> {t.double}</label>
              </div>
              <Field label={t.formula} value={r.expression} readOnly={r.subgroups.length > 1} onChange={v => edit(x => { x.rolls[i].expression = v; if (x.rolls[i].subgroups.length === 1) { x.rolls[i].subgroups[0].expression = v; x.rolls[i].expression = normalFormula(x.rolls[i].subgroups); } })} />
              {r.subgroups.length > 1 && <small className="workshop-hint">{t.formulaFromParts}</small>}
              <div className="workshop-row-head"><h3>{t.parts}</h3><button onClick={() => edit(x => {
                const roll = x.rolls[i];
                if (!roll.subgroups.length) roll.subgroups = [{ ...newPart(), name: roll.name, expression: roll.expression }];
                else roll.subgroups.push(newPart());
                roll.expression = normalFormula(roll.subgroups);
              })}>{t.addPart}</button></div>
              {r.subgroups.map((p,j) => <div className="workshop-part" key={p.id}>
                <div className="workshop-inline">
                  <Field label={t.parts} value={p.name} onChange={v => edit(x => x.rolls[i].subgroups[j].name = v)} />
                  <Field label={t.formula} value={p.expression} onChange={v => edit(x => { const roll = x.rolls[i]; roll.subgroups[j].expression = v; roll.expression = normalFormula(roll.subgroups); })} />
                  <button disabled={isPartUsed(r, p.id)} title={isPartUsed(r, p.id) ? t.partUsed : t.remove} onClick={() => edit(x => removePartSafely(x.rolls[i], p.id, normalFormula))}>×</button>
                </div>
                <div className="workshop-inline">
                  <select value={p.operator || 'ADD'} onChange={e => edit(x => { const roll = x.rolls[i]; roll.subgroups[j].operator = e.target.value; roll.expression = normalFormula(roll.subgroups); })}>
                    <option value="ADD">+</option><option value="SUBTRACT">−</option></select>
                  <label className="workshop-check"><input type="checkbox" checked={p.includeInNormalRoll !== false} disabled={p.includeInNormalRoll !== false && r.subgroups.filter(q => q.includeInNormalRoll !== false).length === 1} onChange={e => edit(x => { const roll = x.rolls[i]; roll.subgroups[j].includeInNormalRoll = e.target.checked; roll.expression = normalFormula(roll.subgroups); })} /> {t.initialThrow}</label>
                                    <label className="workshop-check"><input type="checkbox" checked={!!p.includeInDoubleRoll} onChange={e => edit(x => x.rolls[i].subgroups[j].includeInDoubleRoll = e.target.checked)} /> {t.doubleCandidate}</label>
                </div>
              </div>)}
              <div className="workshop-row-head"><h3>{t.rules}</h3><button onClick={() => edit(x => x.rolls[i].levelRules.push(newLevelRule()))}>{t.addRule}</button></div>
              {r.levelRules.map((rule,j) => <div className="workshop-inline" key={rule.id}>
                <select value={rule.kind} onChange={e => edit(x => x.rolls[i].levelRules[j].kind = e.target.value)}>
                  <option value="FROM_LEVEL">From level</option><option value="EVERY_LEVELS">Every N levels</option></select>
                <Field label={t.level} type="number" min={1} value={rule.trigger} onChange={v => edit(x => x.rolls[i].levelRules[j].trigger = v)} />
                <Field label={t.formula} value={rule.expression} onChange={v => edit(x => x.rolls[i].levelRules[j].expression = v)} />
                <button onClick={() => edit(x => x.rolls[i].levelRules.splice(j,1))}>×</button>
              </div>)}
              <GuidedRollEditor roll={r} doc={doc} t={t} onEdit={mutator => edit(x => mutator(x.rolls[i]))} />
              <details className="workshop-expert"><summary>{t.expertJson}</summary>
                <p className="workshop-hint">{t.expertHelp}</p>
                <JsonField label={t.effects} value={r.effects} onChange={v => edit(x => x.rolls[i].effects = v)} />
                <JsonField label={t.appearance} value={r.diceAppearance || { mode: 'CHARACTER_DEFAULT' }} onChange={v => edit(x => x.rolls[i].diceAppearance = v)} />
              </details>
            </details>)}
          </section>
          <section className="premium-panel">
            <div className="workshop-row-head"><h2>{t.styles}</h2><button onClick={() => edit(x => x.diceStyles.push(newStyle()))}>{t.addStyle}</button></div>
            {doc.diceStyles.map((style,i) => <div className="workshop-part" key={style.id}>
              <div className="workshop-inline">
                <Field label={t.styles} value={style.name} onChange={v => edit(x => x.diceStyles[i].name = v)} />
                <select value={style.material} onChange={e => edit(x => x.diceStyles[i].material = e.target.value)}>
                  {['GLOSSY_RESIN','MATTE_RESIN','METAL','GEMSTONE'].map(s => <option key={s}>{s}</option>)}</select>
                <button onClick={() => edit(x => { x.diceStyles.splice(i, 1);
                  const fallback = 'preset:arcane-blue';
                  const fix = id => id === style.id ? fallback : id;
                  x.character.defaultDiceStyleId = fix(x.character.defaultDiceStyleId);
                  x.character.secondaryDiceStyleId = fix(x.character.secondaryDiceStyleId);
                  x.rolls.forEach(r => {
                    const a = r.diceAppearance || {};
                    if (a.styleId) a.styleId = fix(a.styleId);
                    if (a.subgroupStyleIds) a.subgroupStyleIds = a.subgroupStyleIds.map(v => ({ ...v, styleId: fix(v.styleId) }));
                    if (a.perDieStyleIds) a.perDieStyleIds = a.perDieStyleIds.map(v => ({ ...v, styleId: fix(v.styleId) }));
                    if (a.randomStyleIds) a.randomStyleIds = [...new Set(a.randomStyleIds.map(fix))];
                  }); })}>×</button>
              </div>
              <div className="workshop-inline">
                {['primaryColorArgb','secondaryColorArgb'].map(field => <label className="workshop-field" key={field}><span>{field}</span>
                  <input type="color" value={'#' + (style[field] >>> 0).toString(16).padStart(8,'0').slice(2)}
                    onChange={e => edit(x => x.diceStyles[i][field] = (0xff000000 | parseInt(e.target.value.slice(1), 16)) | 0)} />
                </label>)}
              </div>
            </div>)}
            <label className="workshop-field"><span>{t.defaultStyle}</span><select value={doc.character.defaultDiceStyleId || ''} onChange={e => edit(x => x.character.defaultDiceStyleId = e.target.value || null)}>
              <option value="">System default</option>
              {SYSTEM_STYLES.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              {doc.diceStyles.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select></label>
            <label className="workshop-field"><span>{t.secondaryStyle}</span>
              <select value={doc.character.secondaryDiceStyleId || ''} onChange={e => edit(x => x.character.secondaryDiceStyleId = e.target.value || null)}>
                <option value="">{t.styleAuto}</option>
                {SYSTEM_STYLES.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                {doc.diceStyles.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </label>
          </section>
        </>}
      </div>
    </div>
  </section>;
}
