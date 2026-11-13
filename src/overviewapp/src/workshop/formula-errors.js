/** Stable, presentation-only diagnostics. Never persist an Error or the full input formula. */
const messages = {
  en: {
    prefix: 'Invalid dice expression: ',
    fallback: 'check the formula and its variables',
    length: p => `use at most ${p('limit')} characters`,
    required: () => 'a number or die is required',
    character: p => `unexpected character near position ${p('position')}`,
    integer: () => 'integer range exceeded',
    'dice-product': () => 'dice-by-dice multiplication is not supported',
    nesting: p => `nesting exceeds ${p('limit')} levels`,
    terms: p => `expression exceeds ${p('limit')} terms`,
    'dice-count': p => `dice count must be between 1 and ${p('limit')}`,
    sides: p => `unsupported die d${p('sides')}`,
    'total-dice': p => `expression exceeds ${p('limit')} dice`,
    parenthesis: () => 'missing closing parenthesis',
    syntax: () => 'expected a number or die',
    'separated-numbers': () => 'separate numbers with an operator',
    level: () => 'invalid character level',
    'modifier-name': () => 'invalid modifier name',
    'duplicate-variable': () => 'duplicate or reserved modifier name',
    'modifier-value': () => 'invalid modifier value',
    milestones: () => 'invalid level milestones',
    variable: p => `missing variable or invalid level milestone: ${p('name')}`,
    'initial-part': () => 'at least one initial Part is required',
    'rule-trigger': () => 'invalid level rule trigger',
    'rule-kind': () => 'invalid level rule kind',
    'rule-dice': p => `level rules add more than ${p('limit')} dice`,
    'rule-size': () => 'level rules exceed the expression size budget',
  },
  it: {
    prefix: 'Formula dei dadi non valida: ',
    fallback: 'controlla la formula e le sue variabili',
    length: p => `usa al massimo ${p('limit')} caratteri`,
    required: () => 'inserisci un numero o un dado',
    character: p => `carattere inatteso vicino alla posizione ${p('position')}`,
    integer: () => 'un valore o risultato intermedio supera l’intervallo degli interi supportati',
    'dice-product': () => 'non è consentito moltiplicare due espressioni che contengono dadi',
    nesting: p => `le parentesi o i segni unari superano ${p('limit')} livelli di annidamento`,
    terms: p => `l’espressione supera ${p('limit')} termini`,
    'dice-count': p => `il numero di dadi per termine deve essere compreso tra 1 e ${p('limit')}`,
    sides: p => `il dado d${p('sides')} non è supportato`,
    'total-dice': p => `l’espressione supera ${p('limit')} dadi`,
    parenthesis: () => 'manca una parentesi di chiusura',
    syntax: () => 'è richiesto un numero o un dado',
    'separated-numbers': () => 'separa i numeri con un operatore',
    level: () => 'il livello del personaggio non è valido',
    'modifier-name': () => 'il nome del modificatore non è valido',
    'duplicate-variable': () => 'il nome del modificatore è duplicato o riservato',
    'modifier-value': () => 'il valore del modificatore non è valido',
    milestones: () => 'le soglie di livello non sono valide',
    variable: p => `variabile non definita o soglia di livello non valida: ${p('name')}`,
    'initial-part': () => 'includi almeno una Part nel tiro iniziale',
    'rule-trigger': () => 'il livello di attivazione della regola non è valido',
    'rule-kind': () => 'il tipo di regola di livello non è valido',
    'rule-dice': p => `le regole di livello aggiungono più di ${p('limit')} dadi`,
    'rule-size': () => 'le regole di livello superano i limiti di dimensione dell’espressione',
  },
};

export const DICE_FORMULA_ERROR_REASONS = Object.freeze(Object.keys(messages.en)
  .filter(key => key !== 'prefix' && key !== 'fallback'));

function safeDetails(parameters) {
  const result = {};
  for (const key of ['limit', 'position', 'sides']) {
    if (Number.isSafeInteger(parameters?.[key]) && parameters[key] >= 0) result[key] = parameters[key];
  }
  if (typeof parameters?.name === 'string') result.name = parameters.name.slice(0, 160);
  return Object.freeze(result);
}
function message(reason, details, locale) {
  const language = typeof locale === 'string' && /^it(?:-|$)/i.test(locale) ? messages.it : messages.en;
  const render = Object.hasOwn(language, reason) && typeof language[reason] === 'function' ? language[reason] : null;
  const parameter = key => details[key] ?? '?';
  return language.prefix + (render ? render(parameter) : language.fallback);
}

export class DiceFormulaError extends Error {
  constructor(reason, parameters = {}) {
    const details = safeDetails(parameters);
    super(message(reason, details, 'en'));
    this.name = 'DiceFormulaError';
    this.code = 'dice-formula-invalid';
    this.reason = reason;
    this.details = details;
  }
}

/** Null lets the caller keep existing handling for storage, links and unrelated validation. */
export function formatDiceFormulaError(error, locale = 'en') {
  if (error?.code !== 'dice-formula-invalid') return null;
  return message(error.reason, safeDetails(error.details), locale);
}
