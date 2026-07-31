import { useEffect, useState } from 'react'
import { useGameStore } from '../store/useGameStore'
import './RoundResultOverlay.css'

interface Props {
  roomCode: string
  myTimeMs: number | null
  onReady: () => void
  onPauseToggle: () => void
}

const ICO_CHECK = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 12l5 5L20 7" />
  </svg>
)
const ICO_X = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M6 6l12 12" /><path d="M18 6L6 18" />
  </svg>
)
const ICO_DOT = (
  <svg viewBox="0 0 24 24" fill="currentColor">
    <circle cx="12" cy="12" r="3" />
  </svg>
)

const COUNTDOWN_SECS = 3

export default function RoundResultOverlay({ myTimeMs, onReady, onPauseToggle }: Props) {
  const store = useGameStore()
  const result = store.lastRoundResult!
  const round = store.currentRound!
  const { playerId, playerName, isHost, lobbyPlayers, gameMode, scores, prevScores } = store

  const paused = store.musicPaused
  const isLastRound = round.roundNumber >= round.totalRounds

  const [countDown, setCountDown] = useState(COUNTDOWN_SECS)

  useEffect(() => {
    if (paused || countDown <= 0) return
    const id = setTimeout(() => setCountDown(c => c - 1), 1000)
    return () => clearTimeout(id)
  }, [paused, countDown])

  useEffect(() => {
    if (countDown <= 0 && !paused && !isLastRound) onReady()
  }, [countDown, paused, onReady, isLastRound])

  // Score deltas
  const myPrev = prevScores[playerId] ?? 0
  const myTotal = scores[playerId] ?? 0
  const myDelta = myTotal - myPrev
  const myCorrect = myDelta > 0

  // Opponent
  const opponentPlayer = gameMode === 'multi'
    ? lobbyPlayers.find(p => p.isHost !== isHost) ?? null
    : null
  const opponentId = Object.keys(result.scores).find(id => id !== playerId) ?? null
  const opponentPrev = opponentId ? (prevScores[opponentId] ?? 0) : 0
  const opponentTotal = opponentId ? (scores[opponentId] ?? 0) : 0
  const opponentDelta = opponentTotal - opponentPrev
  const opponentCorrect = opponentDelta > 0

  // Outcome key drives CSS classes on the outcome tag
  const outcomeKey =
    gameMode !== 'multi' ? (myCorrect ? 'you' : 'both-wrong') :
    myCorrect && opponentCorrect ? 'both-correct' :
    myCorrect ? 'you' :
    opponentCorrect ? 'op' :
    'both-wrong'

  const outcomeTag =
    gameMode !== 'multi' ? (myCorrect ? 'Correct' : 'Wrong') :
    outcomeKey === 'both-correct' ? 'Both got it' :
    outcomeKey === 'you' ? 'You got it' :
    outcomeKey === 'op' ? `${opponentPlayer?.name ?? 'They'} got it` :
    'Nobody got it'

  // Parse "Title — Artist"
  const parts = result.correctAnswer.split(' — ')
  const trackTitle = parts[0] ?? result.correctAnswer
  const trackArtist = parts[1] ?? ''

  // Summary line (multi only)
  let summaryNode: React.ReactNode = null
  if (gameMode === 'multi' && opponentPlayer) {
    const gap = Math.abs(myTotal - opponentTotal)
    if (myTotal > opponentTotal) {
      summaryNode = <><b>You lead</b> · +{gap.toLocaleString()} gap</>
    } else if (opponentTotal > myTotal) {
      summaryNode = <><b>{opponentPlayer.name} leads</b> · +{gap.toLocaleString()} gap</>
    } else {
      summaryNode = <>Tied · {myTotal.toLocaleString()} pts each</>
    }
  }

  const myVerdictText = myCorrect
    ? `Correct${myTimeMs !== null ? ` · ${(myTimeMs / 1000).toFixed(1)}s` : ''}`
    : 'Wrong'
  const opVerdictText = opponentCorrect ? 'Correct' : 'Wrong'

  const myInitial = playerName.charAt(0).toUpperCase() || '?'
  const opInitial = opponentPlayer?.name.charAt(0).toUpperCase() ?? '?'

  const myCardState = myCorrect ? 'correct' : outcomeKey === 'both-wrong' ? '' : 'wrong'
  const opCardState = opponentCorrect ? 'correct' : outcomeKey === 'both-wrong' ? '' : 'wrong'
  const myIco = myCorrect ? ICO_CHECK : outcomeKey === 'both-wrong' ? ICO_DOT : ICO_X
  const opIco = opponentCorrect ? ICO_CHECK : outcomeKey === 'both-wrong' ? ICO_DOT : ICO_X

  return (
    <>
      <div className="rr-scrim" />
      <div className="rr-overlay">
        <div className="rr-modal" role="dialog" aria-modal="true" aria-label="Round result">

          <div className="rr-head">
            <div className="rr-round">Round <b>{result.roundNumber}</b> / {round.totalRounds}</div>
            <div className={`rr-outcome-tag ${outcomeKey}`}>
              <span className="rr-pip" />
              {outcomeTag}
            </div>
          </div>

          <div className="rr-cover-wrap" aria-label="Album art revealed">
            <div className="rr-sparkle" />
          </div>

          <div className="rr-track">
            <div className="rr-track-meta">The answer was</div>
            <div className="rr-track-title">{trackTitle}</div>
            {trackArtist && <div className="rr-track-artist">{trackArtist}</div>}
          </div>

          <div className="rr-results">
            <div className={`rr-rcard ${myCardState}`}>
              <div className="rr-row-1">
                <div className="rr-who">
                  <div className="rr-av">{myInitial}</div>
                  <div className="rr-nm">{playerName}</div>
                </div>
                <span className="rr-badge-you">You</span>
              </div>
              <div className="rr-verdict">
                <span className="rr-ico">{myIco}</span>
                {myVerdictText}
              </div>
              <div className="rr-pts">+{myDelta.toLocaleString()} <span className="rr-dim">pts</span></div>
              <div className="rr-foot">
                <span>Total</span>
                <span className="rr-total">{myTotal.toLocaleString()}</span>
              </div>
            </div>

            {opponentPlayer && (
              <div className={`rr-rcard ${opCardState}`}>
                <div className="rr-row-1">
                  <div className="rr-who">
                    <div className="rr-av op">{opInitial}</div>
                    <div className="rr-nm">{opponentPlayer.name}</div>
                  </div>
                </div>
                <div className="rr-verdict">
                  <span className="rr-ico">{opIco}</span>
                  {opVerdictText}
                </div>
                <div className="rr-pts">+{opponentDelta.toLocaleString()} <span className="rr-dim">pts</span></div>
                <div className="rr-foot">
                  <span>Total</span>
                  <span className="rr-total">{opponentTotal.toLocaleString()}</span>
                </div>
              </div>
            )}
          </div>

          {summaryNode && <div className="rr-summary">{summaryNode}</div>}

          <div className="rr-next-row">
            {!isLastRound && (
              <span className="rr-auto-hint">
                {paused ? 'Paused' : `Next in ${countDown}s`}
              </span>
            )}
            <button
              className="rr-pause"
              onClick={onPauseToggle}
              aria-label={paused ? 'Resume' : 'Pause'}
            >
              {paused ? (
                <svg viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3" /></svg>
              ) : (
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="6" y="5" width="4" height="14" rx="1" /><rect x="14" y="5" width="4" height="14" rx="1" />
                </svg>
              )}
            </button>
          </div>

        </div>
      </div>
    </>
  )
}
