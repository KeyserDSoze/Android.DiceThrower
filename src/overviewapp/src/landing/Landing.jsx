import { lazy, Suspense, useEffect, useState } from 'react';

const DiceHero = lazy(() => import('./DiceHero.jsx'));

/** Small line-art dice used as feature icons (same silhouettes as the Android app). */
export function DieIcon({ sides = 20, size = 28 }) {
  const stroke = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.6, strokeLinejoin: 'round', strokeLinecap: 'round' };
  const shapes = {
    4: <><path d="M12 2.5 21.5 19H2.5Z" {...stroke} /><path d="M12 13.5V2.5M12 13.5 2.5 19M12 13.5 21.5 19" {...stroke} opacity=".6" /></>,
    6: <><rect x="3.5" y="3.5" width="17" height="17" rx="4" {...stroke} /><circle cx="8.5" cy="8.5" r="1.4" fill="currentColor" /><circle cx="12" cy="12" r="1.4" fill="currentColor" /><circle cx="15.5" cy="15.5" r="1.4" fill="currentColor" /></>,
    8: <><path d="M12 2 20.5 12 12 22 3.5 12Z" {...stroke} /><path d="M3.5 12h17M12 2v10" {...stroke} opacity=".6" /></>,
    10: <><path d="M12 2 21.5 11 12 21.5 2.5 11Z" {...stroke} /><path d="M12 2 7.6 12.4 12 15.2 16.4 12.4Z M2.5 11l5.1 1.4M21.5 11l-5.1 1.4M12 15.2v6.3" {...stroke} opacity=".6" /></>,
    12: <><path d="M12 2.2 21.3 9 17.8 20H6.2L2.7 9Z" {...stroke} /><path d="M12 7l4.3 3.1-1.6 5h-5.4l-1.6-5Z" {...stroke} opacity=".6" /></>,
    20: <><path d="M12 2 20.7 7v10L12 22l-8.7-5V7Z" {...stroke} /><path d="M12 7.2 16.2 14.6H7.8ZM12 2v5.2M20.7 17l-4.5-2.4M3.3 17l4.5-2.4M3.3 7l4.5 7.6M20.7 7l-4.5 7.6" {...stroke} opacity=".6" /></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">{shapes[sides] || shapes[20]}</svg>;
}

function TrustIcon({ kind }) {
  const common = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round' };
  const paths = {
    account: <><circle cx="12" cy="8" r="3.5" {...common} /><path d="M5 20c1.2-3.6 4-5.2 7-5.2s5.8 1.6 7 5.2" {...common} /><path d="M4 4l16 16" {...common} /></>,
    tracking: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" {...common} /><circle cx="12" cy="12" r="2.6" {...common} /><path d="M4 4l16 16" {...common} /></>,
    offline: <><path d="M4 9.5a12 12 0 0 1 16 0M7 13a7.5 7.5 0 0 1 10 0M10 16.5a3 3 0 0 1 4 0" {...common} /><circle cx="12" cy="19.5" r=".9" fill="currentColor" /></>,
    languages: <><circle cx="12" cy="12" r="9" {...common} /><path d="M3 12h18M12 3c2.6 2.7 2.6 15.3 0 18M12 3c-2.6 2.7-2.6 15.3 0 18" {...common} /></>,
  };
  return <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">{paths[kind]}</svg>;
}

function HeroTray({ copy }) {
  const [rollKey, setRollKey] = useState(0);
  const [results, setResults] = useState(null);
  const [supported] = useState(() => {
    try {
      const canvas = document.createElement('canvas');
      return !!(canvas.getContext('webgl2') || canvas.getContext('webgl'));
    } catch { return false; }
  });
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    // Defer the WebGL bundle until the page has painted.
    const id = (window.requestIdleCallback || ((cb) => setTimeout(cb, 250)))(() => setVisible(true));
    return () => (window.cancelIdleCallback || clearTimeout)(id);
  }, []);

  return (
    <div className="tray">
      <div className="tray-stage">
        {supported && visible ? (
          <Suspense fallback={<div className="tray-poster" />}>
            <DiceHero rollKey={rollKey} label={copy.heroCanvasLabel} onResults={setResults} />
          </Suspense>
        ) : <div className="tray-poster" />}
      </div>
      <div className="tray-bar">
        <div className="tray-results" aria-live="polite">
          <span className="tray-results-label">{results ? copy.youRolled : copy.rolling}</span>
          {results && results.map(({ sides, value }, i) => (
            <span className="result-chip" key={i}><DieIcon sides={sides === 100 ? 10 : sides} size={16} /><b>{value}</b></span>
          ))}
        </div>
        {supported && (
          <button type="button" className="throw-button" onClick={() => { setResults(null); setRollKey((k) => k + 1); }}>
            <DieIcon sides={20} size={20} />{copy.rollAgain}
          </button>
        )}
      </div>
    </div>
  );
}

