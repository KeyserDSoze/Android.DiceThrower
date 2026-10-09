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
  flexibleBody: 'Use custom formulas, Best/Worst double rolls, and conditional Bonus/Malus effects with AND/OR rules and rerolls. Dice Thrower never imposes a game system.',
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
  privacyBody: 'Dice Thrower does not require an account. Standalone keeps character data, images, settings and history locally. If you explicitly connect Google, Android Credential Manager handles sign-in and Drive authorization is limited to the private appData scope. Character data, portable images, sync/deletion metadata and selected roaming preferences then sync through Drive appDataFolder; Dice Thrower never stores your password or OAuth/ID tokens. Disconnect keeps both local and remote data; a separate confirmed Settings action deletes Dice Thrower cloud data while preserving the local copy. Revoked Drive access safely falls back to standalone. The app contains no ads, analytics or tracking. Accelerometer samples are processed only in memory.',
  termsTitle: 'Terms & Conditions',
  termsBody: 'Dice Thrower is a generic virtual dice utility for tabletop entertainment. It is not intended for regulated, gambling, cryptographic, financial, safety, medical or legal randomness. Local data can be lost if application data is cleared or the app is uninstalled.',
  contactTitle: 'Contact',
  contactBody: 'For support, feature requests or bug reports, use the public GitHub issue tracker. Do not post sensitive personal information in a public issue.',
  openIssues: 'Open GitHub issues',
};

const it = {
  ...en,
  tagline: 'I tuoi personaggi. I tuoi tiri. La tua dashboard.',
  intro: 'Un lanciatore di dadi Android indipendente dal gioco, costruito attorno a personaggi configurabili, tiri rapidi raggruppati e al gesto fisico di scuotere il telefono.',
  local: 'Offline per scelta',
  localBody: 'La modalità standalone resta completamente locale e non richiede account. Il collegamento Google è opzionale. Nessuna pubblicità, analytics o tracking.',
  flexible: 'Qualsiasi gioco da tavolo',
  flexibleBody: 'Usa formule personalizzate, tiri doppi Best/Worst ed effetti Bonus/Malus condizionati con regole AND/OR e rilanci. Dice Thrower non impone regole di gioco.',
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
  privacyBody: 'Dice Thrower non richiede un account. In standalone, dati dei personaggi, immagini, impostazioni e storico restano locali. Se colleghi esplicitamente Google, l’accesso usa Android Credential Manager e l’autorizzazione Drive è limitata allo scope privato appData. Dati dei personaggi, immagini portabili, metadati di sync/cancellazione e alcune preferenze roaming vengono quindi sincronizzati tramite Drive appDataFolder; Dice Thrower non salva mai password o token OAuth/ID. Scollegare conserva dati locali e remoti; un’azione separata e confermata nelle Impostazioni elimina i dati cloud Dice Thrower conservando la copia locale. La revoca dell’accesso Drive riporta in sicurezza alla modalità standalone. Nessuna pubblicità, analytics o tracking. I campioni dell’accelerometro sono elaborati solo in memoria.',
  termsTitle: 'Termini e condizioni',
  termsBody: 'Dice Thrower è un’utilità generica per dadi virtuali destinata al gioco da tavolo. Non è destinata a casualità regolamentata, gioco d’azzardo, crittografia, finanza, sicurezza, medicina o ambiti legali. I dati locali possono andare persi se i dati dell’app vengono cancellati o l’app viene disinstallata.',
  contactTitle: 'Contatti',
  contactBody: 'Per supporto, richieste di funzionalità o segnalazioni di bug, usa l’issue tracker pubblico su GitHub. Non pubblicare informazioni personali sensibili in una issue pubblica.',
  openIssues: 'Apri le issue GitHub',
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
  const saved = localStorage.getItem('dicethrower-site-language');
  if (saved && locales[saved]) return saved;
  for (const language of navigator.languages || [navigator.language]) {
    const normalized = normalizeLocale(language);
    if (normalized) return normalized;
  }
  return 'en';
}
