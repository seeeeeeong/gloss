// As the server's password rule: fewer is refused before it is sent.
export const MIN_PASSWORD = 10

export type PasswordField = 'current' | 'next' | 'confirm'
export type PasswordErrors = Partial<Record<PasswordField, string>>
export type PasswordValues = Record<PasswordField, string>

/**
 * What to say under each field before asking the server. The first sign-in asks for the
 * temporary password; my page asks for the current one and names the new pair as new.
 */
export function checkPasswordChange(
  values: PasswordValues,
  place: 'first' | 'settings',
): PasswordErrors {
  const errors: PasswordErrors = {}
  if (!values.current) {
    errors.current =
      place === 'first' ? '임시 비밀번호를 입력해 주세요.' : '현재 비밀번호를 입력해 주세요.'
  }
  if ([...values.next].length < MIN_PASSWORD) {
    errors.next = `${MIN_PASSWORD}자 이상으로 정해 주세요.`
  } else if (values.next !== values.confirm) {
    errors.confirm = place === 'first' ? '비밀번호가 서로 달라요.' : '새 비밀번호가 서로 달라요.'
  }
  return errors
}

const ABOUT_NEW = new Set([
  'account.password_too_short',
  'account.password_too_long',
  'account.password_blank',
  'account.password_common',
  'account.password_unchanged',
])

/** The field a refusal from the server belongs under; null for one that is about the whole form. */
export function passwordFieldOf(code: string): PasswordField | null {
  if (code === 'account.wrong_password') return 'current'
  return ABOUT_NEW.has(code) ? 'next' : null
}
