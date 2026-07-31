import { beforeEach, describe, expect, it } from 'vitest'
import { useGameStore } from './useGameStore'
import type { RoundStartMessage, RoundResultMessage, RoomStateMessage, GameOverMessage } from '../types'

beforeEach(() => {
  useGameStore.getState().reset()
})

describe('applyRoundStart', () => {
  const msg: RoundStartMessage = {
    roundNumber: 2,
    totalRounds: 5,
    trackId: 42,
    previewUrl: 'https://preview/42.mp3',
    options: ['Song A — Artist A', 'Song B — Artist B', 'Song C — Artist C', 'Song D — Artist D'],
    remainingSeconds: 15,
    scores: { p1: 500 },
  }

  it('populates currentRound from the message', () => {
    useGameStore.getState().applyRoundStart(msg)

    expect(useGameStore.getState().currentRound).toEqual({
      trackId: 42,
      previewUrl: 'https://preview/42.mp3',
      options: msg.options,
      roundNumber: 2,
      totalRounds: 5,
      remainingSeconds: 15,
    })
  })

  it('sets phase to playing', () => {
    useGameStore.getState().applyRoundStart(msg)

    expect(useGameStore.getState().phase).toBe('playing')
  })

  it('clears a stale hasAnswered flag from the previous round', () => {
    useGameStore.setState({ hasAnswered: true })

    useGameStore.getState().applyRoundStart(msg)

    expect(useGameStore.getState().hasAnswered).toBe(false)
  })

  it('clears a stale musicPaused flag from the previous round', () => {
    useGameStore.setState({ musicPaused: true })

    useGameStore.getState().applyRoundStart(msg)

    expect(useGameStore.getState().musicPaused).toBe(false)
  })

  it('replaces scores with the message scores', () => {
    useGameStore.getState().applyRoundStart(msg)

    expect(useGameStore.getState().scores).toEqual({ p1: 500 })
  })
})

describe('applyRoundResult', () => {
  it('snapshots the current scores into prevScores before replacing them', () => {
    useGameStore.setState({ scores: { p1: 500, p2: 300 } })
    const msg: RoundResultMessage = {
      roundNumber: 2,
      correctTrackId: 42,
      correctAnswer: 'Song A — Artist A',
      scores: { p1: 1000, p2: 300 },
    }

    useGameStore.getState().applyRoundResult(msg)

    expect(useGameStore.getState().prevScores).toEqual({ p1: 500, p2: 300 })
    expect(useGameStore.getState().scores).toEqual({ p1: 1000, p2: 300 })
  })

  it('sets phase to answer_reveal and stores the result', () => {
    const msg: RoundResultMessage = {
      roundNumber: 2,
      correctTrackId: 42,
      correctAnswer: 'Song A — Artist A',
      scores: { p1: 1000 },
    }

    useGameStore.getState().applyRoundResult(msg)

    expect(useGameStore.getState().phase).toBe('answer_reveal')
    expect(useGameStore.getState().lastRoundResult).toEqual(msg)
  })
})

describe('applyRoomState', () => {
  const msg: RoomStateMessage = {
    players: [{ id: 1, name: 'Alice', isHost: true }],
    status: 'WAITING',
  }

  it('moves phase from home to lobby', () => {
    useGameStore.setState({ phase: 'home' })

    useGameStore.getState().applyRoomState(msg)

    expect(useGameStore.getState().phase).toBe('lobby')
  })

  it('leaves an in-game phase untouched (reconnect mid-round must not bounce to lobby)', () => {
    useGameStore.setState({ phase: 'playing' })

    useGameStore.getState().applyRoomState(msg)

    expect(useGameStore.getState().phase).toBe('playing')
  })

  it('always updates lobbyPlayers regardless of phase', () => {
    useGameStore.setState({ phase: 'playing' })

    useGameStore.getState().applyRoomState(msg)

    expect(useGameStore.getState().lobbyPlayers).toEqual(msg.players)
  })

  // Host status can change after the room was first joined (e.g. a host-disconnect
  // transfer, issue #31) — isHost must stay in sync with the latest broadcast rather
  // than being frozen at whatever it was set to at join time.
  it('syncs isHost from the matching player entry, converting numeric id to the string playerId', () => {
    useGameStore.setState({ playerId: '1', isHost: false })
    const promoted: RoomStateMessage = {
      players: [{ id: 1, name: 'Alice', isHost: true }, { id: 2, name: 'Bob', isHost: false }],
      status: 'WAITING',
    }

    useGameStore.getState().applyRoomState(promoted)

    expect(useGameStore.getState().isHost).toBe(true)
  })

  it('demotes isHost when the matching player entry is no longer host', () => {
    useGameStore.setState({ playerId: '2', isHost: true })
    const demoted: RoomStateMessage = {
      players: [{ id: 1, name: 'Alice', isHost: true }, { id: 2, name: 'Bob', isHost: false }],
      status: 'WAITING',
    }

    useGameStore.getState().applyRoomState(demoted)

    expect(useGameStore.getState().isHost).toBe(false)
  })

  it('leaves isHost untouched when no player entry matches the local playerId', () => {
    useGameStore.setState({ playerId: '99', isHost: true })

    useGameStore.getState().applyRoomState(msg)

    expect(useGameStore.getState().isHost).toBe(true)
  })
})

describe('applyGameOver', () => {
  it('sets phase to game_over and stores scores and result', () => {
    const msg: GameOverMessage = { scores: { p1: 1000, p2: 800 }, winnerPlayerId: 'p1' }

    useGameStore.getState().applyGameOver(msg)

    expect(useGameStore.getState().phase).toBe('game_over')
    expect(useGameStore.getState().scores).toEqual({ p1: 1000, p2: 800 })
    expect(useGameStore.getState().lastGameOver).toEqual(msg)
  })
})
