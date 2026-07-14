import { useState, type ReactNode } from 'react'
import './PasswordGate.css'

const ACCESS_PASSWORD = import.meta.env.VITE_ACCESS_PASSWORD
const STORAGE_KEY = 'bg_access'

interface Props {
  children: ReactNode
}

export default function PasswordGate({ children }: Props) {
  const [unlocked, setUnlocked] = useState(() => {
    if (!ACCESS_PASSWORD) return true
    return localStorage.getItem(STORAGE_KEY) === ACCESS_PASSWORD
  })
  const [input, setInput] = useState('')
  const [error, setError] = useState(false)

  if (unlocked) return <>{children}</>

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (input === ACCESS_PASSWORD) {
      localStorage.setItem(STORAGE_KEY, input)
      setUnlocked(true)
    } else {
      setError(true)
      setInput('')
    }
  }

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    setInput(e.target.value)
    if (error) setError(false)
  }

  return (
    <div className="gate-root">
      <div className="gate-card">
        <h1 className="gate-title">
          <span className="beat">Beat</span><span className="game">Game</span>
        </h1>
        <form className="gate-form" onSubmit={handleSubmit}>
          <input
            className="gate-input"
            type="password"
            placeholder="Enter password"
            autoComplete="current-password"
            autoFocus
            aria-label="Password"
            aria-invalid={error}
            aria-describedby={error ? 'gate-error' : undefined}
            value={input}
            onChange={handleChange}
          />
          {error && <p id="gate-error" className="gate-error">Wrong password</p>}
          <button className="gate-btn" type="submit">Enter</button>
        </form>
      </div>
    </div>
  )
}
