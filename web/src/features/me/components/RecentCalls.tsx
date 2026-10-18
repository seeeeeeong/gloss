import {
  callCost,
  callDays,
  callKind,
  callTime,
  callTokens,
  type UsageCall,
} from '@/features/me/api/usage'

/** The usage section's 최근 호출: by day, each call's kind, time and cost; its tokens on hover. */
export function RecentCalls({ calls, now }: { calls: UsageCall[]; now: Date }) {
  if (calls.length === 0) return null
  return (
    <>
      <h3 className="sub-h">최근 호출</h3>
      {callDays(calls, now).map((day) => (
        <section key={day.key} className="cday" aria-labelledby={`day-${day.key}`}>
          <h4 className="cday-h" id={`day-${day.key}`}>
            {day.label}
          </h4>
          <ul className="calls">
            {day.calls.map((call) => (
              <Call key={`${call.endedAt}-${call.purpose}-${call.costMicros}`} call={call} />
            ))}
          </ul>
        </section>
      ))}
    </>
  )
}

function Call({ call }: { call: UsageCall }) {
  const tokens = callTokens(call)
  return (
    // Focusable only so the keyboard gets the tooltip; screen readers hear the tokens below.
    <li className="call" tabIndex={tokens ? 0 : undefined} data-tip={tokens ?? undefined}>
      <span className="c-m">
        <span className="c-i">{callKind(call.purpose)}</span>
        <span className="c-k">{callTime(call.endedAt)}</span>
        {/* A touch screen has no hover, so its tokens sit under the time there. */}
        {tokens && (
          <span className="c-t" aria-hidden="true">
            {tokens}
          </span>
        )}
      </span>
      <span className="c-c">{callCost(call.costMicros)}</span>
      {tokens && <span className="sr-only">, {tokens}</span>}
    </li>
  )
}
