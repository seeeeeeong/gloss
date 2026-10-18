type Timers = {
  set: (run: () => void, ms: number) => ReturnType<typeof setTimeout>
  clear: (timer: ReturnType<typeof setTimeout>) => void
}

const REAL: Timers = { set: (run, ms) => setTimeout(run, ms), clear: (t) => clearTimeout(t) }

/**
 * Calls [onRest] with a key once it has stayed put for [ms]; a new key, or none, cancels the wait.
 * Writing a post is paid for on the reader's key, so turning through a book asks for nothing.
 */
export function dwell(ms: number, onRest: (key: string) => void, timers: Timers = REAL) {
  let current: string | null = null
  let timer: ReturnType<typeof setTimeout> | undefined
  const cancel = () => {
    if (timer !== undefined) timers.clear(timer)
    timer = undefined
  }
  return {
    at(key: string | null) {
      if (key === current) return
      cancel()
      current = key
      if (key === null) return
      timer = timers.set(() => {
        timer = undefined
        onRest(key)
      }, ms)
    },
    stop() {
      cancel()
      current = null
    },
  }
}
