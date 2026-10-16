import type { InputHTMLAttributes } from 'react'
import { Icon } from './Icon'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'placeholder'> & {
  name: string
  label: string
  invalid?: boolean
}

/** A pill field whose label rides up onto its border once it has focus or a value. */
export function AuthField({ name, label, invalid, ...input }: Props) {
  const id = `field-${name}`
  return (
    <div className="fl">
      {/* A blank placeholder lets CSS tell an empty field from a filled one. */}
      <input id={id} name={name} placeholder=" " aria-invalid={invalid || undefined} {...input} />
      <label htmlFor={id}>{label}</label>
    </div>
  )
}

export function AuthError({ message }: { message: string }) {
  return (
    <p className="a-err" role="alert">
      <Icon name="alert" size={16} />
      <span>{message}</span>
    </p>
  )
}
