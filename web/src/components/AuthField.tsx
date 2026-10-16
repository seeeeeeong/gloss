import type { InputHTMLAttributes, ReactNode } from 'react'
import { Icon } from './Icon'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'placeholder'> & {
  name: string
  label: string
  invalid?: boolean
  /** The error under the form or the field that this field's mark refers to. */
  describedBy?: string
}

/** A pill field whose label rides up onto its border once it has focus or a value. */
export function AuthField({ name, label, invalid, describedBy, ...input }: Props) {
  const id = `field-${name}`
  return (
    <div className="fl">
      {/* A blank placeholder lets CSS tell an empty field from a filled one. */}
      <input
        id={id}
        name={name}
        placeholder=" "
        aria-invalid={invalid || undefined}
        aria-describedby={invalid ? describedBy : undefined}
        {...input}
      />
      <label htmlFor={id}>{label}</label>
    </div>
  )
}

export function AuthError(props: { id?: string; message: string; children?: ReactNode }) {
  return (
    <p className="a-err" id={props.id} role="alert">
      <Icon name="alert" size={16} />
      <span>
        {props.message}
        {props.children}
      </span>
    </p>
  )
}
