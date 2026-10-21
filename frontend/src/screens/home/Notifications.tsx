import { useNavigate } from 'react-router-dom'
import { AppBar, Body, Screen } from '../../components/ui'
import { Icon, type IconName } from '../../components/Icon'
import { get, post } from '../../lib/api'
import { fmt, useApi } from '../../lib/useApi'
import { demoNotifications } from '../../lib/demo'

type Noti = { id: number; type: string; title: string; body: string; at: string; read: boolean; applicationId?: number | null }
type NotificationDto = { id: number; type: string; title: string; body: string; applicationId: number | null; sentAt: string | null; scheduledAt: string; readAt: string | null }

const ICON: [RegExp, IconName][] = [
  [/MATCH|CONFIRM/, 'calendar-check'],
  [/REMIND|DAY|START/, 'bell'],
  [/FEEDBACK|REVIEW/, 'star'],
  [/PAY|REFUND/, 'card'],
]
const iconOf = (type: string): IconName => ICON.find(([re]) => re.test(type))?.[1] ?? 'calendar'

/** 3-6 알림 */
export default function Notifications() {
  const navigate = useNavigate()
  const { data, setData } = useApi<Noti[]>(
    () =>
      get<NotificationDto[]>('/api/me/notifications').then((list) =>
        list.map((n) => ({ id: n.id, type: n.type, title: n.title, body: n.body, at: n.sentAt ?? n.scheduledAt, read: !!n.readAt, applicationId: n.applicationId })),
      ),
    demoNotifications,
  )

  const open = (n: Noti) => {
    if (!n.read) {
      post(`/api/me/notifications/${n.id}/read`).catch(() => undefined)
      setData(data.map((x) => (x.id === n.id ? { ...x, read: true } : x)))
    }
    if (/FEEDBACK/.test(n.type)) navigate(n.applicationId ? `/review/${n.applicationId}` : '/review/100')
    else if (/MATCH|REMIND/.test(n.type)) navigate('/rooms')
    else if (/OPEN/.test(n.type)) navigate('/apply')
  }

  return (
    <Screen>
      <AppBar title="알림" />
      <Body style={{ paddingTop: 12, paddingBottom: 24 }}>
        {data.length === 0 && <p className="t-body-s muted" style={{ textAlign: 'center', padding: 40 }}>새 알림이 없어요</p>}
        {data.map((n) => (
          <button key={n.id} className={n.read ? 'noti-row' : 'noti-row unread'} onClick={() => open(n)}>
            <span className="noti-icon">
              <Icon name={iconOf(n.type)} size={20} />
            </span>
            <span className="noti-main">
              <span className="t-label-m">{n.title}</span>
              <span className="t-body-s muted">{n.body}</span>
            </span>
            <span className="t-label-s muted">
              {fmt.md(n.at)} {fmt.time(n.at)}
            </span>
          </button>
        ))}
      </Body>
    </Screen>
  )
}
