import { Link } from 'react-router'
import styles from './UI.module.css'

export function Button({ to, variant = '', large = false, className = '', children, ...props }) {
  const classes = [styles.button, styles[variant], large && styles.large, className].filter(Boolean).join(' ')
  return to
    ? <Link to={to} className={classes} {...props}>{children}</Link>
    : <button type="button" className={classes} {...props}>{children}</button>
}

export function Card({ as: Element = 'section', padded = false, className = '', children, ...props }) {
  return <Element className={[styles.card, padded && styles.padded, className].filter(Boolean).join(' ')} {...props}>{children}</Element>
}

export function Badge({ children, variant = 'neutral' }) {
  return <span className={`${styles.badge} ${styles[variant] || ''}`}>{children}</span>
}

export function FormField({ id, label, required = false, error, hint, children }) {
  return <div className={styles.field}>
    <label htmlFor={id}>{label}{required && <span className={styles.required}>필수</span>}</label>
    {children}
    {error && <p id={`${id}-error`} className={styles.error} role="alert">{error}</p>}
    {hint && <p id={`${id}-hint`} className={styles.hint}>{hint}</p>}
  </div>
}
