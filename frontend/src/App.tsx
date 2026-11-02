import { useEffect, useRef, useState } from 'react'
import { Icon } from './icons'
import type { ActivityItem, Outcomes, UserTask } from './types'

const emptyOutcomes: Outcomes = {
  trucksSimulated: 0,
  serviceEventsProcessed: 0,
  exceptionsDetected: 0,
  interactionsWithoutPhoneCall: 0,
  correctivePickupsSold: 0,
  additionalTrashPurchases: 0,
  incrementalRevenue: 0,
  failedServicesRecovered: 0,
  ledgerEntries: 0,
  simulationRunning: false,
  elapsedSeconds: 0,
}

const number = new Intl.NumberFormat('en-US')

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: init?.body ? { 'Content-Type': 'application/json', ...init.headers } : init?.headers,
  })
  if (!response.ok) throw new Error(`Request failed (${response.status})`)
  const body = await response.text()
  return (body ? JSON.parse(body) : undefined) as T
}

function formatElapsed(seconds: number) {
  const safe = Math.max(0, Math.floor(seconds || 0))
  const minutes = Math.floor(safe / 60)
  return `${String(minutes).padStart(2, '0')}:${String(safe % 60).padStart(2, '0')}`
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value || 'Just now'
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function tone(value: string) {
  const normalized = value.toLowerCase()
  if (/(fail|error|escalat|exception|urgent)/.test(normalized)) return 'danger'
  if (/(complete|success|recover|approved|resolved)/.test(normalized)) return 'success'
  if (/(wait|pending|schedule|assign|claim)/.test(normalized)) return 'warning'
  return 'info'
}

function StatusPill({ value }: { value: string }) {
  return <span className={`status-pill ${tone(value)}`}><i />{value.replaceAll('_', ' ')}</span>
}

function MetricCard({ label, value, icon, accent, detail }: { label: string; value: string; icon: Parameters<typeof Icon>[0]['name']; accent: string; detail: string }) {
  return (
    <article className="metric-card" style={{ '--accent': accent } as React.CSSProperties}>
      <div className="metric-top"><span className="metric-icon"><Icon name={icon} /></span><span className="metric-trend"><Icon name="arrow" size={13} /> live</span></div>
      <strong>{value}</strong>
      <h3>{label}</h3>
      <p>{detail}</p>
    </article>
  )
}

function isOverfillActivity(item: ActivityItem) {
  return /overfill|overfilled|additional service tag|policy warning|overfill-fee/i.test(`${item.title} ${item.detail}`)
}

function isOverfillTask(task: UserTask) {
  return /overfill|overfilled|additional service tag|policy warning/i.test(`${task.formName} ${task.notes || ''}`)
}

function App() {
  const [outcomes, setOutcomes] = useState(emptyOutcomes)
  const [activity, setActivity] = useState<ActivityItem[]>([])
  const [tasks, setTasks] = useState<UserTask[]>([])
  const [connected, setConnected] = useState<boolean | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [selected, setSelected] = useState<UserTask | null>(null)
  const [operatorId, setOperatorId] = useState(() => localStorage.getItem('service-operator-id') || 'ops-lead')
  const refreshRef = useRef<() => Promise<void>>(async () => {})

  useEffect(() => {
    let cancelled = false
    const refresh = async () => {
      const results = await Promise.allSettled([
        api<Outcomes>('/api/outcomes'),
        api<ActivityItem[]>('/api/activity'),
        api<UserTask[]>('/api/user-tasks'),
      ])
      if (cancelled) return
      const allConnected = results.every((result) => result.status === 'fulfilled')
      setConnected(allConnected)
      if (results.some((result) => result.status === 'fulfilled')) setLastUpdated(new Date())
      if (results[0].status === 'fulfilled') setOutcomes(results[0].value)
      if (results[1].status === 'fulfilled') setActivity(results[1].value)
      if (results[2].status === 'fulfilled') {
        const nextTasks = results[2].value
        setTasks(nextTasks)
        setSelected((current) => current ? nextTasks.find((task) => task.userTaskGuid === current.userTaskGuid) ?? null : null)
      }
    }
    refreshRef.current = refresh
    void refresh()
    const timer = window.setInterval(refresh, 2500)
    return () => { cancelled = true; window.clearInterval(timer) }
  }, [])

  const updateOperator = (value: string) => {
    setOperatorId(value)
    localStorage.setItem('service-operator-id', value)
  }

  const openTasks = tasks.filter((task) => !/(done|complete|resolved|cancel)/i.test(task.status))
  const overfillActivity = activity.filter(isOverfillActivity)
  const overfillTasks = openTasks.filter(isOverfillTask)
  const customerUpdates = overfillActivity.filter((item) => /customer/i.test(item.type)).length
  const feeInvoices = overfillActivity.filter((item) => /overfill-fee/i.test(`${item.title} ${item.detail}`)).length

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="Service Operations home">
          <span className="brand-mark"><span /><span /><span /></span>
          <span><strong>Service Operations</strong><small>Execution control room</small></span>
        </a>
        <div className="topbar-right">
          <label className="operator-field"><Icon name="user" size={15} /><span>Operator</span><input aria-label="Operator ID" value={operatorId} onChange={(event) => updateOperator(event.target.value)} /></label>
          <div className={`connection ${connected === false ? 'offline' : ''}`}><i />{connected === null ? 'Connecting' : connected ? 'Live systems' : 'Reconnecting'}</div>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div>
            <div className="eyebrow"><span>LIVE</span> OVERFILL RESPONSE DESK</div>
            <h1>Turn an overfilled can<br />into a <em>clear next step.</em></h1>
            <p>Follow each field report through local policy, customer communication, billing, and corrective service.</p>
          </div>
          <div className="run-panel">
            <div className="run-label"><span className={outcomes.simulationRunning ? 'pulse' : ''} />{outcomes.simulationRunning ? 'Response coverage active' : 'Response desk standing by'}</div>
            <strong>{formatElapsed(outcomes.elapsedSeconds)}</strong>
            <small>RUN ELAPSED</small>
            <div className="run-track"><span style={{ width: `${Math.min(100, (outcomes.elapsedSeconds / 300) * 100)}%` }} /></div>
            <div className="run-meta"><span>{number.format(overfillActivity.length)} signals in view</span><span>{lastUpdated ? `Updated ${formatTime(lastUpdated.toISOString())}` : 'Awaiting telemetry'}</span></div>
          </div>
        </section>

        {connected === false && <div className="connection-banner"><Icon name="alert" />Backend unavailable. Showing the latest data and retrying automatically.</div>}

        <section className="metrics focus-metrics" aria-label="Overfilled container outcomes">
          <MetricCard label="Reports in view" value={number.format(overfillActivity.length)} icon="alert" accent="#ffbd6a" detail="Overfill-related events in the live stream" />
          <MetricCard label="Service tags sold" value={number.format(outcomes.additionalTrashPurchases)} icon="spark" accent="#8ca8ff" detail="Accepted additional-capacity offers" />
          <MetricCard label="Customer messages" value={number.format(customerUpdates)} icon="phone" accent="#66e5bb" detail="Overfill policy and offer messages sent" />
          <MetricCard label="Fee invoices" value={number.format(feeInvoices)} icon="dollar" accent="#73ef8d" detail="Assessed-fee invoices in the live stream" />
        </section>

        <section className="workflow-map panel" aria-labelledby="workflow-map-title">
          <div className="workflow-map-heading">
            <div><span className="section-kicker">WORKFLOW AT A GLANCE</span><h2 id="workflow-map-title">One report, three policy paths</h2></div>
            <span className="workflow-start"><Icon name="alert" size={15} />Overfilled container detected</span>
          </div>
          <div className="decision-paths">
            <article className="decision-path warning-path"><span className="path-number">01</span><div><h3>Issue a policy warning</h3><p>Notify the customer and record the decision without holding up the route.</p></div><span className="path-status">WARN</span></article>
            <article className="decision-path offer-path"><span className="path-number">02</span><div><h3>Offer additional service tags</h3><p>Wait for acceptance, invoice the purchase, then dispatch corrective service.</p></div><span className="path-status">OFFER TAGS</span></article>
            <article className="decision-path fee-path"><span className="path-number">03</span><div><h3>Assess the local fee</h3><p>Apply the municipality rule and send the overfill invoice for payment.</p></div><span className="path-status">ASSESS FEE</span></article>
          </div>
        </section>

        <section className="workspace focus-workspace">
          <div className="activity-panel panel">
            <div className="panel-heading">
              <div><span className="section-kicker">OVERFILL EVENT STREAM</span><h2>Workflow activity</h2></div>
              <div className="streaming"><i />Streaming</div>
            </div>
            <div className="activity-list">
              {overfillActivity.length === 0 && <Empty icon="alert" title="Waiting for an overfill report" detail="Policy decisions, customer contact, and billing activity will appear here." />}
              {overfillActivity.slice(0, 18).map((item) => (
                <article className="activity-row" key={item.id}>
                  <time>{formatTime(item.time)}</time>
                  <span className={`activity-node ${tone(`${item.type} ${item.status}`)}`}><Icon name={tone(`${item.type} ${item.status}`) === 'danger' ? 'alert' : tone(`${item.type} ${item.status}`) === 'success' ? 'check' : 'route'} size={16} /></span>
                  <div className="activity-copy">
                    <div><h3>{item.title}</h3><StatusPill value={item.status} /></div>
                    <p>{item.detail}</p>
                    <small>{item.customerId && <span>Customer {item.customerId}</span>}{item.truckId && <span>Unit {item.truckId}</span>}</small>
                  </div>
                </article>
              ))}
            </div>
          </div>

          <aside className="side-column">
            <section className="panel inbox-panel">
              <div className="panel-heading compact">
                <div><span className="section-kicker">HUMAN IN THE LOOP</span><h2>Overfill reviews</h2></div>
                <span className="count-badge">{overfillTasks.length}</span>
              </div>
              <div className="task-list">
                {overfillTasks.length === 0 && <Empty icon="inbox" title="No overfill reviews pending" detail="Phone-based customer decisions will appear here when a response is needed." />}
                {overfillTasks.map((task) => (
                  <button className="task-card" key={task.userTaskGuid} onClick={() => setSelected(task)}>
                    <span className={`priority-line ${tone(task.status)}`} />
                    <span className="task-card-main"><span><StatusPill value={task.status} /><time>{formatTime(task.scheduledTime)}</time></span><strong>{task.formName}</strong><small>{task.userGroup || 'Operations review'} · {task.userId ? `Claimed by ${task.userId}` : 'Unassigned'}</small>{task.notes && <span className="task-note-preview">“{task.notes}”</span>}</span>
                    <Icon name="arrow" />
                  </button>
                ))}
              </div>
            </section>

            <section className="panel outcome-panel">
              <div className="panel-heading compact"><div><span className="section-kicker">OVERFILL OUTCOMES</span><h2>Response summary</h2></div><Icon name="ledger" /></div>
              <OutcomeRow icon="alert" value={overfillActivity.filter((item) => /policy warning/i.test(`${item.title} ${item.detail}`)).length} label="Warnings delivered" />
              <OutcomeRow icon="spark" value={outcomes.additionalTrashPurchases} label="Additional tags purchased" />
              <OutcomeRow icon="dollar" value={feeInvoices} label="Fee invoices sent" />
              <OutcomeRow icon="ledger" value={overfillActivity.filter((item) => /overfill decision/i.test(`${item.title} ${item.detail}`)).length} label="Decisions recorded" />
            </section>
          </aside>
        </section>
      </main>

      {selected && <TaskDrawer task={selected} operatorId={operatorId.trim()} onClose={() => setSelected(null)} onChanged={() => refreshRef.current()} />}
    </div>
  )
}

