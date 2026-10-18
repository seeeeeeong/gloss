/** A list that could not be asked for, as the source failing for a moment: asked again on request. */
export function ListFailed({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <p>
      {message}{' '}
      <button type="button" className="tbtn" onClick={onRetry}>
        다시 시도
      </button>
    </p>
  )
}
