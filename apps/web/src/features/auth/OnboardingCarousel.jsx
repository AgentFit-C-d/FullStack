import { useEffect, useState, useSyncExternalStore } from 'react'
import image1 from '../../assets/1.png'
import image2 from '../../assets/2.png'
import image3 from '../../assets/3.png'
import image4 from '../../assets/4.png'
import styles from './Onboarding.module.css'

const slides = [
  { label: '프로젝트 이해', eyebrow: 'PROJECT UNDERSTANDING', title: ['기획서를 바탕으로', '프로젝트를 먼저 이해해요'], description: '프로젝트의 목적과 주요 기능을 분석해 필요한 작업을 정리해요.', tip: '기획서 분석 결과는 직접 확인하고 수정할 수 있어요.', image: image1, alt: '프로젝트 문서 분석 일러스트' },
  { label: '맞춤 추천', eyebrow: 'SMART RECOMMENDATION', title: ['역할과 환경에 맞는', 'AI 도구를 추천해요'], description: '프로젝트와 담당 업무를 바탕으로 필요한 도구만 골라 제안해요.', tip: '필요하지 않은 도구는 추천하지 않아요.', image: image2, alt: '추천 선택 일러스트' },
  { label: '설정 확인', eyebrow: 'SAFE CONFIGURATION', title: ['변경 전에 먼저 확인하고', '안전하게 적용하세요'], description: '필요한 권한과 변경될 설정을 확인한 뒤 직접 결정해요.', tip: '사용자의 승인 없이 설정을 변경하지 않아요.', image: image3, alt: '안전 설정 확인 일러스트' },
  { label: '적용 안내', eyebrow: 'STEP BY STEP', title: ['처음이어도 괜찮아요', '단계별로 따라갈 수 있어요'], description: '추천 이후 필요한 설정과 적용 방법까지 차근차근 안내해요.', tip: '다운로드 이후 다음에 해야 할 작업까지 안내해요.', image: image4, alt: '단계별 진행 일러스트' },
]

const canAutoplay = () => !document.hidden && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
function subscribeAutoplay(callback) {
  const media = window.matchMedia('(prefers-reduced-motion: reduce)')
  document.addEventListener('visibilitychange', callback)
  media.addEventListener('change', callback)
  return () => { document.removeEventListener('visibilitychange', callback); media.removeEventListener('change', callback) }
}

export default function OnboardingCarousel() {
  const [{ index, direction }, setSlide] = useState({ index: 0, direction: 1 })
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const autoplay = useSyncExternalStore(subscribeAutoplay, canAutoplay, () => false)
  const slide = slides[index]
  useEffect(() => {
    if (!autoplay || hovered || focused) return
    const timer = window.setInterval(() => setSlide(previous => ({ index: (previous.index + 1) % slides.length, direction: 1 })), 4500)
    return () => window.clearInterval(timer)
  }, [autoplay, hovered, focused, index])
  return <section className={styles.carousel} aria-label="AgentFit 서비스 소개" aria-roledescription="캐러셀"
    onPointerEnter={event => { if (event.pointerType !== 'touch') setHovered(true) }} onPointerLeave={() => setHovered(false)}
    onFocus={() => setFocused(true)} onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false) }}>
    <div aria-live={focused || !autoplay ? 'polite' : 'off'} aria-atomic="true">
      <div key={index} className={`${styles.body} ${direction > 0 ? styles.next : styles.previous}`}>
        <div className={styles.copy}><div className={styles.eyebrow}>{slide.eyebrow}</div><h2>{slide.title[0]}<br />{slide.title[1]}</h2><p>{slide.description}</p></div>
        <div className={styles.visual}><img src={slide.image} alt={slide.alt} width="320" height="320" /></div>
      </div>
    </div>
    <div className={styles.controls}>
      <span className={styles.count}>{String(index + 1).padStart(2, '0')} / 04</span>
      <div className={styles.progress} role="group" aria-label="서비스 소개 슬라이드 선택">
        {slides.map((item, i) => <button key={item.label} type="button" className={`${styles.segment} ${i === index ? styles.active : ''}`} aria-label={`${i + 1}번째 슬라이드`} aria-pressed={i === index} aria-describedby={`slide-tip-${i}`} onClick={() => setSlide({ index: i, direction: i >= index ? 1 : -1 })}>
          <span id={`slide-tip-${i}`} role="tooltip" className={styles.tooltip}>{item.label}</span>
        </button>)}
      </div>
    </div>
    <p className={styles.tip}>{slide.tip}</p>
  </section>
}
