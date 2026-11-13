import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { initialLocale, locales } from './i18n.js';
import { readStorage, writeStorage } from './safe-storage.js';
import Landing from './landing/Landing.jsx';

const CharacterWorkshop = lazy(() => import('./workshop/CharacterWorkshop.jsx'));

const repo = 'https://github.com/KeyserDSoze/Android.DiceThrower';
const rtl = new Set(['ar', 'he', 'fa', 'ur']);

function currentPage() {
  const path = window.location.pathname;
  if (path.includes('/workshop')) return 'workshop';
  if (path.includes('/privacy')) return 'privacy';
  if (path.includes('/terms')) return 'terms';
  if (path.includes('/contact')) return 'contact';
  return 'home';
}

function initialDark() {
  const saved = readStorage('dicethrower-site-theme');
  if (saved) return saved === 'dark';
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? true;
}

export default function App() {
  const [locale, setLocale] = useState(initialLocale);
  const [dark, setDark] = useState(initialDark);
  const copy = useMemo(() => locales[locale]?.copy || locales.en.copy, [locale]);
  const page = currentPage();
  const base = import.meta.env.BASE_URL;

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = dark ? 'dark' : 'light';
    root.style.colorScheme = dark ? 'dark' : 'light';
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0a0b14' : '#f6f1e7');
  }, [dark]);

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = rtl.has(locale) ? 'rtl' : 'ltr';
  }, [locale]);

  function changeLocale(value) {
    setLocale(value);
    writeStorage('dicethrower-site-language', value);
  }

  function toggleTheme() {
    const next = !dark;
    setDark(next);
    writeStorage('dicethrower-site-theme', next ? 'dark' : 'light');
  }

  const nav = [
    ['home', base, copy.navHome],
    ['workshop', base + 'workshop/', copy.navWorkshop],
    ['privacy', base + 'privacy/', copy.privacy],
    ['terms', base + 'terms/', copy.terms],
    ['contact', base + 'contact/', copy.contact],
  ];

  return (
    <div className="app">
      <header className="site-header">
        <a className="brand" href={base}>
          <img className="brand-icon" src={base + 'brand/dice-thrower-96.webp'} alt="" width="40" height="40" />
          <span className="brand-copy">
            <strong>Dice Thrower</strong>
            <span>{copy.brandTag}</span>
          </span>
        </a>
        <nav aria-label="Dice Thrower">
          {nav.map(([key, href, label]) => (
            <a key={key} href={href} aria-current={page === key ? 'page' : undefined}>{label}</a>
          ))}
          <a href={repo}>{copy.source}</a>
        </nav>
        <div className="controls">
          <select value={locale} onChange={(e) => changeLocale(e.target.value)} aria-label={copy.language}>
            {Object.entries(locales).map(([code, value]) => (
              <option value={code} key={code}>{value.nativeName}</option>
            ))}
          </select>
          <button type="button" className="icon-button" onClick={toggleTheme} aria-label={copy.themeToggle} aria-pressed={dark}>
            <span aria-hidden="true">{dark ? '☀' : '☾'}</span>
          </button>
        </div>
      </header>

      <main>
        {page === 'home' && <Landing copy={copy} base={base} repo={repo} />}
        {page === 'workshop' && (
          <Suspense fallback={<div className="page-loading" aria-busy="true" />}>
            <CharacterWorkshop locale={locale} />
          </Suspense>
        )}
        {page === 'privacy' && <Legal title={copy.privacyTitle} body={copy.privacyBody} />}
        {page === 'terms' && <Legal title={copy.termsTitle} body={copy.termsBody} />}
        {page === 'contact' && (
          <Legal title={copy.contactTitle} body={copy.contactBody}>
            <a className="primary" href={repo + '/issues'}>{copy.openIssues}</a>
          </Legal>
        )}
      </main>

      <footer className="site-footer">
        <div className="footer-brand">
          <img src={base + 'brand/dice-thrower-96.webp'} alt="" width="28" height="28" />
          <b>Dice Thrower</b>
        </div>
        <span>{copy.footerTag}</span>
      </footer>
    </div>
  );
}

function Legal({ title, body, children }) {
  return (
    <section className="legal premium-panel">
      <span className="eyebrow">Dice Thrower</span>
      <h1>{title}</h1>
      <p>{body}</p>
      {children}
    </section>
  );
}
