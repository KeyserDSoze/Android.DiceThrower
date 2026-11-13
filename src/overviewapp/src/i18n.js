import { readStorage } from './safe-storage.js';

const languageList = [
  ['en', 'English'], ['it', 'Italiano'], ['es', 'Español'], ['fr', 'Français'],
  ['de', 'Deutsch'], ['pt', 'Português'], ['ru', 'Русский'], ['ar', 'العربية'],
  ['hi', 'हिन्दी'], ['zh-CN', '简体中文'], ['ja', '日本語'], ['ko', '한국어'],
  ['id', 'Bahasa Indonesia'], ['tr', 'Türkçe'], ['vi', 'Tiếng Việt'], ['bn', 'বাংলা'],
  ['ur', 'اردو'], ['fa', 'فارسی'], ['pl', 'Polski'], ['nl', 'Nederlands'],
  ['th', 'ไทย'], ['ms', 'Bahasa Melayu'], ['sw', 'Kiswahili'], ['ta', 'தமிழ்'],
  ['te', 'తెలుగు'], ['mr', 'मराठी'], ['pa', 'ਪੰਜਾਬੀ'], ['gu', 'ગુજરાતી'],
  ['kn', 'ಕನ್ನಡ'], ['ml', 'മലയാളം'], ['my', 'မြန်မာ'], ['ne', 'नेपाली'],
  ['uk', 'Українська'], ['he', 'עברית'], ['el', 'Ελληνικά'], ['ro', 'Română'],
  ['cs', 'Čeština'], ['hu', 'Magyar'], ['sv', 'Svenska'], ['ha', 'Hausa'],
];

const en = {
  tagline: 'Your characters. Your rolls. Your dashboard.',
  intro: 'A game-agnostic Android dice roller built around configurable characters, grouped quick rolls and a physical shake gesture.',
  local: 'Offline by design',
  localBody: 'Standalone stays fully local and needs no account. Google connection is optional. No ads, analytics or tracking.',
  flexible: 'Any tabletop game',
  flexibleBody: 'Use custom formulas, double-roll Best/Worst with visible A/B candidates, and conditional Bonus/Malus actions with per-Roll cinematic magic, particles and glow controls. Dice Thrower never imposes game rules.',
  dashboard: 'Build your dashboard',
  dashboardBody: 'Order groups and rolls your way. Set a minimum character level on a roll so prepared abilities automatically appear and disappear as characters gain or lose levels.',
  shake: 'Shake or tap',
  shakeBody: 'Throw with the accelerometer or with a configurable on-screen button.',
  progression: 'Levels and variables',
  progressionBody: 'Define character modifiers such as Intelligence and reuse them with {Intelligence}. Add milestone or every-N-level scaling rules so rolls update automatically when the character levels up.',
  history: 'Local roll history',
  historyBody: 'Keep the last 20, 50, 100, or every roll, including original Part values and triggered bonus/malus actions.',
  status: 'Under active development',
  privacy: 'Privacy',
  terms: 'Terms',
  contact: 'Contact',
  source: 'Source code',
  privacyTitle: 'Privacy Policy',
  privacyBody: 'Dice Thrower does not require an account. Standalone keeps character data, images, settings and history locally. The website workshop saves drafts locally in browser IndexedDB; character share links/QR contain a readable-by-recipient copy in their URL fragment and should not include sensitive details. If you explicitly connect Google, Android Credential Manager handles sign-in and Drive authorization is limited to the private appData scope. Character data, portable images, sync/deletion metadata and selected roaming preferences then sync through Drive appDataFolder; Dice Thrower never stores your password or OAuth/ID tokens. Disconnect keeps both local and remote data; a separate confirmed Settings action deletes Dice Thrower cloud data while preserving the local copy. Revoked Drive access safely falls back to standalone. The app contains no ads, analytics or tracking. Accelerometer samples are processed only in memory.',
  termsTitle: 'Terms & Conditions',
  termsBody: 'Dice Thrower is a generic virtual dice utility for tabletop entertainment. It is not intended for regulated, gambling, cryptographic, financial, safety, medical or legal randomness. Local data can be lost if application data is cleared or the app is uninstalled.',
  contactTitle: 'Contact',
  contactBody: 'For support, feature requests or bug reports, use the public GitHub issue tracker. Do not post sensitive personal information in a public issue.',
  openIssues: 'Open GitHub issues',
  navHome: 'Dice Thrower',
  navWorkshop: 'Character workshop',
  brandTag: 'Real dice, on your phone',
  heroEyebrow: 'Android · Offline-first · Any tabletop game',
  heroTitle: 'Dice that feel real.',
  heroBody: 'Real 3D physics, crafted materials and your whole character sheet. Every throw in Dice Thrower tumbles, bounces and lands just like on your table.',
  ctaWorkshop: 'Open the character workshop',
  ctaSource: 'Source on GitHub',
  rollAgain: 'Roll again',
  rolling: 'Rolling…',
  youRolled: 'Result',
  heroCanvasLabel: 'Six polyhedral dice thrown on a felt tray with real physics',
  trustAccount: 'No account required',
  trustTracking: 'No ads, no tracking',
  trustOffline: 'Works offline',
  trustLanguages: '40 languages',
  physicsEyebrow: 'Fair by design',
  physicsTitle: 'The number is decided first. The physics only makes it beautiful.',
  physicsBody: 'An unbiased random engine picks every result before anything moves. Then a rigid-body simulation throws the dice — gravity, bounces, friction and rolling — and lands each one exactly on that value. The animation can never change a number.',
  physicsPoint1: 'Rounded edges, engraved numerals, 6 and 9 underlined',
  physicsPoint2: 'Glossy resin, matte, brushed metal and gemstone',
  physicsPoint3: 'Opposite faces add up like real dice: 1·6, 2·5, 3·4',
  showcaseEyebrow: 'Your table, your style',
  showcaseTitle: 'A premium table in your pocket.',
  showcaseTable: 'Throw with a tap, a swipe or a shake — results stay readable above the dice.',
  showcaseRolls: 'Group the rolls you actually use. Each one shows the die it throws.',
  showcaseStyles: 'Ten hand-tuned dice styles, or design your own and share them.',
  featuresEyebrow: 'Built for your table',
  featuresTitle: 'Fast when you play. Flexible when you configure.',
  formulaEyebrow: 'Game agnostic',
  formulaTitle: 'Your rules stay yours.',
  formulaBody: 'Dice Thrower stores expressions, variables and level rules — not a specific RPG system.',
  workshopTitle: 'Build & share your characters',
  workshopBody: 'A web editor with browser storage, share links and QR codes for Android import. No account, ever.',
  workshopCta: 'Open workshop',
  footerTag: 'MIT · local-first · game-agnostic',
  themeToggle: 'Switch light/dark theme',
  language: 'Language',
};

