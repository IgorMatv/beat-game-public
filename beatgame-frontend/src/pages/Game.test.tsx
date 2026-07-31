import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, fireEvent } from '@testing-library/react'
import Game from './Game'
import { useGameStore } from '../store/useGameStore'
import type { RoundStartMessage } from '../types'

// Regression coverage for a bug that shipped twice: selectedIndex not resetting when a
// new round starts left the previously-picked answer highlighted before the player had
// clicked anything in the new round.

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return {
    ...actual,
    useParams: () => ({ code: 'ABC123' }),
    useNavigate: () => vi.fn(),
  }
})

vi.mock('../hooks/useWebSocket', () => ({
  useWebSocket: () => ({
    connect: vi.fn(),
    disconnect: vi.fn(),
    sendAnswer: vi.fn(),
    sendReady: vi.fn(),
    sendStart: vi.fn(),
    sendPause: vi.fn(),
  }),
}))

const round1: RoundStartMessage = {
  roundNumber: 1,
  totalRounds: 3,
  trackId: 1,
  previewUrl: null,
  options: ['Song A — Artist A', 'Song B — Artist B', 'Song C — Artist C', 'Song D — Artist D'],
  remainingSeconds: 15,
  scores: { '10': 0 },
}

const round2: RoundStartMessage = {
  ...round1,
  roundNumber: 2,
  trackId: 2,
  options: ['Song E — Artist E', 'Song F — Artist F', 'Song G — Artist G', 'Song H — Artist H'],
}

beforeEach(() => {
  useGameStore.getState().reset()
  useGameStore.setState({ playerToken: 'test-token', playerId: '10', roomCode: 'ABC123', gameMode: 'multi' })
})

// Game.tsx splits "Title — Artist" into separate spans, so the rendered text node is
// just the title half — query by that.
const titleOf = (option: string) => option.split(' — ')[0]

describe('Game — round-transition selection state', () => {
  it('shows the picked answer as selected until the round ends', () => {
    useGameStore.getState().applyRoundStart(round1)
    render(<Game />)

    fireEvent.click(screen.getByText(titleOf(round1.options[1])).closest('button')!)

    expect(screen.getByText(titleOf(round1.options[1])).closest('button')).toHaveClass('selected')
  })

  it('clears the previous round selection when a new round starts', () => {
    useGameStore.getState().applyRoundStart(round1)
    render(<Game />)
    fireEvent.click(screen.getByText(titleOf(round1.options[1])).closest('button')!)
    expect(screen.getByText(titleOf(round1.options[1])).closest('button')).toHaveClass('selected')

    act(() => {
      useGameStore.getState().applyRoundStart(round2)
    })

    for (const option of round2.options) {
      expect(screen.getByText(titleOf(option)).closest('button')).not.toHaveClass('selected')
    }
  })

  it('re-enables the answer buttons for the new round', () => {
    useGameStore.getState().applyRoundStart(round1)
    render(<Game />)
    fireEvent.click(screen.getByText(titleOf(round1.options[1])).closest('button')!)
    expect(screen.getByText(titleOf(round1.options[1])).closest('button')).toBeDisabled()

    act(() => {
      useGameStore.getState().applyRoundStart(round2)
    })

    for (const option of round2.options) {
      expect(screen.getByText(titleOf(option)).closest('button')).not.toBeDisabled()
    }
  })
})
