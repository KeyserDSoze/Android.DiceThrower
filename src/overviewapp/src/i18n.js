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
  localBody: 'Characters, roll definitions, settings and history stay on your device. No account, analytics or tracking.',
  flexible: 'Any tabletop game',
  flexibleBody: 'Use a free-form tag and expressions such as 6d6+6 or 4d3+3d6+10. Dice Thrower does not impose game rules.',
  dashboard: 'Build your dashboard',
  dashboardBody: 'Order groups and rolls the way you play. Expand a group or launch an ungrouped roll directly.',
  shake: 'Shake or tap',
  shakeBody: 'Throw with the accelerometer or with a configurable on-screen button.',
  history: 'Local roll history',
  historyBody: 'Keep the last 20, 50, 100, or every roll.',
  status: 'Under active development',
  privacy: 'Privacy',
  terms: 'Terms',
  contact: 'Contact',
  source: 'Source code',
  privacyTitle: 'Privacy Policy',
  privacyBody: 'Dice Thrower does not require an account, does not request Internet access, and does not collect analytics or tracking data. Character data, selected image references, settings and roll history are stored locally on the device. Accelerometer samples are processed only in memory to detect a shake and are not recorded or transmitted.',
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
  localBody: 'Personaggi, definizioni dei tiri, impostazioni e storico restano sul dispositivo. Nessun account, analytics o tracking.',
  flexible: 'Qualsiasi gioco da tavolo',
  flexibleBody: 'Usa un tag libero ed espressioni come 6d6+6 o 4d3+3d6+10. Dice Thrower non impone regole di gioco.',
  dashboard: 'Costruisci la tua dashboard',
  dashboardBody: 'Ordina gruppi e tiri nel modo in cui giochi. Espandi un gruppo o lancia direttamente un tiro senza gruppo.',
  shake: 'Scuoti o tocca',
  shakeBody: 'Lancia con l’accelerometro oppure con un pulsante a schermo configurabile.',
  history: 'Storico locale dei tiri',
  historyBody: 'Conserva gli ultimi 20, 50, 100 oppure tutti i tiri.',
  status: 'In sviluppo attivo',
  privacy: 'Privacy',
  terms: 'Termini',
  contact: 'Contatti',
  source: 'Codice sorgente',
  privacyTitle: 'Informativa sulla privacy',
  privacyBody: 'Dice Thrower non richiede un account, non richiede accesso a Internet e non raccoglie analytics o dati di tracking. I dati dei personaggi, i riferimenti alle immagini selezionate, le impostazioni e lo storico dei tiri restano sul dispositivo. I campioni dell’accelerometro sono elaborati solo in memoria per rilevare lo scuotimento e non vengono registrati o trasmessi.',
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
