import { describe, expect, it } from 'vitest'
import {
  isRoundStart,
  isRoundResult,
  isGameOver,
  isRoomState,
  isRoomConfig,
  isMusicPaused,
  isStartGameError,
} from './useWebSocket'

// The backend sends no "type" field on any WS message — these discriminators are the
// only thing standing between a message and the wrong handler. Each real message shape
// must match its own discriminator and no other's.

const roundStart = {
  roundNumber: 1,
  totalRounds: 5,
  trackId: 42,
  previewUrl: 'https://preview/42.mp3',
  options: ['a', 'b', 'c', 'd'],
  remainingSeconds: 15,
  scores: {},
}

const roundResult = {
  roundNumber: 1,
  correctTrackId: 42,
  correctAnswer: 'Song A — Artist A',
  scores: {},
}

const gameOver = { scores: {}, winnerPlayerId: 'p1' }

const roomState = { players: [{ id: 1, name: 'Alice', isHost: true }], status: 'WAITING' }

const roomConfig = { roomCode: 'ABC123', rounds: 5, category: 'POP', categoryType: 'GENRE' }

const musicPaused = { paused: true }

const startGameError = { reason: 'No tracks available for POP' }

const allMessages = {
  roundStart,
  roundResult,
  gameOver,
  roomState,
  roomConfig,
  musicPaused,
  startGameError,
}

describe('isRoundStart', () => {
  it('matches a RoundStartMessage', () => {
    expect(isRoundStart(roundStart)).toBe(true)
  })

  it.each(Object.entries(allMessages).filter(([k]) => k !== 'roundStart'))(
    'does not match %s',
    (_name, msg) => {
      expect(isRoundStart(msg)).toBe(false)
    },
  )
})

describe('isRoundResult', () => {
  it('matches a RoundResultMessage', () => {
    expect(isRoundResult(roundResult)).toBe(true)
  })

  it.each(Object.entries(allMessages).filter(([k]) => k !== 'roundResult'))(
    'does not match %s',
    (_name, msg) => {
      expect(isRoundResult(msg)).toBe(false)
    },
  )
})

describe('isGameOver', () => {
  it('matches a GameOverMessage', () => {
    expect(isGameOver(gameOver)).toBe(true)
  })

  it.each(Object.entries(allMessages).filter(([k]) => k !== 'gameOver'))(
    'does not match %s',
    (_name, msg) => {
      expect(isGameOver(msg)).toBe(false)
    },
  )
})

describe('isRoomState', () => {
  it('matches a RoomStateMessage', () => {
    expect(isRoomState(roomState)).toBe(true)
  })

  it.each(Object.entries(allMessages).filter(([k]) => k !== 'roomState'))(
    'does not match %s',
    (_name, msg) => {
      expect(isRoomState(msg)).toBe(false)
    },
  )
})

describe('isRoomConfig', () => {
  it('matches a RoomConfigMessage', () => {
    expect(isRoomConfig(roomConfig)).toBe(true)
  })

  it.each(Object.entries(allMessages).filter(([k]) => k !== 'roomConfig'))(
    'does not match %s',
    (_name, msg) => {
      expect(isRoomConfig(msg)).toBe(false)
    },
  )
})

describe('isMusicPaused', () => {
  it('matches a MusicPausedMessage', () => {
    expect(isMusicPaused(musicPaused)).toBe(true)
  })

  it.each(Object.entries(allMessages).filter(([k]) => k !== 'musicPaused'))(
    'does not match %s',
    (_name, msg) => {
      expect(isMusicPaused(msg)).toBe(false)
    },
  )

  it('rejects a paused field that is not boolean', () => {
    expect(isMusicPaused({ paused: 'true' })).toBe(false)
  })
})

describe('isStartGameError', () => {
  it('matches a StartGameErrorMessage', () => {
    expect(isStartGameError(startGameError)).toBe(true)
  })

  it.each(Object.entries(allMessages).filter(([k]) => k !== 'startGameError'))(
    'does not match %s',
    (_name, msg) => {
      expect(isStartGameError(msg)).toBe(false)
    },
  )
})
