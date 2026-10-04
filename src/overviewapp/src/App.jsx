import { useMemo, useState } from 'react';
import { initialLocale, locales } from './i18n.js';

const repo = 'https://github.com/KeyserDSoze/Android.DiceThrower';
const brandArt = 'https://raw.githubusercontent.com/KeyserDSoze/Android.DiceThrower/main/src/app/src/main/res/drawable-nodpi/ic_launcher_art.png';

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
  const base = import.meta.env.BASE_URL;

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
      <a href={base}>Dice Thrower</a>
      <a href={base + 'privacy/'}>{copy.privacy}</a>
      <a href={base + 'terms/'}>{copy.terms}</a>
      <a href={base + 'contact/'}>{copy.contact}</a>
      <a href={repo}>{copy.source}</a>
    </nav>
  );

  return (
    <div className={dark ? 'app dark' : 'app light'}>
      <div className="ambient ambient-a" />
      <div className="ambient ambient-b" />

      <header>
        <a className="brand" href={base}>
          <img className="brand-icon" src={brandArt} alt="" />
          <div className="brand-copy">
            <strong>Dice Thrower</strong>
            <span>local-first dice dashboard</span>
          </div>
        </a>
        {links}
        <div className="controls">
          <select value={locale} onChange={(e) => changeLocale(e.target.value)} aria-label="Language">
            {Object.entries(locales).map(([code, value]) => (
              <option value={code} key={code}>{value.nativeName}</option>
            ))}
          </select>
          <button className="icon-button" onClick={toggleTheme} aria-label="Toggle theme">
            {dark ? '☀' : '☾'}
          </button>
        </div>
      </header>

      <main>
        {page === 'home' && (
          <>
            <section className="hero-shell">
              <div className="hero-copy">
                <div className="eyebrow"><span className="pulse" />{copy.status}</div>
                <h1>Dice<br />Thrower</h1>
                <p className="tagline">{copy.tagline}</p>
                <p className="intro">{copy.intro}</p>
                <div className="hero-actions">
                  <a className="primary" href={repo}>GitHub</a>
                  <a className="secondary" href={base + 'privacy/'}>{copy.privacy}</a>
                </div>
                <div className="trust-row">
                  <span>◆ Offline</span>
                  <span>◆ Account optional</span>
                  <span>◆ No tracking</span>
                </div>
              </div>

              <div className="hero-visual" aria-hidden="true">
                <div className="orbit orbit-one" />
                <div className="orbit orbit-two" />
                <img className="hero-art" src={brandArt} alt="" />
                <div className="floating-card card-character">
                  <span className="mini-avatar">A</span>
                  <div><b>Alyndra</b><small>Level 8 · Arcane</small></div>
                  <span className="level-pill">Lv 8</span>
                </div>
                <div className="floating-card card-roll">
                  <span className="die-chip">d20</span>
                  <div><b>Fireball</b><small>8d6 + {'{Intelligence}'}</small></div>
                  <span className="roll-pill">ROLL</span>
                </div>
                <div className="floating-card card-level">
                  <span className="spark">✦</span>
                  <div><b>Level up</b><small>all formulas update</small></div>
                </div>
              </div>
            </section>

            <section className="metric-strip">
              <div><strong>8</strong><span>dice types</span></div>
              <div><strong>∞</strong><span>custom rolls</span></div>
              <div><strong>40</strong><span>languages planned</span></div>
              <div><strong>0</strong><span>accounts required</span></div>
            </section>

            <section className="section-heading">
              <span className="eyebrow">Built for your table</span>
              <h2>Fast when you play.<br />Flexible when you configure.</h2>
            </section>

            <section className="grid">
              {[
                ['◎', copy.local, copy.localBody],
                ['◇', copy.flexible, copy.flexibleBody],
                ['▦', copy.dashboard, copy.dashboardBody],
                ['↯', copy.shake, copy.shakeBody],
                ['↟', copy.progression, copy.progressionBody],
                ['◷', copy.history, copy.historyBody],
              ].map(([icon, title, body]) => (
                <article key={title}>
                  <div className="feature-icon">{icon}</div>
                  <h3>{title}</h3>
                  <p>{body}</p>
                </article>
              ))}
            </section>

            <section className="formula-showcase">
              <div>
                <span className="eyebrow">Game agnostic</span>
                <h2>Your rules stay yours.</h2>
                <p>Dice Thrower stores expressions, variables and level rules — not a specific RPG system.</p>
              </div>
              <div className="formula-stack">
                <code>1d20+{'{Strength}'}</code>
                <code>4d3+3d6+10</code>
                <code>2d6+{'{level}'}+{'{Spell Power}'}</code>
              </div>
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

      <footer>
        <div className="footer-brand"><img src={brandArt} alt="" /> <b>Dice Thrower</b></div>
        <span>MIT · local-first · game-agnostic</span>
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
