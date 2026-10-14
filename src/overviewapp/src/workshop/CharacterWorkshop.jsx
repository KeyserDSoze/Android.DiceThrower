import { useEffect, useMemo, useState } from 'react';
import QRCode from 'qrcode';
import {
  androidUrl, decodeToken, documentFromEnvelope, envelope, importAsCopy, instantiateTemplate,
  listSaved, newCharacter, newGroup, newLevelRule, newModifier, newPart, newRoll, newStyle,
  normalFormula, readShareHash, removeDocument, saveDocument, shareUrl, templates, validateDocument,
  canUpgradeLegacyStarterWeaponRolls, upgradeLegacyStarterWeaponRolls, withImportPreferences,
  WIZARD_2014_SRD_SPELLS, FIGHTER_2014_LEVELS, ROGUE_2014_LEVELS, WIZARD_2014_LEVELS, RANGER_2014_LEVELS, CLERIC_2014_LEVELS, PALADIN_2014_LEVELS, BARD_2014_LEVELS,
} from './character.js';
import GuidedRollEditor from './GuidedRollEditor.jsx';
import { isPartUsed, removePartSafely, SYSTEM_STYLES } from './guided.js';

function orderedGroups(characterDoc) {
  if (!characterDoc) return [];
  const rolls = characterDoc.rolls.map((roll,index)=>({roll,index}))
    .sort((a,b)=>(a.roll.order ?? 0)-(b.roll.order ?? 0) || a.index-b.index);
  const groups = [...characterDoc.groups].sort((a,b)=>
    (a.order ?? 0)-(b.order ?? 0) || a.name.localeCompare(b.name));
  return [...groups.map(group=>({group,items:rolls.filter(item=>item.roll.groupId===group.id)})),
    {group:null,items:rolls.filter(item=>!item.roll.groupId)}]
    .filter(entry=>entry.group || entry.items.length);
}

