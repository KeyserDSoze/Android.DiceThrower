import { useMemo, useState } from 'react';
import { initialLocale, locales } from './i18n.js';

const repo = 'https://github.com/KeyserDSoze/Android.DiceThrower';

function currentPage() {
  const path = window.location.pathname;
  if (path.includes('/privacy')) return 'privacy';
  if (path.includes('/terms')) return 'terms';
  if (path.includes('/contact')) return 'contact';
  return 'home';
}

export default function App() {
  const [locale, setLocale] = useState(initialLocale);
  const [dark, setDark] = useState(() => {
    const saved = localStorage.getItem('dicethrower-site-theme');
    if (saved) return saved === 'dark';
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? true;
  });

  const copy = useMemo(() => locales[locale]?.copy || locales.en.copy, [locale]);
  const page = currentPage();

  function changeLocale(value) {
    setLocale(value);
    localStorage.setItem('dicethrower-site-language', value);
  }

  function toggleTheme() {
    const next = !dark;
    setDark(next);
    localStorage.setItem('dicethrower-site-theme', next ? 'dark' : 'light');
  }

  const links = (
    <nav>
      <a href="./">{'Dice Thrower'}</a>
      <a href="./privacy/">{copy.privacy}</a>
      <a href="./terms/">{copy.terms}</a>
      <a href="./contact/">{copy.contact}</a>
      <a href={repo}>{copy.source}</a>
    </nav>
  );

  return (
    <div className={dark ? 'app dark' : 'app light'}>
      <header>
        <div className="brand">
          <div className="die">20</div>
          <strong>Dice Thrower</strong>
        </div>
        {links}
        <div className="controls">
          <select value={locale} onChange={(e) => changeLocale(e.target.value)} aria-label="Language">
            {Object.entries(locales).map(([code, value]) => (
              <option value={code} key={code}>{value.nativeName}</option>
            ))}
          </select>
          <button onClick={toggleTheme} aria-label="Toggle theme">{dark ? '☀' : '☾'}</button>
        </div>
      </header>

      <main>
        {page === 'home' && (
          <>
            <section className="hero">
              <div className="eyebrow">{copy.status}</div>
              <h1>Dice Thrower</h1>
              <p className="tagline">{copy.tagline}</p>
              <p className="intro">{copy.intro}</p>
              <a className="primary" href={repo}>GitHub</a>
            </section>
            <section className="grid">
              {[
                [copy.local, copy.localBody],
                [copy.flexible, copy.flexibleBody],
                [copy.dashboard, copy.dashboardBody],
                [copy.shake, copy.shakeBody],
                [copy.history, copy.historyBody],
              ].map(([title, body]) => (
                <article key={title}><h2>{title}</h2><p>{body}</p></article>
              ))}
            </section>
          </>
        )}

        {page === 'privacy' && <Legal title={copy.privacyTitle} body={copy.privacyBody} />}
        {page === 'terms' && <Legal title={copy.termsTitle} body={copy.termsBody} />}
        {page === 'contact' && (
          <Legal title={copy.contactTitle} body={copy.contactBody}>
            <a className="primary" href={repo + '/issues'}>{copy.openIssues}</a>
          </Legal>
        )}
      </main>

      <footer>Dice Thrower · MIT · local-first</footer>
    </div>
  );
}

function Legal({ title, body, children }) {
  return (
    <section className="legal">
      <h1>{title}</h1>
      <p>{body}</p>
      {children}
    </section>
  );
}