export default function Landing({ copy, base, repo }) {
  const features = [
    [6, copy.local, copy.localBody],
    [20, copy.flexible, copy.flexibleBody],
    [12, copy.dashboard, copy.dashboardBody],
    [8, copy.shake, copy.shakeBody],
    [10, copy.progression, copy.progressionBody],
    [4, copy.history, copy.historyBody],
  ];
  const screens = [
    ['app-table.webp', copy.showcaseTable],
    ['app-group.webp', copy.showcaseRolls],
    ['app-styles.webp', copy.showcaseStyles],
  ];
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">{copy.heroEyebrow}</span>
          <h1>{copy.heroTitle}</h1>
          <p className="hero-body">{copy.heroBody}</p>
          <div className="hero-actions">
            <a className="primary" href={base + 'workshop/'}>{copy.ctaWorkshop}</a>
            <a className="secondary" href={repo}>{copy.ctaSource}</a>
          </div>
          <ul className="trust-row">
            <li><TrustIcon kind="account" />{copy.trustAccount}</li>
            <li><TrustIcon kind="tracking" />{copy.trustTracking}</li>
            <li><TrustIcon kind="offline" />{copy.trustOffline}</li>
            <li><TrustIcon kind="languages" />{copy.trustLanguages}</li>
          </ul>
        </div>
        <HeroTray copy={copy} />
      </section>

      <section className="physics premium-panel">
        <div>
          <span className="eyebrow">{copy.physicsEyebrow}</span>
          <h2>{copy.physicsTitle}</h2>
          <p>{copy.physicsBody}</p>
        </div>
        <ul className="physics-points">
          <li><DieIcon sides={20} />{copy.physicsPoint1}</li>
          <li><DieIcon sides={12} />{copy.physicsPoint2}</li>
          <li><DieIcon sides={6} />{copy.physicsPoint3}</li>
        </ul>
      </section>

      <section className="showcase">
        <header className="section-heading">
          <span className="eyebrow">{copy.showcaseEyebrow}</span>
          <h2>{copy.showcaseTitle}</h2>
        </header>
        <div className="phones">
          {screens.map(([file, caption], i) => (
            <figure className={`phone phone-${i}`} key={file}>
              <div className="phone-frame">
                <img src={`${base}screens/${file}`} alt={caption} loading="lazy" width="540" height="1200" />
              </div>
              <figcaption>{caption}</figcaption>
            </figure>
          ))}
        </div>
      </section>

      <section className="features">
        <header className="section-heading">
          <span className="eyebrow">{copy.featuresEyebrow}</span>
          <h2>{copy.featuresTitle}</h2>
        </header>
        <div className="feature-grid">
          {features.map(([sides, title, body]) => (
            <article className="feature" key={title}>
              <div className="feature-icon"><DieIcon sides={sides} /></div>
              <h3>{title}</h3>
              <p>{body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="formula premium-panel">
        <div>
          <span className="eyebrow">{copy.formulaEyebrow}</span>
          <h2>{copy.formulaTitle}</h2>
          <p>{copy.formulaBody}</p>
        </div>
        <div className="formula-stack">
          <code>1d20+{'{Strength}'}</code>
          <code>4d3+3d6+10</code>
          <code>2d6+{'{level}'}+{'{Spell Power}'}</code>
        </div>
      </section>

      <section className="workshop-banner">
        <div>
          <h2>{copy.workshopTitle}</h2>
          <p>{copy.workshopBody}</p>
        </div>
        <a className="primary" href={base + 'workshop/'}>{copy.workshopCta}</a>
      </section>
    </>
  );
}