function Empty({ icon, title, detail }: { icon: Parameters<typeof Icon>[0]['name']; title: string; detail: string }) {
  return <div className="empty"><span><Icon name={icon} size={22} /></span><strong>{title}</strong><p>{detail}</p></div>
}

function OutcomeRow({ icon, value, label }: { icon: Parameters<typeof Icon>[0]['name']; value: number; label: string }) {
  return <div className="outcome-row"><span><Icon name={icon} /></span><div><strong>{number.format(value)}</strong><small>{label}</small></div></div>
}

function TaskDrawer({ task, operatorId, onClose, onChanged }: { task: UserTask; operatorId: string; onClose: () => void; onChanged: () => Promise<void> }) {
  const [note, setNote] = useState(task.notes || '')
  const [busy, setBusy] = useState<'claim' | 'approve' | 'escalate' | null>(null)
  const [error, setError] = useState('')
  const claimed = Boolean(task.userId?.trim())

  const act = async (action: 'claim' | 'approve' | 'escalate') => {
    if (!operatorId) { setError('Enter an operator ID before taking action.'); return }
    if (action === 'escalate' && !note.trim()) { setError('Add a note explaining why this task needs escalation.'); return }
    setBusy(action)
    setError('')
    try {
      const path = `/api/user-tasks/${encodeURIComponent(task.wfRunId)}/${encodeURIComponent(task.userTaskGuid)}/${action === 'claim' ? 'claim' : 'complete'}`
      const resultField = task.fields.some((field) => field.name === 'accepted') ? 'accepted' : 'approved'
      const body = action === 'claim'
        ? { userId: operatorId }
        : { userId: operatorId, [resultField]: action === 'approve', note: note.trim() }
      await api(path, { method: 'POST', body: JSON.stringify(body) })
      await onChanged()
      onClose()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to update this task.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="drawer-layer" role="dialog" aria-modal="true" aria-labelledby="task-title" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside className="task-drawer">
        <header><div><span className="section-kicker">ACTION REQUIRED</span><h2 id="task-title">{task.formName}</h2></div><button className="icon-button" onClick={onClose} aria-label="Close task"><Icon name="x" /></button></header>
        <div className="drawer-status"><StatusPill value={task.status} /><span><Icon name="clock" size={15} />Scheduled {formatTime(task.scheduledTime)}</span></div>

        <section className="assignment">
          <span><Icon name="user" /></span>
          <div><small>ASSIGNMENT</small><strong>{task.userId || 'Unassigned'}</strong><p>{task.userGroup || 'Operations team'}</p></div>
          {!claimed && <button className="button secondary" disabled={Boolean(busy)} onClick={() => void act('claim')}>{busy === 'claim' ? 'Claiming…' : 'Claim task'}</button>}
        </section>

        {task.fields?.length > 0 && <section className="task-section"><h3>Review context</h3><div className="field-list">{task.fields.map((field) => <div className="field-row" key={field.name}><div><strong>{field.displayName || field.name}{field.required && <sup>*</sup>}</strong><p>{field.description || 'Review before completing this task.'}</p></div><span>{field.type}</span></div>)}</div></section>}

        <section className="task-section"><div className="notes-heading"><h3>Operator notes</h3><span>Visible in task history</span></div>{task.notes && <div className="existing-note"><small>EXISTING NOTE</small><p>{task.notes}</p></div>}<textarea value={note} onChange={(event) => setNote(event.target.value)} placeholder="Add decision context or escalation details…" rows={5} /></section>

        {error && <div className="action-error"><Icon name="alert" size={16} />{error}</div>}
        <footer><button className="button danger-button" disabled={Boolean(busy)} onClick={() => void act('escalate')}><Icon name="alert" size={16} />{busy === 'escalate' ? 'Escalating…' : 'Escalate'}</button><button className="button approve-button" disabled={Boolean(busy)} onClick={() => void act('approve')}><Icon name="check" size={16} />{busy === 'approve' ? 'Approving…' : 'Approve'}</button></footer>
      </aside>
    </div>
  )
}

export default App
