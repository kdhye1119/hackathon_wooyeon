import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Sheet } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { ApiError, post, session } from '../../lib/api'
import { fmt } from '../../lib/useApi'
import type { ApplicationView } from '../../lib/types'

type Step = 'locating' | 'done' | 'far'


/** 체크인: 위치 확인 없이 바로 체크인 → 완료 / 실패 (해커톤: 백엔드는 체크인 가능 시간만 확인) */
export default function CheckInSheet({ app, open, onClose, demo: demoProp, onDone }: { app: ApplicationView | null; open: boolean; onClose: () => void; demo: boolean; onDone: () => void }) {
  // 시안 세션이면 서버 호출 없이 완료 화면으로
  const demo = demoProp || !session.token || session.token === 'demo'
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('locating')
  const [error, setError] = useState('')
  const [at, setAt] = useState('')
  const venue = app?.table?.venueName ?? '식당'

  const run = async () => {
    setStep('locating')
    try {
      const started = Date.now()
      if (!demo) await post(`/api/applications/${app!.id}/check-in`)
      // 너무 빨리 끝나도 처리 중 화면을 잠깐 보여준다
      await new Promise((r) => setTimeout(r, Math.max(0, 800 - (Date.now() - started))))
      setAt(fmt.time(new Date().toISOString()))
      setStep('done')
      onDone()
    } catch (e) {
      setError(e instanceof ApiError || e instanceof Error ? e.message : '체크인에 실패했어요')
      setStep('far')
    }
  }

  useEffect(() => {
    if (!open) return
    setError('')
    run()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  return (
    <Sheet open={open} onClose={onClose} className="sheet-center">
      <>
        {step === 'locating' && (
          <>
            <span className="sheet-spacer8" />
            <span className="checkin-icon">
              <Icon name="map-pin" size={34} color="var(--terracotta)" />
            </span>
            <h2 className="t-h2">체크인 중</h2>
            <span className="loading-dots" aria-label="체크인 중">
              <i />
              <i />
              <i />
            </span>
          </>
        )}
        {step === 'done' && (
          <>
            <span className="sheet-spacer8" />
            <span className="checkin-icon ok">
              <Icon name="check" size={36} />
            </span>
            <h2 className="t-h2">체크인 되었습니다</h2>
            <p className="t-body-s muted">
              {at}, {venue}
            </p>
            <span className="sheet-spacer6" />
            <div className="checkin-actions">
              <Button onClick={() => navigate(app?.table?.roomId ? `/rooms/${app.table.roomId}` : '/rooms')}>단체방으로 이동</Button>
            </div>
          </>
        )}
        {step === 'far' && (
          <>
            <span className="sheet-spacer8" />
            <span className="checkin-icon warn">
              <Icon name="alert" size={34} color="var(--terracotta)" />
            </span>
            <h2 className="t-h2">체크인하지 못했어요</h2>
            <p className="t-body-s muted">{error || '잠시 후 다시 눌러주세요'}</p>
            <span className="sheet-spacer6" />
            <div className="checkin-actions">
              <Button onClick={run}>다시 시도</Button>
              <Button variant="secondary" onClick={() => navigate(app?.table?.roomId ? `/rooms/${app.table.roomId}?request=1` : '/rooms')}>
                운영진에게 요청
              </Button>
            </div>
          </>
        )}
      </>
    </Sheet>
  )
}
