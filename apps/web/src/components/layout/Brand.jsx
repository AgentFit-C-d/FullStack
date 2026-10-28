import logo from '../../assets/logo.png'
import styles from './Layout.module.css'

export default function Brand({ auth = false }) {
  return <div className={`${styles.brand} ${auth ? styles.authBrand : ''}`}>
    <img src={logo} width="35" height="35" alt="" /><span>AgentFit</span>
  </div>
}
