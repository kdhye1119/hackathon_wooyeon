import { useCallback, useEffect, useState } from 'react'
import { session } from './api'
import { useFirstLoadVeil } from './loading'

/**
 * API 조회 훅. 백엔드가 꺼져 있거나 로그인 전이면 Figma 시안 데이터(fallback)로 화면을 그린다.
 * demo=true 이면 시안 데이터를 보여주는 중.
 */
export function useApi<T>(fetcher: () => Promise<T>, fallback: T, deps: unknown[] = []) {
  const [data, setData] = useState<T>(fallback)
  const [loading, setLoading] = useState(true)
  const [demo, setDemo] = useState(false)
  // 실제 계정이면 화면이 그려지기 전에 가림막을 연다
  const closeVeil = useFirstLoadVeil(!!session.token && session.token !== 'demo')

  const load = useCallback(async () => {
    if (!session.token || session.token === 'demo') {
      setData(fallback)
      setDemo(true)
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setData(await fetcher())
      setDemo(false)
    } catch {
      setData(fallback)
      setDemo(true)
    } finally {
      setLoading(false)
      closeVeil()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    load()
  }, [load])

  return { data, setData, loading, demo, reload: load }
}

const DAYS = ['일', '월', '화', '수', '목', '금', '토']

export const fmt = {
  /** 10월 29일 목요일 */
  dateLong(iso: string) {
    const d = new Date(iso)
    return `${d.getMonth() + 1}월 ${d.getDate()}일 ${DAYS[d.getDay()]}요일`
  },
  /** 10.27 */
  md(iso: string) {
    const d = new Date(iso)
    return `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
  },
  /** 19:30 */
  time(iso: string) {
    const d = new Date(iso)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  },
  day(iso: string) {
    return new Date(iso).getDate()
  },
  month(iso: string) {
    return `${new Date(iso).getMonth() + 1}월`
  },
  weekday(iso: string) {
    return DAYS[new Date(iso).getDay()]
  },
  won(n: number) {
    return `${n.toLocaleString('ko-KR')}원`
  },
  /**
   * 마감 시각 표기. 서버의 자정(다음 날 00:00)은 Figma처럼 '전날 24:00'으로,
   * 23:59:59 도 24:00 으로 보여준다. → { md: '10.27', wd: '화', time: '24:00' }
   */
  dl(iso: string) {
    let d = new Date(iso)
    let time = fmt.time(iso)
    if (time === '00:00') {
      d = new Date(d.getTime() - 60_000)
      time = '24:00'
    } else if (time === '23:59') time = '24:00'
    return { md: fmt.md(d.toISOString()), wd: DAYS[d.getDay()], time }
  },
  /** 10.27(화) 24:00 */
  deadline(iso: string, space = false) {
    const x = fmt.dl(iso)
    return `${x.md}${space ? ' ' : ''}(${x.wd}) ${x.time}`
  },
}
