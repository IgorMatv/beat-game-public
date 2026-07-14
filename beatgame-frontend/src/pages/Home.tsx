import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useGameStore } from '../store/useGameStore'
import './Home.css'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

function shake(el: HTMLElement) {
  el.animate(
    [
      { transform: 'translateX(0)' },
      { transform: 'translateX(-6px)' },
      { transform: 'translateX(6px)' },
      { transform: 'translateX(0)' },
    ],
    { duration: 280, easing: 'ease-in-out' }
  )
}

export default function Home() {
  const navigate = useNavigate()
  const nameRef = useRef<HTMLInputElement>(null)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [creating, setCreating] = useState(false)
  const [joining, setJoining] = useState(false)

  const store = useGameStore()

  function handleCodeChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6)
    setCode(v)
  }

  async function handleCreate() {
    if (!name.trim()) {
      if (nameRef.current) { nameRef.current.focus(); shake(nameRef.current) }
      return
    }
    setCreating(true)
    try {
      const res = await fetch(`${API_BASE}/api/rooms`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerName: name.trim() }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: { roomCode: string; playerToken: string } = await res.json()
      store.setPlayerName(name.trim())
      store.setPlayerToken(data.playerToken)
      store.setRoomCode(data.roomCode)
      store.setIsHost(true)
      store.setGameMode('multi')
      navigate(`/room/${data.roomCode}`)
    } catch (err) {
      console.error('[Home] createRoom failed', err)
      setCreating(false)
    }
  }

  function handleSolo() {
    if (!name.trim()) {
      if (nameRef.current) { nameRef.current.focus(); shake(nameRef.current) }
      return
    }
    store.setPlayerName(name.trim())
    navigate('/solo/config')
  }

  async function handleJoin() {
    if (code.length !== 6) return
    if (!name.trim()) {
      if (nameRef.current) { nameRef.current.focus(); shake(nameRef.current) }
      return
    }
    setJoining(true)
    try {
      const res = await fetch(`${API_BASE}/api/rooms/${code}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerName: name.trim() }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: { playerToken: string; players: { id: number; name: string; isHost: boolean }[] } = await res.json()
      store.setPlayerName(name.trim())
      store.setPlayerToken(data.playerToken)
      store.setRoomCode(code)
      store.setIsHost(false)
      store.setGameMode('multi')
      store.applyRoomState({ players: data.players, status: 'WAITING' })
      navigate(`/room/${code}`)
    } catch (err) {
      console.error('[Home] joinRoom failed', err)
      setJoining(false)
    }
  }

  function handleNameKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter') handleCreate()
  }

  function handleCodeKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && code.length === 6) handleJoin()
  }

  const codeReady = code.length === 6

  return (
    <div data-theme="home" className="min-h-dvh grid place-items-center p-6 overflow-hidden relative">
      <div className="ambient" />
      <div className="grain" />

      <div className="top-bar">
        <div><span className="dot" />Live · open beta</div>
        <div>v0.1</div>
      </div>

      <main className="stage">
        <header className="brand">
          <h1 className="wordmark" aria-label="BeatGame">
            <span className="beat">Beat</span>
            <span className="game">Game</span>
            <span className="dot-accent" aria-hidden="true" />
          </h1>
          <div className="wave" aria-hidden="true">
            {Array.from({ length: 10 }).map((_, i) => <span key={i} />)}
          </div>
          <p className="tagline"><span>Two players</span> · 30 sec · guess the song</p>
        </header>

        <section className="panel">
          <div className="field">
            <label htmlFor="name">
              Your name <span className="hint">so your rival knows who's losing</span>
            </label>
            <input
              id="name"
              ref={nameRef}
              className="input"
              type="text"
              placeholder="Enter your name"
              maxLength={16}
              autoComplete="off"
              spellCheck={false}
              value={name}
              onChange={e => setName(e.target.value)}
              onKeyDown={handleNameKeyDown}
            />
          </div>

          <button
            className="btn btn-primary"
            onClick={handleCreate}
            disabled={creating}
          >
            <span>{creating ? 'Creating…' : 'Create room'}</span>
            <svg
              className="arrow"
              width="18" height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M5 12h14" />
              <path d="M13 6l6 6-6 6" />
            </svg>
          </button>

          <button className="btn btn-solo" onClick={handleSolo}>
            Play solo
          </button>

          <div className="divider">or join one</div>

          <div className="field">
            <label htmlFor="code">
              Room code <span className="hint">6 characters</span>
            </label>
            <div className="join-row">
              <input
                id="code"
                className="input code-input"
                type="text"
                placeholder="A1B2C3"
                maxLength={6}
                autoComplete="off"
                spellCheck={false}
                inputMode="text"
                value={code}
                onChange={handleCodeChange}
                onKeyDown={handleCodeKeyDown}
              />
              <button
                className={`btn btn-ghost${codeReady ? ' ready' : ''}`}
                onClick={handleJoin}
                disabled={!codeReady || joining}
              >
                {joining ? 'Joining…' : 'Join'}
              </button>
            </div>
          </div>
        </section>

        <div className="meta">
          <div>No sign-up</div>
        </div>
      </main>
    </div>
  )
}
