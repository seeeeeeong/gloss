/** The field a sign-in left empty, the first one only, and what to say under it. */
export function emptyLoginField(
  loginId: string,
  password: string,
): { field: 'loginId' | 'password'; message: string } | null {
  if (!loginId.trim()) return { field: 'loginId', message: '아이디를 입력해 주세요.' }
  if (!password) return { field: 'password', message: '비밀번호를 입력해 주세요.' }
  return null
}

/** How long a sign-in lock has left, as the countdown reads: "14분 32초 남았어요." */
export function lockLeft(seconds: number): string {
  const left = Math.max(0, Math.ceil(seconds))
  return `${Math.floor(left / 60)}분 ${String(left % 60).padStart(2, '0')}초 남았어요.`
}