const it = {
  ...en,
  tagline: 'I tuoi personaggi. I tuoi tiri. La tua dashboard.',
  intro: 'Un lanciatore di dadi Android indipendente dal gioco, costruito attorno a personaggi configurabili, tiri rapidi raggruppati e al gesto fisico di scuotere il telefono.',
  local: 'Offline per scelta',
  localBody: 'La modalità standalone resta completamente locale e non richiede account. Il collegamento Google è opzionale. Nessuna pubblicità, analytics o tracking.',
  flexible: 'Qualsiasi gioco da tavolo',
  flexibleBody: 'Usa formule personalizzate, doppio tiro Best/Worst con gruppi A/B riconoscibili ed effetti Bonus/Malus con rune, particelle, glow e impostazioni cinematiche per ogni tiro. Dice Thrower non impone regole di gioco.',
  dashboard: 'Costruisci la tua dashboard',
  dashboardBody: 'Ordina gruppi e tiri. Imposta un livello minimo su ogni tiro: le abilità preparate compaiono e scompaiono automaticamente quando il personaggio sale o scende di livello.',
  shake: 'Scuoti o tocca',
  shakeBody: 'Lancia con l’accelerometro oppure con un pulsante a schermo configurabile.',
  progression: 'Livelli e variabili',
  progressionBody: 'Definisci modificatori come Intelligenza e riusali con {Intelligenza}. Aggiungi regole a soglia o ogni N livelli: i tiri si aggiornano automaticamente quando il personaggio sale di livello.',
  history: 'Storico locale dei tiri',
  historyBody: 'Conserva gli ultimi 20, 50, 100 oppure tutti i tiri, con valori originali delle Parts e azioni bonus/malus applicate.',
  status: 'In sviluppo attivo',
  privacy: 'Privacy',
  terms: 'Termini',
  contact: 'Contatti',
  source: 'Codice sorgente',
  privacyTitle: 'Informativa sulla privacy',
  privacyBody: 'Dice Thrower non richiede un account. In standalone, dati dei personaggi, immagini, impostazioni e storico restano locali. Il laboratorio web conserva le bozze in IndexedDB nel browser; link e QR condivisi contengono una copia leggibile da chi li riceve e non devono includere informazioni sensibili. Se colleghi esplicitamente Google, l’accesso usa Android Credential Manager e l’autorizzazione Drive è limitata allo scope privato appData. Dati dei personaggi, immagini portabili, metadati di sync/cancellazione e alcune preferenze roaming vengono quindi sincronizzati tramite Drive appDataFolder; Dice Thrower non salva mai password o token OAuth/ID. Scollegare conserva dati locali e remoti; un’azione separata e confermata nelle Impostazioni elimina i dati cloud Dice Thrower conservando la copia locale. La revoca dell’accesso Drive riporta in sicurezza alla modalità standalone. Nessuna pubblicità, analytics o tracking. I campioni dell’accelerometro sono elaborati solo in memoria.',
  termsTitle: 'Termini e condizioni',
  termsBody: 'Dice Thrower è un’utilità generica per dadi virtuali destinata al gioco da tavolo. Non è destinata a casualità regolamentata, gioco d’azzardo, crittografia, finanza, sicurezza, medicina o ambiti legali. I dati locali possono andare persi se i dati dell’app vengono cancellati o l’app viene disinstallata.',
  contactTitle: 'Contatti',
  contactBody: 'Per supporto, richieste di funzionalità o segnalazioni di bug, usa l’issue tracker pubblico su GitHub. Non pubblicare informazioni personali sensibili in una issue pubblica.',
  openIssues: 'Apri le issue GitHub',
  navWorkshop: 'Laboratorio personaggi',
  brandTag: 'Dadi veri, sul telefono',
  heroEyebrow: 'Android · Offline · Qualsiasi gioco da tavolo',
  heroTitle: 'Dadi che sembrano veri.',
  heroBody: 'Fisica 3D reale, materiali curati e l’intera scheda del personaggio. Ogni tiro di Dice Thrower rotola, rimbalza e atterra come sul tuo tavolo.',
  ctaWorkshop: 'Apri il laboratorio personaggi',
  ctaSource: 'Codice su GitHub',
  rollAgain: 'Tira di nuovo',
  rolling: 'Lancio in corso…',
  youRolled: 'Risultato',
  heroCanvasLabel: 'Sei dadi poliedrici lanciati su un vassoio di panno con fisica reale',
  trustAccount: 'Nessun account',
  trustTracking: 'Niente pubblicità né tracciamento',
  trustOffline: 'Funziona offline',
  trustLanguages: '40 lingue',
  physicsEyebrow: 'Equo per progetto',
  physicsTitle: 'Il numero viene deciso prima. La fisica lo rende solo bellissimo.',
  physicsBody: 'Un generatore casuale imparziale sceglie ogni risultato prima che qualcosa si muova. Poi una simulazione a corpi rigidi lancia i dadi — gravità, rimbalzi, attrito e rotolamenti — e li fa atterrare esattamente su quel valore. L’animazione non può mai cambiare un numero.',
  physicsPoint1: 'Spigoli arrotondati, numeri incisi, 6 e 9 sottolineati',
  physicsPoint2: 'Resina lucida, opaca, metallo spazzolato e gemma',
  physicsPoint3: 'Le facce opposte sommano come nei dadi veri: 1·6, 2·5, 3·4',
  showcaseEyebrow: 'Il tuo tavolo, il tuo stile',
  showcaseTitle: 'Un tavolo premium in tasca.',
  showcaseTable: 'Lancia con un tocco, uno swipe o scuotendo il telefono: i risultati restano leggibili sopra i dadi.',
  showcaseRolls: 'Raggruppa i tiri che usi davvero. Ognuno mostra il dado che lancia.',
  showcaseStyles: 'Dieci stili di dadi curati a mano, oppure crea i tuoi e condividili.',
  featuresEyebrow: 'Fatto per il tuo tavolo',
  featuresTitle: 'Veloce quando giochi. Flessibile quando configuri.',
  formulaEyebrow: 'Indipendente dal gioco',
  formulaTitle: 'Le tue regole restano tue.',
  formulaBody: 'Dice Thrower memorizza espressioni, variabili e regole di livello, non un sistema di gioco specifico.',
  workshopTitle: 'Crea e condividi personaggi',
  workshopBody: 'Un editor web con salvataggio nel browser, link e QR per importare in Android. Mai un account.',
  workshopCta: 'Apri il laboratorio',
  footerTag: 'MIT · locale · indipendente dal gioco',
  themeToggle: 'Cambia tema chiaro/scuro',
  language: 'Lingua',
};

export const locales = Object.fromEntries(
  languageList.map(([code, nativeName]) => [code, { nativeName, copy: code === 'it' ? it : en }]),
);

const aliases = { in: 'id', iw: 'he', zh: 'zh-CN' };

export function normalizeLocale(tag) {
  if (!tag) return 'en';
  if (locales[tag]) return tag;
  const normalized = tag.replace('_', '-');
  if (locales[normalized]) return normalized;
  const language = normalized.split('-')[0].toLowerCase();
  return aliases[language] || (locales[language] ? language : 'en');
}

export function initialLocale() {
  const saved = readStorage('dicethrower-site-language');
  if (saved && locales[saved]) return saved;
  for (const language of navigator.languages || [navigator.language]) {
    const normalized = normalizeLocale(language);
    if (normalized) return normalized;
  }
  return 'en';
}
