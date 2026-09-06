function SecurityAlert({ title = 'Security update', message = '' }) {
  return <article className="security-alert"><strong>{title}</strong><p>{message}</p></article>
}

export default SecurityAlert
