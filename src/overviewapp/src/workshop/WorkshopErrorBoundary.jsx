import { Component } from 'react';

function RenderChildren({ render }) {
  return render();
}

/**
 * Shows a recoverable message instead of unmounting the whole page when part
 * of the workshop throws while rendering. Pass children as a function to also
 * catch errors raised while the parent builds that subtree. Changing resetKey
 * (e.g. opening another character) clears the error.
 */
export default class WorkshopErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('Character workshop failed to render', error, info?.componentStack);
  }

  componentDidUpdate(previous) {
    if (this.state.error && previous.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  render() {
    const { children, t, onClose } = this.props;
    if (!this.state.error) return typeof children === 'function' ? <RenderChildren render={children} /> : children;
    return <section className="premium-panel" role="alert">
      <h2>{t.crashTitle}</h2>
      <p className="workshop-hint">{t.crashBody}</p>
      <div className="workshop-actions">
        <button type="button" className="primary" onClick={() => this.setState({ error: null })}>{t.crashRetry}</button>
        {onClose && <button type="button" onClick={() => { this.setState({ error: null }); onClose(); }}>{t.crashClose}</button>}
      </div>
    </section>;
  }
}
