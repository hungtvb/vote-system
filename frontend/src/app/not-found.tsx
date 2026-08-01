export default function NotFound() {
  return (
    <main
      data-qa-scenario="complete"
      data-qa-admin-cloaked="true"
      data-qa-admin-overflow="false"
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: '24px',
        background: '#F0E9D8',
        color: '#3A362E'
      }}
    >
      <section style={{ width: 'min(100%, 560px)', textAlign: 'center' }}>
        <span style={{ fontFamily: 'monospace', letterSpacing: '.16em' }}>404</span>
        <h1 style={{ margin: '16px 0 8px', fontSize: 'clamp(2rem, 8vw, 4rem)' }}>Page not found</h1>
        <p style={{ margin: 0 }}>The page you requested does not exist.</p>
        <a
          href="/"
          style={{
            display: 'inline-block',
            marginTop: '28px',
            padding: '12px 18px',
            border: '2px solid currentColor',
            color: 'inherit',
            fontFamily: 'monospace',
            fontWeight: 700,
            textDecoration: 'none'
          }}
        >
          RETURN HOME
        </a>
      </section>
    </main>
  );
}
