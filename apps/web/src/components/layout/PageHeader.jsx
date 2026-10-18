import styles from './Layout.module.css'

export default function PageHeader({ eyebrow, title, description, action }) {
  return <div className={styles.titleRow}><div><div className={styles.eyebrow}>{eyebrow}</div><h1 className={styles.pageTitle}>{title}</h1><p className={styles.pageDescription}>{description}</p></div>{action}</div>
}