const dictionary = {
  en: {
    title: 'Character workshop', intro: 'Design a character on the web. Keep it privately in this browser, or share by link or QR. Large unchanged starter templates may use a versioned template reference.',
    storage: 'Saved on this device · IndexedDB', noCloud: 'Not synced with Google Drive yet. Clearing browser data may delete these characters.',
    library: 'Your characters', starter: 'Starter characters', create: 'New character', load: 'Edit', delete: 'Delete', use: 'Use template',
    save: 'Save in browser', share: 'Share link & QR', export: 'Export JSON', import: 'Import JSON', copy: 'Copy link',
    openApp: 'Open in Android app', preview: 'Character shared with you', add: 'Add to my browser', discard: 'Dismiss',
    importCheck: 'Review before adding', importSource: 'Shared character', importSettings: 'Choose name and level before importing',
    importExisting: 'Import a new independent copy. Your existing characters remain untouched.',
    appDirect: 'Send this character to the Android app', appDirectHint: 'Opens Dice Thrower if it is installed and supports character import.',
    appLarge: 'Character too large for reliable app handoff. Export the JSON file and import it inside Dice Thrower instead.',
    templateLinkNote: 'This QR refers to a versioned official D&D starter preset on this site, not to a standalone backup. Export JSON to keep an independent copy.',
    everyN: 'Every N levels', periodicStart: 'First level (optional)', periodicMax: 'Maximum additions (optional)',
    unlockedAt: 'Unlocked at level', ungrouped: 'Ungrouped rolls',
    addToGroup: '+ Roll in group', rollsInGroup: 'rolls',
    spellCatalogue: 'Wizard SRD 2014 · Spell catalogue',
    spellCatalogueNote: 'All SRD Wizard spells are references, not automatically known or prepared. Only spells with a meaningful dice/check preset appear as Rolls. Choose spells, targets, save results, concentration and spent slots manually.',
    spellWithRoll: 'Dice preset', spellReference: 'Reference only',
    spellLevel: 'Spell Lv', cantripTitle: 'Cantrips',
    fighterProgression: 'Fighter 2014 · levels 1–20', fighterProficiency: 'Proficiency',
    rogueProgression: 'Rogue 2014 · levels 1–20', rogueSneak: 'Sneak Attack',
    wizardProgression: 'Wizard 2014 · levels 1–20', wizardSlots: 'Slots (1–9)', wizardCantrips: 'cantrips known',
    rangerProgression: 'Ranger 2014 · levels 1–20', rangerSlots: 'Slots (1–5)',
    clericProgression: 'Cleric 2014 · levels 1–20', clericSlots: 'Slots (1–9)', clericCantrips: 'cantrips known',
    paladinProgression: 'Paladin 2014 · levels 1–20', paladinSlots: 'Slots (1–5)', paladinLayOnHands: 'Lay on Hands HP',
    bardProgression: 'Bard 2014 · levels 1–20', bardSlots: 'Slots (1–9)', bardKnown: 'spells known', bardInspiration: 'Inspiration',
    bardChoiceNote: 'Bardic Inspiration dice are given to another creature who can hear you: a d6 at level 1, d8 at 5, d10 at 10, d12 at 15. Spending uses is manual. Song of Rest applies only when eligible creatures spend Hit Dice at the end of a short rest. Vicious Mockery is damage only on a failed target Wisdom save: it has no spell attack or critical, and scales 1/2/3/4d4 at levels 1/5/11/17. Jack of All Trades is configured only for an unproficient sample ability check, with half proficiency rounded down; do not apply it to already proficient checks. Class college, Expertise, Magical Secrets, learned spells and spent slots are player choices. Spell save DC = 8 + CHA + proficiency.',
    paladinChoiceNote: 'At level 5 use Longsword · 2 attacks. At level 11 use its Improved Smite alternative: weapon hits gain another 1d8 radiant and a natural 20 doubles both the sword and this extra die, never Strength. Divine Smite is an optional separate damage Roll, usable only after a melee weapon hit and spending a spell slot: choose exactly one regular/critical and undead-or-fiend alternative. If a critical hit, double only its dice. Spell slots are not automatically spent. Lay on Hands (5 HP per class level), Fighting Style, Aura, subclass Oath, ASI and resources must be handled manually.',
    clericChoiceNote: 'Sacred Flame calls for the target’s Dexterity save: roll only the damage tier (1d8/2d8/3d8/4d8 at levels 1/5/11/17), never an attack/critical. Healing Word and Cure Wounds heal Nd4 or Nd8 + Wisdom when the spell is prepared and cast with an Nth-level slot (1–9). Guidance and Bless d4 are optional and only apply to eligible checks/attacks/saves. Spell save DC is 8 + Wisdom + proficiency. Spell preparation, chosen Divine Domain, Channel Divinity, Divine Intervention and expended slots remain manual.',
    rangerChoiceNote: 'Extra Attack: use Bow · 2 attacks from level 5 (each attack has its own natural-20 critical). Hunter’s Mark damage is optional and requires the spell to be known, an eligible marked target, an active concentration and a hit; on a critical choose its 2d6 variant INSTEAD of 1d6. Cure Wounds and spell attacks are optional examples, not automatic known spells; slots are reference values, not expended automatically. Favored Enemy, terrain, Fighting Style, ASI and Ranger Archetype remain player choices.',
    wizardChoiceNote: 'Spell Lv 1–9 are native Roll groups shared with Android. The 2014 Wizard SRD catalogue is only a reference, not a prepared spellbook: choose which spells are known, prepared and cast. The optional highest-unlocked-slot Rolls use character level only to select the maximum available spell slot; they do not automatically spend a slot, and you can always use the fixed base-slot Roll.   Fire Bolt has a tier-specific attack/damage/critical preset at levels 1/5/11/17: use the highest currently unlocked tier. Magic Missile rolls list each dart separately with one extra dart per higher spell slot (3 through 11); the player assigns targets and chooses a spell/slot. Prepared spells, spent slots, Arcane Tradition, Arcane Recovery, Spell Mastery and Signature Spells are not auto-tracked. If your table rolls a single shared d4 for all Magic Missile darts, use one Dart Part and apply that result to all darts.',
    rogueChoiceNote: 'Sneak Attack is conditional (once per turn, eligible finesse/ranged hit with advantage or ally nearby): throw its separate damage Roll only when eligible. Use the separate critical Sneak Attack Roll on a critical hit; the Dagger critical only adds its own weapon die. Expertise/skill choices, ASI, archetype, Reliable Talent and resource uses require player choices or manual adjudication. Slippery Mind unlocks Wisdom save proficiency at level 15.',
    fighterChoiceNote: 'Ability Score Improvements, feats, Fighting Style and Martial Archetype are player choices. Edit the relevant modifiers and rolls after choosing. For Indomitable, roll the relevant saving throw again and use its new result; ability and resource choices are not tracked by the dice engine.',
    fighterEvents: [
      'Fighting Style, Second Wind', 'Action Surge (1 use)', 'Choose Martial Archetype',
      'Ability Score Improvement (choice)', 'Extra Attack: 2 attacks',
      'Ability Score Improvement (choice)', 'Martial Archetype feature', 'Ability Score Improvement (choice)',
      'Indomitable (1 use)', 'Martial Archetype feature', 'Extra Attack: 3 attacks',
      'Ability Score Improvement (choice)', 'Indomitable (2 uses)', 'Ability Score Improvement (choice)',
      'Martial Archetype feature', 'Ability Score Improvement (choice)',
      'Action Surge (2 uses), Indomitable (3 uses)', 'Martial Archetype feature',
      'Ability Score Improvement (choice)', 'Extra Attack: 4 attacks',
    ],
    rogueEvents: [
      'Expertise (choose 2), Sneak Attack, Thieves’ Cant', 'Cunning Action',
      'Choose Roguish Archetype', 'Ability Score Improvement (choice)', 'Uncanny Dodge',
      'Expertise (choose 2 more)', 'Evasion', 'Ability Score Improvement (choice)',
      'Roguish Archetype feature', 'Ability Score Improvement (choice)',
      'Reliable Talent (apply manually)', 'Ability Score Improvement (choice)',
      'Roguish Archetype feature', 'Blindsense', 'Slippery Mind (Wisdom saves)',
      'Ability Score Improvement (choice)', 'Roguish Archetype feature',
      'Elusive', 'Ability Score Improvement (choice)', 'Stroke of Luck (manual)',
    ],
    wizardEvents: [
      'Spellcasting, Arcane Recovery', 'Choose Arcane Tradition', '2nd-level slots',
      'Ability Score Improvement (choice)', '3rd-level slots', 'Arcane Tradition feature',
      '4th-level slots', 'Ability Score Improvement (choice)', '5th-level slots',
      'Arcane Tradition feature', '6th-level slots', 'Ability Score Improvement (choice)',
      '7th-level slots', 'Arcane Tradition feature', '8th-level slots',
      'Ability Score Improvement (choice)', '9th-level slots', 'Spell Mastery (choice)',
      'Ability Score Improvement (choice)', 'Signature Spells (choice)',
    ],
    rangerEvents: [
      'Favored Enemy, Natural Explorer', 'Fighting Style, Spellcasting',
      'Choose Ranger Archetype, Primeval Awareness', 'Ability Score Improvement (choice)',
      'Extra Attack: 2 attacks', 'Favored Enemy, Natural Explorer improvements',
      'Ranger Archetype feature', 'Ability Score Improvement (choice), Land’s Stride',
      '3rd-level slots', 'Natural Explorer improvement, Hide in Plain Sight',
      'Ranger Archetype feature', 'Ability Score Improvement (choice)',
      '4th-level slots', 'Favored Enemy improvement, Vanish',
      'Ranger Archetype feature', 'Ability Score Improvement (choice)',
      '5th-level slots', 'Feral Senses', 'Ability Score Improvement (choice)', 'Foe Slayer',
    ],
    clericEvents: [
      'Spellcasting, choose Divine Domain', 'Channel Divinity (1/rest), Domain feature',
      '2nd-level spell slots', 'Ability Score Improvement (choice)',
      'Destroy Undead (CR 1/2), 3rd-level slots', 'Channel Divinity (2/rest), Domain feature',
      '4th-level slots', 'Ability Score Improvement, Destroy Undead (CR 1), Domain feature',
      '5th-level slots', 'Divine Intervention (manual)',
      'Destroy Undead (CR 2), 6th-level slots', 'Ability Score Improvement (choice)',
      '7th-level slots', 'Destroy Undead (CR 3)', '8th-level slots',
      'Ability Score Improvement (choice)', 'Destroy Undead (CR 4), Domain feature, 9th-level slots',
      'Channel Divinity (3/rest)', 'Ability Score Improvement (choice)',
      'Divine Intervention improvement',
    ],
    paladinEvents: [
      'Divine Sense, Lay on Hands', 'Fighting Style, Spellcasting, Divine Smite',
      'Divine Health, choose Sacred Oath', 'Ability Score Improvement (choice)',
      'Extra Attack: 2 attacks', 'Aura of Protection', 'Sacred Oath feature',
      'Ability Score Improvement (choice)', '3rd-level slots', 'Aura of Courage',
      'Improved Divine Smite (+1d8/hit)', 'Ability Score Improvement (choice)',
      '4th-level slots', 'Cleansing Touch', 'Sacred Oath feature',
      'Ability Score Improvement (choice)', '5th-level slots',
      'Aura improvements', 'Ability Score Improvement (choice)', 'Sacred Oath feature',
    ],
    bardEvents: [
      'Spellcasting, Bardic Inspiration d6', 'Jack of All Trades, Song of Rest d6',
      'Choose Bard College, Expertise (2 skills)', 'Ability Score Improvement (choice)',
      'Bardic Inspiration d8, Font of Inspiration', 'Countercharm, Bard College feature',
      '4th-level slots', 'Ability Score Improvement (choice)', 'Song of Rest d8',
      'Inspiration d10, Expertise (2 more), Magical Secrets',
      '6th-level slots', 'Ability Score Improvement (choice)',
      'Song of Rest d10, 7th-level slots', 'Magical Secrets, Bard College feature',
      'Bardic Inspiration d12', 'Ability Score Improvement (choice)',
      'Song of Rest d12, 9th-level slots', 'Magical Secrets',
      'Ability Score Improvement (choice)', 'Superior Inspiration',
    ],
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
    actionAdd: 'Add', actionSubtract: 'Subtract', actionMultiply: 'Multiply',
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
    upgradeLegacy: 'Update old starter weapon rolls', upgradeConfirm: 'Replace the two original weapon Rolls with Attack and Damage Parts? Your other character data will be preserved and the result saved locally.', upgradeComplete: 'Starter weapon rolls updated and saved.',
    download: 'Download / keep backup', select: 'Open a character or create one.', saved: 'Saved locally.', ready: 'Link ready.',
  },
  it: {
    title: 'Laboratorio personaggi', intro: 'Crea il personaggio sul sito. Salvalo privatamente nel browser oppure condividilo via link o QR. I modelli standard più grandi possono usare un riferimento alla versione del modello.',
    storage: 'Salvato su questo dispositivo · IndexedDB', noCloud: 'Non ancora sincronizzato con Google Drive. La pulizia dei dati del browser può cancellare i personaggi.',
    library: 'I tuoi personaggi', starter: 'Personaggi preimpostati', create: 'Nuovo personaggio', load: 'Modifica', delete: 'Elimina', use: 'Usa modello',
    save: 'Salva nel browser', share: 'Condividi link e QR', export: 'Esporta JSON', import: 'Importa JSON', copy: 'Copia link',
    openApp: "Apri nell'app Android", preview: 'Personaggio ricevuto', add: 'Aggiungi al browser', discard: 'Chiudi',
    importCheck: 'Controlla prima di aggiungere', importSource: 'Personaggio condiviso', importSettings: 'Scegli nome e livello prima dell’importazione',
    importExisting: 'Importa una nuova copia indipendente. I personaggi esistenti non cambieranno.',
    appDirect: 'Invia questo personaggio all’app Android', appDirectHint: 'Apre Dice Thrower se è installata e supporta l’importazione dei personaggi.',
    appLarge: 'Personaggio troppo grande per il passaggio diretto all’app. Esporta il JSON e importalo da Dice Thrower.',
    templateLinkNote: 'Questo QR richiama un modello D&D predefinito e versionato sul sito. Non sostituisce un backup indipendente: esporta il JSON per conservarne una copia.',
    everyN: 'Ogni N livelli', periodicStart: 'Primo livello (opzionale)', periodicMax: 'Numero massimo di aggiunte (opzionale)',
    unlockedAt: 'Disponibile dal livello', ungrouped: 'Tiri senza gruppo',
    addToGroup: '+ Tiro nel gruppo', rollsInGroup: 'tiri',
    spellCatalogue: 'Mago SRD 2014 · Catalogo incantesimi',
    spellCatalogueNote: 'Tutti gli incantesimi SRD del Mago sono riferimenti: non sono automaticamente conosciuti né preparati. Solo quelli che richiedono davvero dadi o prove hanno un tiro predisposto. Incantesimi, bersagli, tiri salvezza, concentrazione e slot spesi restano manuali.',
    spellWithRoll: 'Tiro predisposto', spellReference: 'Solo riferimento',
    spellLevel: 'Magie Lv', cantripTitle: 'Trucchetti',
    fighterProgression: 'Guerriero 2014 · livelli 1–20', fighterProficiency: 'Competenza',
    rogueProgression: 'Ladro 2014 · livelli 1–20', rogueSneak: 'Attacco Furtivo',
    wizardProgression: 'Mago 2014 · livelli 1–20', wizardSlots: 'Slot (1–9)', wizardCantrips: 'trucchetti conosciuti',
    rangerProgression: 'Ranger 2014 · livelli 1–20', rangerSlots: 'Slot (1–5)',
    clericProgression: 'Chierico 2014 · livelli 1–20', clericSlots: 'Slot (1–9)', clericCantrips: 'trucchetti conosciuti',
    paladinProgression: 'Paladino 2014 · livelli 1–20', paladinSlots: 'Slot (1–5)', paladinLayOnHands: 'Imposizione delle Mani PF',
    bardProgression: 'Bardo 2014 · livelli 1–20', bardSlots: 'Slot (1–9)', bardKnown: 'incantesimi conosciuti', bardInspiration: 'Ispirazione',
    bardChoiceNote: 'Il dado di Ispirazione Bardica va assegnato a un’altra creatura che ti sente: d6 dal livello 1, d8 dal 5, d10 dal 10, d12 dal 15. Gli usi si consumano manualmente. Canto di Riposo si applica solo quando una creatura spende Dadi Vita dopo un riposo breve. Scherno Crudele infligge danni solo se il bersaglio fallisce il TS Saggezza: non ha tiro per colpire o critico; 1/2/3/4d4 ai livelli 1/5/11/17. Jack of All Trades vale solo per le prove senza competenza e applica metà competenza arrotondata per difetto. Collegio, Maestria, Segreti Magici, incantesimi imparati e slot consumati restano scelte del giocatore. CD incantesimi = 8 + CAR + competenza.',
    paladinChoiceNote: 'Dal livello 5 usa Spada Lunga · 2 attacchi. Dal livello 11 usa la variante con Punizione Divina Migliorata: ogni colpo a segno aggiunge 1d8 radiante e sul 20 naturale si raddoppiano i dadi dell’arma e quello extra, mai Forza. La Punizione Divina opzionale va lanciata separatamente soltanto dopo un colpo in mischia e consumando uno slot: scegli UNA sola variante normale/critico e contro non morti o immondi. Su critico raddoppia solo i dadi. Gli slot non si consumano automaticamente. Imposizione delle Mani (5 PF per livello), Stile di Combattimento, Aure, Giuramento, caratteristiche e risorse sono gestiti manualmente.',
    clericChoiceNote: 'Fiamma Sacra richiede un tiro salvezza di Destrezza del bersaglio: usa soltanto il danno appropriato (1d8/2d8/3d8/4d8 ai livelli 1/5/11/17), non un tiro per colpire o critico. Parola Guaritrice e Cura Ferite curano Nd4 o Nd8 + Saggezza se preparati e lanciati con uno slot di livello N (1–9). Guida e Benedizione sono d4 opzionali applicabili solo a prove/attacchi/tiri salvezza idonei. CD degli incantesimi = 8 + Saggezza + competenza. Preparazione, Dominio Divino, Incanalare Divinità, Intervento Divino e slot spesi restano gestiti dal giocatore.',
    rangerChoiceNote: 'Attacco Extra: dal livello 5 usa Arco · 2 attacchi (ogni attacco ha un proprio critico con 20 naturale). Danno Marchio del Cacciatore solo se conosci l’incantesimo, mantieni concentrazione, il bersaglio è marchiato e il colpo va a segno; su critico usa 2d6 INVECE di 1d6. Cura Ferite e attacchi magici sono esempi opzionali, non incantesimi automaticamente conosciuti; gli slot sono valori di riferimento. Nemico Prescelto, terreno, Stile di Combattimento, caratteristiche e Archetipo sono scelte del giocatore.',
    wizardChoiceNote: 'Spell Lv 1–9 sono i gruppi di tiri nativi condivisi con Android. Il catalogo SRD 2014 è solo un riferimento: scegli quali incantesimi conosci, prepari e lanci. I tiri opzionali massimo slot sbloccato usano il livello del personaggio per individuare solo lo slot massimo disponibile, senza consumarlo. Resta sempre disponibile il tiro base con slot minimo.   Dardo di Fuoco ha attacco/danno/critico separato ai livelli 1/5/11/17: usa la variante sbloccata più alta. Dardo Incantato mostra ogni dardo con una Parte separata (3–11 dardi secondo lo slot): scegli il bersaglio e lo slot. Incantesimi preparati, slot spesi, Tradizione Arcana, Recupero Arcano, Padronanza Magica e Incantesimi Distintivi sono gestiti dal giocatore. Se il tavolo tira un unico d4 per tutti i dardi, tira una sola Parte e usa quel risultato per ciascun dardo.',
    rogueChoiceNote: 'Attacco Furtivo è condizionale (una volta per turno, arma accurata/a distanza, vantaggio o alleato vicino): usa il tiro danno separato solo se applicabile. Su un critico usa il tiro Attacco Furtivo critico; il critico del pugnale aggiunge solo il dado dell’arma. Maestria, abilità, incrementi di caratteristica, archetipo, Talento Affidabile e risorse sono scelte o valutazioni manuali. Mente Sfuggente sblocca la competenza al TS Saggezza dal livello 15.',
    fighterChoiceNote: 'Aumenti di caratteristica, talenti, Stile di Combattimento e Archetipo Marziale sono scelte del giocatore. Modifica i relativi valori e tiri dopo aver scelto. Per Indomito, ripeti il tiro salvezza interessato e usa il nuovo risultato. Le scelte e gli usi delle risorse non vengono tracciati dal motore.',
    fighterEvents: [
      'Stile di Combattimento, Recuperare Energie', 'Azione Impetuosa (1 uso)', 'Scegli Archetipo Marziale',
      'Aumento delle caratteristiche (scelta)', 'Attacco Extra: 2 attacchi',
      'Aumento delle caratteristiche (scelta)', 'Privilegio di Archetipo', 'Aumento delle caratteristiche (scelta)',
      'Indomito (1 uso)', 'Privilegio di Archetipo', 'Attacco Extra: 3 attacchi',
      'Aumento delle caratteristiche (scelta)', 'Indomito (2 usi)', 'Aumento delle caratteristiche (scelta)',
      'Privilegio di Archetipo', 'Aumento delle caratteristiche (scelta)',
      'Azione Impetuosa (2 usi), Indomito (3 usi)', 'Privilegio di Archetipo',
      'Aumento delle caratteristiche (scelta)', 'Attacco Extra: 4 attacchi',
    ],
    rogueEvents: [
      'Maestria (scegli 2), Attacco Furtivo, Gergo Ladresco', 'Azione Scaltra',
      'Scegli Archetipo Ladresco', 'Aumento delle caratteristiche (scelta)', 'Schivata Prodigiosa',
      'Maestria (scegli altre 2)', 'Elusione', 'Aumento delle caratteristiche (scelta)',
      'Privilegio di Archetipo', 'Aumento delle caratteristiche (scelta)',
      'Talento Affidabile (manuale)', 'Aumento delle caratteristiche (scelta)',
      'Privilegio di Archetipo', 'Percezione Cieca', 'Mente Sfuggente (TS Saggezza)',
      'Aumento delle caratteristiche (scelta)', 'Privilegio di Archetipo',
      'Inafferrabile', 'Aumento delle caratteristiche (scelta)', 'Colpo di Fortuna (manuale)',
    ],
    wizardEvents: [
      'Lancio degli Incantesimi, Recupero Arcano', 'Scegli Tradizione Arcana', 'Slot di 2° livello',
      'Aumento delle caratteristiche (scelta)', 'Slot di 3° livello', 'Privilegio di Tradizione Arcana',
      'Slot di 4° livello', 'Aumento delle caratteristiche (scelta)', 'Slot di 5° livello',
      'Privilegio di Tradizione Arcana', 'Slot di 6° livello', 'Aumento delle caratteristiche (scelta)',
      'Slot di 7° livello', 'Privilegio di Tradizione Arcana', 'Slot di 8° livello',
      'Aumento delle caratteristiche (scelta)', 'Slot di 9° livello', 'Padronanza Magica (scelta)',
      'Aumento delle caratteristiche (scelta)', 'Incantesimi Distintivi (scelta)',
    ],
    rangerEvents: [
      'Nemico Prescelto, Esploratore Nato', 'Stile di Combattimento, Lancio Incantesimi',
      'Scegli Archetipo Ranger, Consapevolezza Primordiale',
      'Aumento delle caratteristiche (scelta)', 'Attacco Extra: 2 attacchi',
      'Miglioramenti Nemico Prescelto ed Esploratore Nato', 'Privilegio di Archetipo',
      'Aumento delle caratteristiche, Andatura sul Terreno', 'Slot di 3° livello',
      'Esploratore Nato, Nascondersi in Piena Vista', 'Privilegio di Archetipo',
      'Aumento delle caratteristiche (scelta)', 'Slot di 4° livello',
      'Miglioramento Nemico Prescelto, Svanire', 'Privilegio di Archetipo',
      'Aumento delle caratteristiche (scelta)', 'Slot di 5° livello',
      'Sensi Ferini', 'Aumento delle caratteristiche (scelta)', 'Uccisore di Nemici',
    ],
    clericEvents: [
      'Lancio degli Incantesimi, scegli Dominio Divino', 'Incanalare Divinità (1/riposo), privilegio di Dominio',
      'Slot di 2° livello', 'Aumento delle caratteristiche (scelta)',
      'Distruggere Non Morti (GS 1/2), slot di 3° livello',
      'Incanalare Divinità (2/riposo), privilegio di Dominio',
      'Slot di 4° livello', 'Aumento delle caratteristiche, Distruggere Non Morti (GS 1), Dominio',
      'Slot di 5° livello', 'Intervento Divino (manuale)',
      'Distruggere Non Morti (GS 2), slot di 6° livello',
      'Aumento delle caratteristiche (scelta)', 'Slot di 7° livello',
      'Distruggere Non Morti (GS 3)', 'Slot di 8° livello',
      'Aumento delle caratteristiche (scelta)',
      'Distruggere Non Morti (GS 4), privilegio di Dominio, slot di 9° livello',
      'Incanalare Divinità (3/riposo)', 'Aumento delle caratteristiche (scelta)',
      'Miglioramento Intervento Divino',
    ],
    paladinEvents: [
      'Senso Divino, Imposizione delle Mani', 'Stile di Combattimento, Incantesimi, Punizione Divina',
      'Salute Divina, scegli Giuramento Sacro', 'Aumento delle caratteristiche (scelta)',
      'Attacco Extra: 2 attacchi', 'Aura di Protezione', 'Privilegio di Giuramento',
      'Aumento delle caratteristiche (scelta)', 'Slot di 3° livello', 'Aura di Coraggio',
      'Punizione Divina Migliorata (+1d8/colpo)', 'Aumento delle caratteristiche (scelta)',
      'Slot di 4° livello', 'Tocco Purificatore', 'Privilegio di Giuramento',
      'Aumento delle caratteristiche (scelta)', 'Slot di 5° livello',
      'Miglioramento delle Aure', 'Aumento delle caratteristiche (scelta)', 'Privilegio di Giuramento',
    ],
    bardEvents: [
      'Lancio degli Incantesimi, Ispirazione Bardica d6',
      'Jack of All Trades, Canto di Riposo d6',
      'Scegli Collegio Bardico, Maestria (2 abilità)',
      'Aumento delle caratteristiche (scelta)',
      'Ispirazione Bardica d8, Fonte di Ispirazione',
      'Controfascino, privilegio del Collegio', 'Slot di 4° livello',
      'Aumento delle caratteristiche (scelta)', 'Canto di Riposo d8',
      'Ispirazione d10, Maestria (altre 2), Segreti Magici',
      'Slot di 6° livello', 'Aumento delle caratteristiche (scelta)',
      'Canto di Riposo d10, slot di 7° livello',
      'Segreti Magici, privilegio del Collegio',
      'Ispirazione Bardica d12', 'Aumento delle caratteristiche (scelta)',
      'Canto di Riposo d12, slot di 9° livello',
      'Segreti Magici', 'Aumento delle caratteristiche (scelta)', 'Ispirazione Superiore',
    ],
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
    actionAdd: 'Aggiungi', actionSubtract: 'Sottrai', actionMultiply: 'Moltiplica',
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
    upgradeLegacy: 'Aggiorna i vecchi tiri del modello', upgradeConfirm: 'Sostituire i due vecchi tiri dell’arma con le parti Attacco e Danno? Gli altri dati del personaggio saranno conservati e il risultato salvato in locale.', upgradeComplete: 'Tiri del modello aggiornati e salvati.',
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
  const [previewName, setPreviewName] = useState('');
  const [previewLevel, setPreviewLevel] = useState(1);
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
    if (window.location.hash.startsWith('#character=') || window.location.hash.startsWith('#template=')) {
      try { setPreview(readShareHash()); } catch (e) { setError(e.message); }
    }
  }, []);
  useEffect(() => {
    if (preview) {
      setPreviewName(preview.character.name);
      setPreviewLevel(preview.character.level);
    }
  }, [preview]);

  const importCandidate = useMemo(() => {
    if (!preview) return null;
    try { return withImportPreferences(preview, previewName, previewLevel); }
    catch { return null; }
  }, [preview, previewName, previewLevel]);
  const importAppLink = useMemo(() => {
    if (!importCandidate) return null;
    try {
      const link = androidUrl(importCandidate);
      return link.length <= 8000 ? link : null;
    } catch { return null; }
  }, [importCandidate]);
  const editorAppLink = useMemo(() => {
    if (!doc) return null;
    try {
      const link = androidUrl(doc);
      return link.length <= 8000 ? link : null;
    } catch { return null; }
  }, [doc]);


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
  async function upgradeStarter() {
    if (!doc || !canUpgradeLegacyStarterWeaponRolls(doc) || !window.confirm(t.upgradeConfirm)) return;
    try {
      const upgraded = upgradeLegacyStarterWeaponRolls(doc);
      if (!upgraded) return;
      await saveDocument(upgraded);
      setDoc(upgraded); setUrl(''); setQr(''); setError('');
      await refresh();
      setMessage(t.upgradeComplete);
    } catch (e) { setError(e.message); }
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
      if (!importCandidate) throw new Error(t.invalid);
      const copy = importAsCopy(importCandidate);
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
      const imported = documentFromEnvelope(packet);
      setPreview(imported);
      setError(''); setMessage('');
      // The same explicit preview and confirmation now handles QR, link and file.

    } catch (e) { setError(e.message); }
    event.target.value = '';
  }

  const isRogue2014 = doc?.character.tag.startsWith('D&D 5e (2014) Rogue sample');
  const isFighter2014 = doc?.character.tag.startsWith('D&D 5e sample');
  const isWizard2014 = doc?.character.tag.startsWith('D&D 5e (2014) Wizard sample');
  const isRanger2014 = doc?.character.tag.startsWith('D&D 5e (2014) Ranger sample');
  const isCleric2014 = doc?.character.tag.startsWith('D&D 5e (2014) Cleric sample');
  const isPaladin2014 = doc?.character.tag.startsWith('D&D 5e (2014) Paladin sample');
  const isBard2014 = doc?.character.tag.startsWith('D&D 5e (2014) Bard sample');
  const dndLevels = isBard2014 ? BARD_2014_LEVELS : isPaladin2014 ? PALADIN_2014_LEVELS : isCleric2014 ? CLERIC_2014_LEVELS : isRanger2014 ? RANGER_2014_LEVELS : isWizard2014 ? WIZARD_2014_LEVELS : isRogue2014 ? ROGUE_2014_LEVELS : FIGHTER_2014_LEVELS;

  const renderRollEditor = (r,i) => (<details className="workshop-roll" key={r.id}>
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
                <Field label={rule.kind === 'EVERY_LEVELS' ? t.everyN : t.level} type="number" min={1} value={rule.trigger} onChange={v => edit(x => x.rolls[i].levelRules[j].trigger = v)} />
                {rule.kind === 'EVERY_LEVELS' && <>
                  <Field label={t.periodicStart} type="number" min={1} value={rule.startLevel ?? ''} onChange={v => edit(x => { if (v === '') delete x.rolls[i].levelRules[j].startLevel; else x.rolls[i].levelRules[j].startLevel = Number(v); })} />
                  <Field label={t.periodicMax} type="number" min={1} value={rule.maxRepeats ?? ''} onChange={v => edit(x => { if (v === '') delete x.rolls[i].levelRules[j].maxRepeats; else x.rolls[i].levelRules[j].maxRepeats = Number(v); })} />
                </>}
                <Field label={t.formula} value={rule.expression} onChange={v => edit(x => x.rolls[i].levelRules[j].expression = v)} />
                <button onClick={() => edit(x => x.rolls[i].levelRules.splice(j,1))}>×</button>
              </div>)}
              <GuidedRollEditor roll={r} doc={doc} t={t} onEdit={mutator => edit(x => mutator(x.rolls[i]))} />
              <details className="workshop-expert"><summary>{t.expertJson}</summary>
                <p className="workshop-hint">{t.expertHelp}</p>
                <JsonField label={t.effects} value={r.effects} onChange={v => edit(x => x.rolls[i].effects = v)} />
                <JsonField label={t.appearance} value={r.diceAppearance || { mode: 'CHARACTER_DEFAULT' }} onChange={v => edit(x => x.rolls[i].diceAppearance = v)} />
              </details>
            </details>);

  return <section className="workshop">
    <div className="workshop-heading">
      <span className="eyebrow">Offline-first · No account</span>
      <h1>{t.title}</h1><p>{t.intro}</p>
      <p className="workshop-hint">{t.storage} · {t.noCloud}</p>
    </div>
    {error && <p className="workshop-error" role="alert">{error}</p>}
    {message && <p className="workshop-success" role="status">{message}</p>}
    {preview && <section className="premium-panel workshop-preview" aria-label={t.preview}>
      <div className="workshop-row-head"><h2>{t.importCheck}</h2><span className="eyebrow">{t.importSource}</span></div>
      <div className="workshop-character-card">
        <div className="workshop-character-top">
          <div className="workshop-character-avatar" aria-hidden="true">{preview.character.name.slice(0,1).toUpperCase()}</div>
          <div><h3>{preview.character.name}</h3><p>{t.level} {preview.character.level} · {preview.character.tag}</p></div>
        </div>
        <div className="workshop-preview-stats">
          <span>{preview.modifiers.length} {t.modifiers}</span>
          <span>{preview.groups.length} {t.groups}</span>
          <span>{preview.rolls.length} {t.rolls}</span>
        </div>
        <details className="workshop-preview-rolls"><summary>{t.rolls}</summary>
          <div className="workshop-preview-list">
            {orderedGroups(preview).map(({group,items}) =>
              <details className="workshop-roll-group" key={group?.id || 'ungrouped'}>
                <summary><strong>{group?.name || t.ungrouped}</strong><span>{items.length} {t.rollsInGroup}</span></summary>
                <div className="workshop-group-content">
                  {items.map(({roll})=><div key={roll.id} className="workshop-preview-roll">
                    <strong>{roll.name}</strong>
                    {roll.minimumLevel > 1 && <small>{t.unlockedAt} {roll.minimumLevel}</small>}
                    {!!roll.subgroups?.length && <small>{roll.subgroups.map(part=>part.name).join(' · ')}</small>}
                  </div>)}
                </div>
              </details>)}
          </div>
        </details>
      </div>
      <p className="workshop-hint">{t.importSettings}</p>
      <div className="workshop-fields">
        <Field label={t.characterName} value={previewName} onChange={setPreviewName} />
        <Field label={t.level} type="number" min={1} max={9999} value={previewLevel} onChange={setPreviewLevel} />
      </div>
      <p className="workshop-hint">{t.importExisting} {t.warning}</p>
      <div className="workshop-actions">
        <button className="primary" disabled={!importCandidate} onClick={addShared}>{t.add}</button>
        {importAppLink && <a className="secondary" href={importAppLink}>{t.openApp}</a>}
        <button onClick={() => { setPreview(null); history.replaceState(null, '', location.pathname); }}>{t.discard}</button>
      </div>
      <p className="workshop-hint">{importAppLink ? t.appDirectHint : t.appLarge}</p>
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
            {(isFighter2014 || isRogue2014 || isWizard2014 || isRanger2014 || isCleric2014 || isPaladin2014 || isBard2014) && <details className="workshop-fighter-timeline">
              <summary>{isBard2014 ? t.bardProgression : isPaladin2014 ? t.paladinProgression : isCleric2014 ? t.clericProgression : isRanger2014 ? t.rangerProgression : isWizard2014 ? t.wizardProgression : isRogue2014 ? t.rogueProgression : t.fighterProgression}</summary>
              <p className="workshop-hint">{isBard2014 ? t.bardChoiceNote : isPaladin2014 ? t.paladinChoiceNote : isCleric2014 ? t.clericChoiceNote : isRanger2014 ? t.rangerChoiceNote : isWizard2014 ? t.wizardChoiceNote : isRogue2014 ? t.rogueChoiceNote : t.fighterChoiceNote}</p>
              <div className="workshop-level-grid">
                {dndLevels.map((step, index) =>
                  <div className={step.level === doc.character.level ? 'workshop-level-row current' : 'workshop-level-row'} key={step.level}>
                    <strong>{t.level} {step.level}</strong>
                    <span>+{step.proficiency} {t.fighterProficiency}{isRogue2014 && <> · {step.sneakAttackDice}d6 {t.rogueSneak}</>}{isWizard2014 && <> · {step.cantripsKnown} {t.wizardCantrips}</>}{isCleric2014 && <> · {step.cantripsKnown} {t.clericCantrips}</>}</span>
                    <span>{isBard2014 ? t.bardEvents[index] + ' · ' + t.bardSlots + ': ' + step.slots.join('/') + ' · ' + t.bardKnown + ': ' + step.spellsKnown + ' · ' + t.bardInspiration + ': d' + step.inspirationDie : isPaladin2014 ? t.paladinEvents[index] + ' · ' + t.paladinSlots + ': ' + step.slots.join('/') + ' · ' + t.paladinLayOnHands + ': ' + step.layOnHandsPool : isCleric2014 ? t.clericEvents[index] + ' · ' + t.clericSlots + ': ' + step.slots.join('/') : isRanger2014 ? t.rangerEvents[index] + ' · ' + t.rangerSlots + ': ' + step.slots.join('/') : isWizard2014 ? t.wizardEvents[index] + ' · ' + t.wizardSlots + ': ' + step.slots.join('/') : isRogue2014 ? t.rogueEvents[index] : t.fighterEvents[index]}</span>
                  </div>)}
              </div>
            </details>}
            {isWizard2014 && <details className="workshop-spell-catalog">
              <summary>{t.spellCatalogue} · {WIZARD_2014_SRD_SPELLS.reduce((n,list)=>n+list.length,0)}</summary>
              <p className="workshop-hint">{t.spellCatalogueNote}</p>
              <div className="workshop-catalog-grid">
                {WIZARD_2014_SRD_SPELLS.map((spells,level)=>
                  <details key={level} className="workshop-catalog-level">
                    <summary>{level===0?t.cantripTitle:t.spellLevel+' '+level} · {spells.length}</summary>
                    <ul>{spells.map(spell=>{
                      const hasRoll=doc.rolls.some(roll=>roll.name===spell||roll.name.startsWith(spell+' ·')||roll.name.startsWith(spell+' ('));
                      return <li key={spell}><span>{spell}</span><small>{hasRoll?t.spellWithRoll:t.spellReference}</small></li>;
                    })}</ul>
                  </details>)}
              </div>
              <p className="workshop-hint"><a href="https://5e.d20srd.org/srd/spellLists/wizardSpells.htm" target="_blank" rel="noreferrer">D&D 5e (2014) SRD · Wizard spell list</a></p>
              <p className="workshop-hint workshop-srd-attribution">This work includes material taken from the System Reference Document 5.1 (“SRD 5.1”) by Wizards of the Coast LLC and available at <a href="https://dnd.wizards.com/resources/systems-reference-document" target="_blank" rel="noreferrer">https://dnd.wizards.com/resources/systems-reference-document</a>. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at <a href="https://creativecommons.org/licenses/by/4.0/legalcode" target="_blank" rel="noreferrer">https://creativecommons.org/licenses/by/4.0/legalcode</a>.</p>
            </details>}
            <div className="workshop-actions">
              <button className="primary" onClick={save}>{t.save}</button>
              {canUpgradeLegacyStarterWeaponRolls(doc) && <button type="button"
                onClick={upgradeStarter}>{t.upgradeLegacy}</button>}
              <button onClick={createShare}>{t.share}</button>
              {editorAppLink && <a className="secondary" href={editorAppLink}>{t.openApp}</a>}
              <button onClick={() => downloadDocument(doc)}>{t.export}</button>
            </div>
            <p className="workshop-hint">{editorAppLink ? t.appDirectHint : t.appLarge}</p>
            {url && <div className="workshop-share">
              <p className="workshop-hint">{t.warning}</p>
              {url.includes('#template=') && <p className="workshop-hint">{t.templateLinkNote}</p>}
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
            <div className="workshop-row-head"><h2>{t.groups}</h2><button onClick={() => edit(x => { const group=newGroup(x.character.id); group.order=x.groups.length; x.groups.push(group); })}>{t.addGroup}</button></div>
            {doc.groups.map((g,i) => <div className="workshop-inline" key={g.id}>
              <Field label={t.group} value={g.name} onChange={v => edit(x => x.groups[i].name = v)} />
              <button onClick={() => edit(x => { x.rolls.forEach(r => { if (r.groupId === g.id) r.groupId = null; }); x.groups.splice(i, 1); })} aria-label={t.remove}>×</button>
            </div>)}
          </section>
          <section className="premium-panel">
            <div className="workshop-row-head"><h2>{t.rolls}</h2><button onClick={() => edit(x => x.rolls.push(newRoll(x.character.id)))}>{t.addRoll}</button></div>
            <div className="workshop-grouped-rolls">
              {orderedGroups(doc).map(({group,items})=>
                <details className="workshop-roll-group" key={group?.id || 'ungrouped'}>
                  <summary><strong>{group?.name || t.ungrouped}</strong>
                    <span>{items.length} {t.rollsInGroup}</span></summary>
                  <div className="workshop-group-content">
                    {group && <button type="button" className="workshop-add-in-group"
                      onClick={()=>edit(x=>{const roll=newRoll(x.character.id,group.id);roll.order=x.rolls.length;x.rolls.push(roll);})}>{t.addToGroup}</button>}
                    {items.map(({roll,index})=>renderRollEditor(roll,index))}
                  </div>
                </details>)}
            </div>
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
