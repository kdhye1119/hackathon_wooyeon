import { useLayoutEffect, useRef, useState } from 'react'

/**
 * 실제 계정의 첫 데이터 로딩 동안 화면을 가린다.
 * useApi/useAdminApi 는 받기 전까지 시안 데이터를 그려 두므로, 느린 서버에서 시안 데이터가 잠깐 보였다 사라지는 걸 막는다.
 * 화면이 그려지기 전(useLayoutEffect)에 등록하고 바로 가려서 첫 프레임에도 시안 데이터가 보이지 않는다.
 */
let pending = 0
const listeners = new Set<(n: number) => void>()

export function beginLoad() {
  pending++
  listeners.forEach((l) => l(pending))
}

export function endLoad() {
  pending = Math.max(0, pending - 1)
  listeners.forEach((l) => l(pending))
}

/** 첫 로딩 가림막 등록: 화면이 그려지기 전에 열고, done() 또는 언마운트 때 닫는다 */
export function useFirstLoadVeil(active: boolean) {
  const open = useRef(false)
  useLayoutEffect(() => {
    if (active) {
      open.current = true
      beginLoad()
    }
    return () => {
      if (open.current) {
        open.current = false
        endLoad()
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return () => {
    if (open.current) {
      open.current = false
      endLoad()
    }
  }
}

export function LoadingVeil({ className = 'loading-veil' }: { className?: string }) {
  const [n, setN] = useState(pending)
  useLayoutEffect(() => {
    listeners.add(setN)
    setN(pending)
    return () => {
      listeners.delete(setN)
    }
  }, [])
  if (n === 0) return null
  return (
    <div className={className} role="status" aria-label="불러오는 중">
      <span className="loading-dot" />
    </div>
  )
}
