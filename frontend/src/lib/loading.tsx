import { useEffect, useState } from 'react'

/**
 * 실제 계정의 첫 데이터 로딩 동안 화면을 가린다.
 * useApi/useAdminApi 는 받기 전까지 시안 데이터를 그려 두므로, 느린 서버에서 시안 데이터가 잠깐 보였다 사라지는 걸 막는다.
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

/** 로딩 가림막: 120ms 넘게 걸릴 때만 보인다 (빠르면 깜빡임 없이 바로 화면) */
export function LoadingVeil({ className = 'loading-veil' }: { className?: string }) {
  const [n, setN] = useState(pending)
  const [show, setShow] = useState(false)
  useEffect(() => {
    listeners.add(setN)
    return () => {
      listeners.delete(setN)
    }
  }, [])
  useEffect(() => {
    if (n === 0) {
      setShow(false)
      return
    }
    const t = window.setTimeout(() => setShow(true), 120)
    return () => window.clearTimeout(t)
  }, [n])
  if (!show) return null
  return (
    <div className={className} role="status" aria-label="불러오는 중">
      <span className="loading-dot" />
    </div>
  )
}
