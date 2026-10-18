// Readers and the operator are in Korea; the server sends UTC.
const SEOUL = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(iso: string | null): string {
  return iso ? SEOUL.format(new Date(iso)) : '없음'
}
